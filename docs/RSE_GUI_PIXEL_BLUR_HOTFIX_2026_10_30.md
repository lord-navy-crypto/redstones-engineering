# RSE Minecraft 1.21.1 GUI: pixel-blur diagnostic hotfix

## Report

Users observed that **the entire diagnostics/log interface** looks blurred at
the glyph/pixel level, not just a too-small font or incorrect numeric values.
An independent Agriculture N-key screen report describes the same symptom.

## Changes in this branch

- Replaced the default Minecraft screen background-blur entry path in five RSE
  GUI classes with `RseCrispGuiBackdrop.render`.
- This backdrop uses `GuiGraphics.fill` only. It does **not** create an FBO,
  apply a shader, resample a texture, scale the pose, or touch world state.
- Applies to the **RSE Diagnostics Console**, the **Diagnostic Tablet**, old
  `EngineeringScreen` derived devices, **Redstone Encyclopedia**, and the
  **LDLib2 host screen** used by the modern engineering HMIs.
- Kept normal `super.render` widget drawing, session evidence filter/page,
  copy, clear, and live server-based controls unchanged.
- Added `tools/rse_crisp_gui_diagnostics_verify.py` to the CI source checks.

This is a **targeted hypothesis fix**. NeoForge issue #1504 documents a
Minecraft 1.21.1 failure involving combined background blur, GUI depth and many
HUD layers. This source-level workaround avoids a risky path, but **does not
prove the user's exact problem is completely resolved** until a real client
screenshot demonstrates readable text.

## In-game acceptance (not automated by CI)

1. Back up an existing world; install only one build of RSE.
2. Use Minecraft 1.21.1 / matching NeoForge, LDLib2, Jade, GeckoLib.
3. Open player inventory and press the red diagnostics **+** control to open
   the RSE Diagnostics Console. **N is Agriculture's shortcut** in the current
   code, not an RSE registered shortcut; test the panels separately.
4. Check **font glyph edge sharpness** and actual WARN/ERROR text content,
   Filter, Page, Copy Report and Clear; also open the Diagnostic Tablet.
5. Check several engineering HMI panels. Compare GUI Scale 2, 3, 4; windowed
   versus full screen; effects with and without shaders/HUD mods.
6. Press F2 and inspect the original PNG at 100% zoom; if RSE is still blurry,
   compare vanilla inventory, tooltip, chat, pause UI. If all blur, focus on
   game settings, shaderpacks and framebuffer scaling rather than local UI
   code. No setting or diagnosis should be inferred without this evidence.

No claim is made of Minecraft client gameplay verification, screenshot or
full compatibility verification.
