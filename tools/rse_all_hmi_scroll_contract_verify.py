#!/usr/bin/env python3
"""Guard every RSE block-facing HMI screen family against non-scrollable content.

Source regression coverage, NOT a Minecraft client screenshot or functional
scroll-wheel integration test. The bounded view-port contracts make the
LDLib2 scroller (rather than its parent flex column) own overflow.
"""
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
ld = root / "src/main/java/dev/redstoneengineering/ui/ldlib"
screens = root / "src/main/java/dev/redstoneengineering/client/ui"
errors = []

def check(value, detail):
    if not value:
        errors.append(detail)

common = (ld / "RseLdUiComponents.java").read_text(encoding="utf-8")
check("root.layout(layout -> layout.minHeight(0));" in common,
      "responsiveUi must bound the height of EVERY root engineering screen")
check("root.layout(l -> l.widthPercent(100).flex(1).minHeight(0)" in common,
      "shared tabbedWorkspace must allow its viewport to shrink")
check("scroller.layout(l -> l.widthPercent(100).flex(1).minHeight(0));" in common,
      "shared tabbedWorkspace needs a bounded ScrollerView")
check("scroller.viewPort(view -> view.layout(l -> l.flex(1).minHeight(0)" in common,
      "shared tabbedWorkspace needs bounded LDLib2 viewport")
for token in (
    "ScrollerMode.BOTH",
    "verticalScrollDisplay(ScrollDisplay.AUTO)",
    "scroller.addScrollViewChild(page);",
    "page.setDisplay(i == 0)",
    "scroller.verticalScroller.setNormalizedValue(0)",
    "return navigation;",
):
    check(token in common, "common tab scroller lost " + token)

hmis = []
standalone = []
shared = []
for path in sorted(ld.glob("*LdUi.java")):
    text = path.read_text(encoding="utf-8")
    if "public static ModularUI create(" not in text:
        continue
    hmis.append(path.name)
    check("RseLdUiComponents.responsiveUi(" in text,
          f"{path.name}: missing shared responsive root")
    uses_shared = "RseLdUiComponents.tabbedWorkspace(" in text
    uses_standalone = "new ScrollerView()" in text
    check(uses_shared != uses_standalone,
          f"{path.name}: HMI must use exactly one recognized scroll host")
    if uses_shared:
        shared.append(path.name)
    if uses_standalone:
        standalone.append(path.name)
        for token in (
            "workspace.layout(l -> l.widthPercent(100).flex(1).minHeight(0));",
            "workspace.viewPort(view -> view.layout(l -> l.flex(1).minHeight(0).paddingAll(8)));",
            "workspace.addScrollViewChildren(",
            "RseLdUiComponents.standaloneTabs(workspace,",
            "ScrollerMode.BOTH",
            "verticalScrollDisplay(ScrollDisplay.AUTO)",
        ):
            check(token in text, f"{path.name}: missing vertical-scrolling contract {token!r}")

expected_standalone = {
    "LogicAnalyzerLdUi.java", "OscilloscopeLdUi.java",
    "PidControllerLdUi.java", "SignalConditionerLdUi.java",
    "UniversalFieldDeviceLdUi.java",
}
check(set(standalone) == expected_standalone,
      f"independent scrollers not fully accounted for: {standalone}")
check(len(hmis) >= 23, f"unexpected decrease in HMI families: {len(hmis)}")
check(len(shared) >= 18, f"unexpected decrease in shared HMI coverage: {len(shared)}")

# The old hand-drawn workspace is intentionally separate from LDLib2.
legacy = (screens / "EngineeringScreen.java").read_text(encoding="utf-8")
for token in ("boolean mouseScrolled(", "scrollY -=", "clampScroll();",
              "graphics.pose().translate(-scrollX, -scrollY",
              "graphics.enableScissor(", "renderScrollIndicators(graphics)"):
    check(token in legacy, "legacy EngineeringScreen missing " + token)

tablet = (screens / "DiagnosticTabletScreen.java").read_text(encoding="utf-8")
for token in ("boolean mouseScrolled(", "scrollY -=", "clampScroll();",
              "graphics.pose().translate(0.0F, -scrollY"):
    check(token in tablet, "Diagnostic Tablet missing " + token)

logs = (screens / "RseDiagnosticsScreen.java").read_text(encoding="utf-8")
check("boolean mouseScrolled(" in logs and "filteredEntries().size()" in logs,
      "session diagnostics wheel must respect filtered page count")
book = (screens / "RedstoneEncyclopediaScreen.java").read_text(encoding="utf-8")
check("boolean mouseScrolled(" in book and "changePage(scrollYDelta < 0 ? 1 : -1)" in book,
      "book's per-block pagination must be navigable via wheel")

for name in ("RseDiagnosticsScreen.java", "DiagnosticTabletScreen.java",
             "EngineeringScreen.java", "RedstoneEncyclopediaScreen.java"):
    check("RseCrispGuiBackdrop.render(graphics, width, height);" in
          (screens / name).read_text(encoding="utf-8"),
          f"{name}: blur fix may not be removed")

if errors:
    print("RSE ALL HMI SCROLL CONTRACT: FAIL")
    for message in errors:
        print(" -", message)
    raise SystemExit(1)

print("RSE ALL HMI SCROLL CONTRACT: PASS")
print(f" {len(hmis)} LDLib2 screen families: {len(shared)} shared + {len(standalone)} standalone")
print(" shared dynamic height, independent Pioneer/Evidence/plot viewports, "
      "traditional screens, log/book wheel and shader-free backgrounds checked")
print(" Not a client-side gameplay scroll-to-bottom certification.")
