package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.ui.menu.RedstoneEncyclopediaMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.Block;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Book-style, automatically complete manual built from the live RSE block registry and port contracts. */
public final class RedstoneEncyclopediaScreen extends AbstractContainerScreen<RedstoneEncyclopediaMenu> {
    private static final int PAPER = 0xFFF1E4B8;
    private static final int PAPER_DARK = 0xFFD8C58F;
    private static final int INK = 0xFF2B2118;
    private static final int MUTED = 0xFF6A5842;
    private static final int ACCENT = 0xFF9A2C2C;
    private final List<Block> blocks;
    private int page;

    public RedstoneEncyclopediaScreen(RedstoneEncyclopediaMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 300;
        imageHeight = 218;
        blocks = BuiltInRegistries.BLOCK.stream()
                .filter(block -> RedstoneEngineering.MOD_ID.equals(BuiltInRegistries.BLOCK.getKey(block).getNamespace()))
                .sorted(Comparator.comparing(block -> BuiltInRegistries.BLOCK.getKey(block).toString()))
                .toList();
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("<"), button -> page = Math.max(0, page - 1))
                .bounds(leftPos + 14, topPos + imageHeight - 27, 34, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> page = Math.min(blocks.size(), page + 1))
                .bounds(leftPos + imageWidth - 48, topPos + imageHeight - 27, 34, 20).build());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PAPER_DARK);
        graphics.fill(leftPos + 5, topPos + 5, leftPos + imageWidth - 5, topPos + imageHeight - 5, PAPER);
        graphics.fill(leftPos + imageWidth / 2 - 1, topPos + 8, leftPos + imageWidth / 2 + 1, topPos + imageHeight - 34, 0x33806A45);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        if (page == 0) renderIntroduction(graphics);
        else renderBlockPage(graphics, blocks.get(page - 1));
        String footer = "Page " + (page + 1) + " / " + (blocks.size() + 1);
        graphics.drawString(font, footer, (imageWidth - font.width(footer)) / 2, imageHeight - 25, MUTED, false);
    }

    private void renderIntroduction(GuiGraphics graphics) {
        graphics.drawString(font, Component.literal("REDSTONE ENCYCLOPEDIA"), 18, 16, ACCENT, false);
        drawWrapped(graphics,
                "A field manual for every registered Redstone Systems Engineering block. Entries are generated from the live block registry, so new RSE blocks cannot silently disappear from this manual.",
                18, 38, 260, INK, 10);
        drawWrapped(graphics,
                "Core rule: connect only declared engineering-port faces. A legitimate zero value is not the same thing as missing evidence. Use meters, HMIs and the Engineering Diagnostic Tablet to verify live state.",
                18, 88, 260, INK, 10);
        drawWrapped(graphics,
                "Media identity: Redstone = coarse 0..15 control; Lapis = 0..100 precision information; Copper = power/load; Optical, Pneumatic, Radio and other media keep their own implemented evidence models.",
                18, 138, 260, MUTED, 10);
    }

    private void renderBlockPage(GuiGraphics graphics, Block block) {
        String id = BuiltInRegistries.BLOCK.getKey(block).toString();
        graphics.drawString(font, block.getName(), 18, 14, ACCENT, false);
        graphics.drawString(font, id, 18, 27, MUTED, false);
        graphics.drawString(font, "ROLE • " + role(id), 18, 43, INK, false);

        int y = 58;
        String usage = usage(id);
        y = drawWrapped(graphics, usage, 18, y, 260, INK, 10) + 4;

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
                    y = drawWrapped(graphics, line, 18, y, 260, MUTED, 9);
                    if (y > imageHeight - 52) break;
                }
            }
        } else {
            graphics.drawString(font, "Passive / no EngineeringPort contract.", 18, y, MUTED, false);
            y += 11;
        }

        if (y < imageHeight - 46) {
            y += 3;
            drawWrapped(graphics, domainNote(id), 18, y, 260, INK, 9);
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
        String path = id.substring(id.indexOf(':') + 1);
        if (containsAny(path, "junction", "splitter")) return "Explicit branch / distribution";
        if (containsAny(path, "cable", "line", "wire", "conduit", "tube", "fiber", "data_bus", "differential_data_pair", "dust")) return "Transmission / interconnect";
        if (containsAny(path, "meter", "analyzer", "probe", "monitor", "sensor", "calorimeter", "debugger", "compass")) return "Measurement / diagnostics";
        if (containsAny(path, "source", "emitter", "transmitter", "exciter", "compressor", "injector", "oscillator")) return "Source / excitation";
        if (containsAny(path, "receiver", "indicator", "actuator", "cylinder")) return "Receiver / actuator";
        if (containsAny(path, "regulator", "valve", "filter", "attenuator", "resistor", "capacitor", "fuse", "conditioner", "controller", "divider", "delay", "encoder", "decoder", "serializer", "deserializer", "regenerator", "voter", "latch", "interlock", "processor", "sampler")) return "Processing / control";
        return "Engineering device";
    }

    private static String usage(String id) {
        String path = id.substring(id.indexOf(':') + 1);
        if (path.equals("engineering_compass")) return "Place it as a passive world-axis datum. It does not participate in a signal network; use its orientation as a stable North/reference aid while routing equipment.";
        if (path.equals("topology_debugger")) return "Place near the system you are commissioning and use its diagnostics to inspect explicit engineering-port compatibility and topology faults without changing the simulated network.";
        if (containsAny(path, "cable", "line", "wire", "fiber", "conduit", "tube", "dust", "data_bus", "differential_data_pair")) return "Use this as physical transmission media. Connect only compatible same-domain ports; use explicit junction/splitter devices where the topology requires branching.";
        if (containsAny(path, "meter", "analyzer", "probe", "monitor", "sensor", "debugger")) return "Connect or aim the declared measurement face, then open its HMI/diagnostics. Treat quality/evidence state separately from the numeric value; zero can be a valid measurement.";
        if (containsAny(path, "source", "emitter", "transmitter", "exciter", "compressor", "injector", "oscillator")) return "Provide the documented control/input conditions, then route the declared output face into compatible media. Verify the receiving end instead of assuming the source propagated successfully.";
        if (containsAny(path, "receiver", "indicator", "actuator", "cylinder")) return "Route a compatible upstream signal/medium into the declared input. Observe output, actuation or HMI evidence and diagnose starvation, topology or quality separately.";
        return "Place the device, inspect its declared physical I/O below, then configure it with right-click/HMI where available. Build from source → transport → processing → measurement/actuation and verify each boundary.";
    }

    private static String domainNote(String id) {
        String path = id.substring(id.indexOf(':') + 1);
        if (path.contains("lapis")) return "DOMAIN NOTE • Lapis carries precision information on the implemented 0..100 scale. Conversion to/from Redstone exposes scaling/quantization instead of inventing precision.";
        if (path.contains("copper")) return "DOMAIN NOTE • Copper is electrical power/load evidence, not a generic signal wire. Voltage/current/power and protection/evidence readiness remain distinct.";
        if (path.contains("pneumatic") || path.contains("air_")) return "DOMAIN NOTE • Pneumatic behavior uses the implemented lumped pressure/path-loss model; diagnostics do not claim CFD or random leak physics.";
        if (path.contains("optical")) return "DOMAIN NOTE • Optical diagnostics use the implemented discrete intensity/channel model; no fabricated dB link model.";
        if (path.contains("radio")) return "DOMAIN NOTE • RadioKernel owns distance, obstruction, fading, interference, collision, decode margin and latency. HMIs only present synchronized evidence.";
        if (path.contains("redstone")) return "DOMAIN NOTE • Redstone remains a coarse 0..15 control medium. A value of 0 can still be valid evidence.";
        return "FIELD NOTE • Prefer synchronized evidence and explicit physical ports; the client UI must not become a second physics solver.";
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
