package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.ui.menu.RedstoneEncyclopediaMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Book-style, automatically complete manual built from the live RSE block registry and formal port
 * contracts. Basic usage/configuration guidance belongs here rather than crowding every live HMI.
 */
public final class RedstoneEncyclopediaScreen extends AbstractContainerScreen<RedstoneEncyclopediaMenu> {
    private static final int PAPER = 0xFFF1E4B8;
    private static final int PAPER_DARK = 0xFFD8C58F;
    private static final int INK = 0xFF2B2118;
    private static final int MUTED = 0xFF6A5842;
    private static final int ACCENT = 0xFF9A2C2C;
    private static final int VIEW_MARGIN = 8;
    private static final int CONTENT_TOP = 42;
    private static final int CONTENT_BOTTOM_MARGIN = 38;
    private final List<Block> blocks;
    private List<Block> filteredBlocks;
    private int page;
    private boolean configurationView;
    private Button previousButton;
    private Button nextButton;
    private Button viewButton;
    private EditBox searchBox;
    private int scrollOffset;

    public RedstoneEncyclopediaScreen(RedstoneEncyclopediaMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 520;
        imageHeight = 300;
        blocks = BuiltInRegistries.BLOCK.stream()
                .filter(block -> RedstoneEngineering.MOD_ID.equals(BuiltInRegistries.BLOCK.getKey(block).getNamespace()))
                .sorted(Comparator.comparing(block -> BuiltInRegistries.BLOCK.getKey(block).toString()))
                .toList();
        filteredBlocks = blocks;
    }

    @Override
    protected void init() {
        imageWidth = Math.max(360, width - VIEW_MARGIN * 2);
        imageHeight = Math.max(240, height - VIEW_MARGIN * 2);
        super.init();
        scrollOffset = 0;
        int searchWidth = Math.max(150, Math.min(320, imageWidth / 3));
        searchBox = addRenderableWidget(new EditBox(font, leftPos + imageWidth - searchWidth - 20, topPos + 11, searchWidth, 18,
                Component.literal("Search engineering blocks")));
        searchBox.setHint(Component.literal("Search blocks..."));
        searchBox.setMaxLength(64);
        searchBox.setResponder(this::applyFilter);

        previousButton = addRenderableWidget(Button.builder(Component.literal("Prev"), button -> changePage(-1))
                .bounds(leftPos + 14, topPos + imageHeight - 27, 48, 20).build());
        nextButton = addRenderableWidget(Button.builder(Component.literal("Next"), button -> changePage(1))
                .bounds(leftPos + imageWidth - 62, topPos + imageHeight - 27, 48, 20).build());
        viewButton = addRenderableWidget(Button.builder(Component.literal("Ports / Config"), button -> {
                    if (page == 0) return;
                    configurationView = !configurationView;
                    scrollOffset = 0;
                    refreshNavigationButtons();
                })
                .bounds(leftPos + imageWidth / 2 - 42, topPos + imageHeight - 27, 84, 20).build());
        refreshNavigationButtons();
    }

    private void applyFilter(String query) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            filteredBlocks = blocks;
            page = 0;
            scrollOffset = 0;
        } else {
            filteredBlocks = blocks.stream()
                    .filter(block -> {
                        String id = BuiltInRegistries.BLOCK.getKey(block).toString();
                        String name = block.getName().getString().toLowerCase(Locale.ROOT);
                        return id.toLowerCase(Locale.ROOT).contains(needle)
                                || name.contains(needle)
                                || role(id).toLowerCase(Locale.ROOT).contains(needle);
                    })
                    .toList();
            page = filteredBlocks.isEmpty() ? 0 : 1;
            scrollOffset = 0;
        }
        configurationView = false;
        scrollOffset = 0;
        refreshNavigationButtons();
    }

    private boolean searchActive() {
        return searchBox != null && !searchBox.getValue().trim().isEmpty();
    }

    private int minimumPage() {
        return searchActive() && !filteredBlocks.isEmpty() ? 1 : 0;
    }

    private void changePage(int delta) {
        page = Math.max(minimumPage(), Math.min(filteredBlocks.size(), page + delta));
        configurationView = false;
        scrollOffset = 0;
        refreshNavigationButtons();
    }

    private void refreshNavigationButtons() {
        if (previousButton != null) previousButton.active = page > minimumPage();
        if (nextButton != null) nextButton.active = page < filteredBlocks.size();
        if (viewButton == null) return;
        viewButton.active = page != 0 && !filteredBlocks.isEmpty();
        viewButton.setMessage(Component.literal(configurationView ? "Guide" : "Ports / Config"));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PAPER_DARK);
        graphics.fill(leftPos + 5, topPos + 5, leftPos + imageWidth - 5, topPos + imageHeight - 5, PAPER);
        graphics.fill(leftPos + imageWidth / 2 - 1, topPos + CONTENT_TOP, leftPos + imageWidth / 2 + 1,
                topPos + imageHeight - CONTENT_BOTTOM_MARGIN, 0x33806A45);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.enableScissor(leftPos + 10, topPos + CONTENT_TOP,
                leftPos + imageWidth - 10, topPos + imageHeight - CONTENT_BOTTOM_MARGIN);
        graphics.pose().pushPose();
        graphics.pose().translate(0, CONTENT_TOP - scrollOffset, 0);

        if (page == 0 && searchActive()) renderNoMatches(graphics);
        else if (page == 0) renderIntroduction(graphics);
        else if (configurationView) renderConfigurationPage(graphics, filteredBlocks.get(page - 1));
        else renderGuidePage(graphics, filteredBlocks.get(page - 1));

        graphics.pose().popPose();
        graphics.disableScissor();

        String footer = searchActive()
                ? filteredBlocks.isEmpty() ? "0 matches / " + blocks.size() + " entries"
                : "Match " + page + " / " + filteredBlocks.size() + " • " + blocks.size() + " total"
                : "Entry " + page + " / " + blocks.size();
        graphics.drawString(font, footer, (imageWidth - font.width(footer)) / 2, imageHeight - 25, MUTED, false);
        if (maxScroll() > 0) {
            String scroll = "SCROLL " + scrollOffset + " / " + maxScroll();
            graphics.drawString(font, scroll, imageWidth - font.width(scroll) - 18, imageHeight - 25, MUTED, false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= leftPos + 10 && mouseX <= leftPos + imageWidth - 10
                && mouseY >= topPos + CONTENT_TOP
                && mouseY <= topPos + imageHeight - CONTENT_BOTTOM_MARGIN) {
            scrollOffset = Math.max(0, Math.min(maxScroll(), scrollOffset - (int)Math.round(scrollY * 24.0)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private int contentDocumentHeight() {
        int width = Math.max(220, imageWidth - 60);
        if (page == 0) {
            String a = searchActive()
                    ? "No registered RSE block matches the current name, registry id, or engineering role. Refine the search or clear it to return to the full live registry."
                    : "Field manual for every registered Redstone Systems Engineering block. The table of contents is the live RSE block registry, so a newly registered block cannot silently disappear from the manual.";
            int lines = font.split(Component.literal(a), width).size();
            return searchActive() ? 100 + lines * 12 : 360;
        }
        Block block = filteredBlocks.get(page - 1);
        String id = BuiltInRegistries.BLOCK.getKey(block).toString();
        if (!configurationView) {
            int usageLines = font.split(Component.literal(usage(id)), width).size();
            int checkLines = font.split(Component.literal(fieldCheck(id)), width).size();
            return 150 + (usageLines + checkLines) * 12;
        }
        int height = 120;
        if (block instanceof EngineeringPortProvider provider) {
            for (EngineeringPort port : provider.engineeringPorts(block.defaultBlockState())) {
                String line = port.side().getName().toUpperCase(Locale.ROOT) + " • "
                        + port.domain().label() + " • " + port.direction() + " • " + port.label();
                height += Math.max(1, font.split(Component.literal(line), width).size()) * 11;
            }
        }
        for (Property<?> property : block.defaultBlockState().getProperties()) {
            height += 24;
        }
        height += Math.max(1, font.split(Component.literal(domainNote(id)), width).size()) * 11 + 60;
        return height;
    }

    private int maxScroll() {
        int visible = Math.max(80, imageHeight - CONTENT_TOP - CONTENT_BOTTOM_MARGIN);
        return Math.max(0, contentDocumentHeight() - visible);
    }

    private void renderNoMatches(GuiGraphics graphics) {
        graphics.drawString(font, Component.literal("NO MATCHES"), 18, 40, ACCENT, false);
        drawWrapped(graphics,
                "No registered RSE block matches the current name, registry id, or engineering role. Refine the search or clear it to return to the full live registry.",
                18, 60, Math.max(220, imageWidth - 60), INK, 10);
    }

    private void renderIntroduction(GuiGraphics graphics) {
        graphics.drawString(font, Component.literal("REDSTONE ENCYCLOPEDIA"), 18, 16, ACCENT, false);
        drawWrapped(graphics,
                "Field manual for every registered Redstone Systems Engineering block. The table of contents is the live RSE block registry, so a newly registered block cannot silently disappear from the manual.",
                18, 38, Math.max(220, imageWidth - 60), INK, 10);
        drawWrapped(graphics,
                "Each entry has two views: GUIDE explains how to use the device; PORTS / CONFIG documents declared physical I/O and common configurable state. Live measurements and fault decisions remain in the device HMI or Diagnostic Tablet.",
                18, 91, Math.max(220, imageWidth - 60), INK, 10);
        drawWrapped(graphics,
                "Core rule: connect declared engineering-port faces. A legitimate zero is not missing evidence. Redstone = coarse 0..15 control; Lapis = 0..100 precision information; Copper = power/load.",
                18, 149, Math.max(220, imageWidth - 60), MUTED, 10);
    }

    private void renderGuidePage(GuiGraphics graphics, Block block) {
        String id = BuiltInRegistries.BLOCK.getKey(block).toString();
        graphics.drawString(font, block.getName(), 18, 14, ACCENT, false);
        graphics.drawString(font, id, 18, 27, MUTED, false);
        graphics.drawString(font, "ROLE • " + role(id), 18, 43, INK, false);
        graphics.drawString(font, "HOW TO USE", 18, 60, ACCENT, false);
        int y = drawWrapped(graphics, usage(id), 18, 74, Math.max(220, imageWidth - 60), INK, 10) + 5;
        if (y < imageHeight - 54) {
            graphics.drawString(font, "FIELD CHECK", 18, y, ACCENT, false);
            y += 13;
            drawWrapped(graphics, fieldCheck(id), 18, y, Math.max(220, imageWidth - 60), MUTED, 9);
        }
    }

    private void renderConfigurationPage(GuiGraphics graphics, Block block) {
        String id = BuiltInRegistries.BLOCK.getKey(block).toString();
        graphics.drawString(font, block.getName(), 18, 14, ACCENT, false);
        graphics.drawString(font, "PORTS / CONFIG", 18, 28, MUTED, false);
        int y = 44;

        graphics.drawString(font, "PHYSICAL I/O", 18, y, ACCENT, false);
        y += 13;
        if (block instanceof EngineeringPortProvider provider) {
            List<EngineeringPort> ports = provider.engineeringPorts(block.defaultBlockState());
            if (ports.isEmpty()) {
                graphics.drawString(font, "No declared engineering ports.", 18, y, MUTED, false);
                y += 11;
            } else {
                for (EngineeringPort port : ports) {
                    String line = port.side().getName().toUpperCase(Locale.ROOT) + " • "
                            + port.domain().label() + " • " + port.direction() + " • " + port.label();
                    y = drawWrapped(graphics, line, 18, y, Math.max(220, imageWidth - 60), MUTED, 9);
                    if (y > 112) break;
                }
            }
        } else {
            graphics.drawString(font, "Passive / no EngineeringPort contract.", 18, y, MUTED, false);
            y += 11;
        }

        if (y < 132) {
            y += 3;
            graphics.drawString(font, "CONFIGURATION", 18, y, ACCENT, false);
            y += 13;
            List<Property<?>> properties = block.defaultBlockState().getProperties().stream()
                    .sorted(Comparator.comparing(Property::getName))
                    .toList();
            if (properties.isEmpty()) {
                graphics.drawString(font, "No BlockState configuration properties.", 18, y, MUTED, false);
                y += 10;
            } else {
                for (Property<?> property : properties) {
                    String values = property.getPossibleValues().stream().map(Object::toString).limit(6).collect(Collectors.joining("/"));
                    if (property.getPossibleValues().size() > 6) values += "/…";
                    String line = property.getName() + " = " + values + " • " + propertyMeaning(property.getName());
                    y = drawWrapped(graphics, line, 18, y, Math.max(220, imageWidth - 60), INK, 9);
                    if (y > 174) break;
                }
            }
        }

        if (y < imageHeight - 45) {
            y += 2;
            drawWrapped(graphics, domainNote(id), 18, y, Math.max(220, imageWidth - 60), MUTED, 9);
        }
    }

    private int drawWrapped(GuiGraphics graphics, String text, int x, int y, int width, int color, int step) {
        List<FormattedCharSequence> lines = font.split(Component.literal(text), width);
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, x, y, color, false);
            y += step;
        }
        return y;
    }

    private static String role(String id) {
        String path = path(id);
        if (containsAny(path, "junction", "splitter")) return "Explicit branch / distribution";
        if (containsAny(path, "cable", "line", "wire", "conduit", "tube", "fiber", "data_bus", "differential_data_pair", "dust")) return "Transmission / interconnect";
        if (containsAny(path, "meter", "analyzer", "probe", "monitor", "sensor", "calorimeter", "debugger", "compass")) return "Measurement / diagnostics";
        if (containsAny(path, "source", "emitter", "transmitter", "exciter", "compressor", "injector", "oscillator")) return "Source / excitation";
        if (containsAny(path, "receiver", "indicator", "actuator", "cylinder")) return "Receiver / actuator";
        if (containsAny(path, "regulator", "valve", "filter", "attenuator", "resistor", "capacitor", "fuse", "conditioner", "controller", "divider", "delay", "encoder", "decoder", "serializer", "deserializer", "regenerator", "voter", "latch", "interlock", "processor", "sampler")) return "Processing / control";
        return "Engineering device";
    }

    private static String usage(String id) {
        String path = path(id);
        return switch (path) {
            case "engineering_compass" -> "Place it as a passive world-axis datum. It never carries a signal. Use its stable North/world-axis reference while laying out directional equipment and documenting routes.";
            case "topology_debugger" -> "Place or use it near the system being commissioned. Read formal EngineeringPort compatibility and topology faults before changing physics settings; it is a structural diagnosis tool, not a signal source.";
            case "signal_probe" -> "Aim the measurement face at the signal you want to inspect. Rotate the measurement axis instead of rewiring the process. Use the reading as evidence only; the probe must not drive the measured network.";
            case "signal_analyzer" -> "Choose TAP for non-invasive observation or INLINE when the analyzer is intentionally inserted in the route. Verify the physical test input/output faces, then use the analyzer HMI for rolling quality and timing evidence.";
            case "oscilloscope" -> "Connect the instrument input to the measurement route, select the intended channel/trigger controls in the HMI, and compare retained samples over the displayed timebase. Do not treat a rendered trace as a second simulation.";
            case "logic_analyzer" -> "Connect digital/instrument evidence to the declared input, then inspect retained transitions and channel state. Use it to compare timing and digital activity without changing the observed circuit.";
            case "redstone_to_lapis_scaler" -> "Feed a 0..15 Redstone control value into RX and route TX to Lapis media. The output is a scaled representation; it cannot create source precision that Redstone never carried.";
            case "lapis_to_redstone_quantizer" -> "Feed Lapis precision information into RX and take Redstone from TX. Expect quantization onto the 0..15 Redstone grid and use the HMI to inspect reconstruction/quantization loss.";
            case "copper_circuit_meter" -> "Place the measurement interface on the intended Copper side. Read server-authoritative voltage, equivalent resistance, current and power evidence; keep evidence readiness separate from fuse/protection reliability.";
            case "pneumatic_flow_meter" -> "Insert it in a directed pneumatic run with IN and OUT aligned to flow. Compare inlet/outlet pressure and flow proxy to localize dominant loss without assuming continuous CFD.";
            case "pneumatic_cylinder" -> "Feed the declared pneumatic input from a solved pressure path. The target and finite response period depend on authoritative pressure; use path diagnostics to distinguish source, line and restriction loss.";
            case "radio_transmitter" -> "Set the intended radio channel/control input and provide a payload. The RadioKernel, not the screen, owns range, obstruction, fading, interference, collision and latency.";
            case "radio_receiver" -> "Match the intended channel, then inspect synchronized link evidence. A received payload of zero can still be valid; diagnose margin, obstruction, adjacent-channel interference and collision separately.";
            case "optical_receiver" -> "Terminate the intended optical path at the receiver. Use segment evidence for source/channel, passive loss and headroom; processors such as splitter/filter/attenuator form explicit segment boundaries.";
            case "pid_controller" -> "Wire process measurement to the controller input and controller output to the actuator/process path. Tune deliberately, compare commissioning evidence, and capture acceptance only after the topology and response are stable.";
            case "operations_monitor" -> "Place it as the plant-level observation surface. Use its bounded event/incident view to connect equipment evidence to operations without turning the monitor into a network solver.";
            default -> genericUsage(path);
        };
    }

    private static String genericUsage(String path) {
        if (containsAny(path, "junction", "splitter")) return "Use this only where a deliberate branch/distribution point is required. Connect compatible media to the declared faces; avoid using an implicit all-face processor where an explicit branch contract exists.";
        if (containsAny(path, "cable", "line", "wire", "fiber", "conduit", "tube", "dust", "data_bus", "differential_data_pair")) return "Use this as physical transmission media. Connect compatible same-domain endpoints and use explicit junction/splitter devices for branching. Verify the far end after every route change.";
        if (containsAny(path, "meter", "analyzer", "probe", "monitor", "sensor", "debugger")) return "Connect or aim the declared measurement face, then open the HMI/diagnostics. Treat evidence quality separately from the numeric value; a legitimate zero is still valid evidence.";
        if (containsAny(path, "source", "emitter", "transmitter", "exciter", "compressor", "injector", "oscillator")) return "Provide the documented control/input condition and route the declared output into compatible media. Confirm propagation at a receiver or meter instead of assuming the source reached the load.";
        if (containsAny(path, "receiver", "indicator", "actuator", "cylinder")) return "Route a compatible upstream medium into the declared input. Observe the resulting output/actuation and use diagnostics to separate missing supply, topology faults and degraded evidence.";
        if (containsAny(path, "regulator", "valve", "filter", "attenuator", "resistor", "capacitor", "fuse", "conditioner", "controller", "divider", "delay", "encoder", "decoder", "serializer", "deserializer", "regenerator", "voter", "latch", "interlock", "processor", "sampler")) return "Insert the device between compatible upstream and downstream endpoints, configure its state in the HMI/right-click controls, then verify both input evidence and transformed output. Do not infer correct processing from output alone.";
        return "Place the device, inspect its declared physical I/O in Ports / Config, then build source → transport → processing → measurement/actuation and verify each boundary with synchronized evidence.";
    }

    private static String fieldCheck(String id) {
        String path = path(id);
        if (containsAny(path, "cable", "line", "wire", "fiber", "conduit", "tube", "dust")) return "After rerouting or breaking media: confirm the old route stops carrying evidence and the new route becomes connected. Use the Diagnostic Tablet when the visual connection is ambiguous.";
        if (containsAny(path, "meter", "analyzer", "probe", "monitor", "sensor")) return "Check the physical measurement face, evidence validity/age, then the value. Never diagnose a zero solely from the number.";
        if (containsAny(path, "source", "transmitter", "emitter", "compressor", "oscillator")) return "Check command/state → declared TX face → downstream evidence. A source can be healthy while the route is disconnected.";
        if (containsAny(path, "receiver", "actuator", "cylinder", "indicator")) return "Check declared RX face → upstream evidence → local state/response. Separate starvation from a local device fault.";
        return "Use the Diagnostic Tablet for a retained topology snapshot before and after changing configuration; compare the physical route, evidence and state rather than relying on appearance alone.";
    }

    private static String propertyMeaning(String property) {
        return switch (property) {
            case "input_facing" -> "physical RX / process-input face";
            case "output_facing" -> "physical TX / process-output face";
            case "facing" -> "primary physical orientation";
            case "axis" -> "physical route/measurement axis";
            case "powered" -> "current powered/control state";
            case "open" -> "enabled/open path state";
            case "setpoint" -> "configured target or limit";
            case "channel" -> "selected communication/resonance channel";
            case "mode" -> "selected operating mode";
            case "delay" -> "configured timing delay";
            case "level" -> "configured level/state";
            default -> "block-state configuration";
        };
    }

    private static String domainNote(String id) {
        String path = path(id);
        if (path.contains("lapis")) return "DOMAIN • Lapis = implemented 0..100 precision information. Conversion exposes source spacing/quantization rather than inventing precision.";
        if (path.contains("copper")) return "DOMAIN • Copper = electrical power/load evidence, not generic signal. Electrical evidence readiness and protection reliability remain distinct.";
        if (path.contains("pneumatic") || path.contains("air_")) return "DOMAIN • Pneumatic = implemented lumped pressure/path-loss model; no fabricated CFD or random leak history.";
        if (path.contains("optical")) return "DOMAIN • Optical = implemented discrete intensity/channel model; no fabricated dB model.";
        if (path.contains("radio")) return "DOMAIN • RadioKernel owns range/obstruction/fading/interference/collision/margin/latency; UI only presents evidence.";
        if (path.contains("redstone")) return "DOMAIN • Redstone = coarse 0..15 control. Zero can be valid evidence.";
        return "FIELD NOTE • Prefer synchronized evidence and explicit physical ports; client UI never becomes a second physics solver.";
    }

    private static String path(String id) {
        return id.substring(id.indexOf(':') + 1);
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
