package dev.redstoneengineering.block;

import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.physics.SoulFluxNetwork;
import dev.redstoneengineering.physics.VibrationNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fourteen niche engineering blocks: in-world commissioning tests.
 *
 * These are server-side, read-only witnesses of actual operational
 * contracts; no fabricated pass certificates, hidden writes or network
 * connectivity claims. Each device has a specific diagnostic hypothesis.
 * No extra block IDs, world-save schema, timers or simulation parameters.
 */
public final class NicheDeviceCommissioning {
    private NicheDeviceCommissioning() {}

    public record Inspection(String purpose, String evidence, String nextCheck) {
        public String fullReport() {
            return "[RSE commissioning] " + purpose + " | " + evidence
                    + " | NEXT: " + nextCheck;
        }
    }

    /** Emit an untruncated chat report instead of an easily clipped actionbar. */
    public static void send(ServerPlayer player, BlockPos pos, BlockState state) {
        player.displayClientMessage(Component.literal(inspect(player.serverLevel(), pos, state).fullReport()), false);
    }

    /** The world and snapshots are only inspected, never modified. */
    public static Inspection inspect(Level level, BlockPos pos, BlockState state) {
        var block = state.getBlock();
        if (block instanceof EngineeringCompassBlock) {
            int neighbors = 0, declaredFaces = 0, unloaded = 0;
            StringBuilder roles = new StringBuilder();
            for (Direction direction : Direction.values()) {
                BlockPos target = pos.relative(direction);
                if (!level.hasChunkAt(target)) {
                    unloaded++;
                    continue;
                }
                BlockState neighbor = level.getBlockState(target);
                if (!(neighbor.getBlock() instanceof EngineeringPortProvider provider)) continue;
                neighbors++;
                // A port declaration is NOT a live wire or a compatible connection.
                EngineeringPort face = provider.engineeringPort(neighbor, direction.getOpposite()).orElse(null);
                if (face != null) {
                    declaredFaces++;
                    if (roles.length() > 0) roles.append(", ");
                    roles.append(direction.getName()).append(":").append(face.domain());
                }
            }
            return new Inspection("6-face FIELD SURVEY N=-Z E=+X / " + pos.toShortString(),
                    "adjacent engineering blocks=" + neighbors + "; declared facing ports=" + declaredFaces
                            + "; unloaded neighbors=" + unloaded + "; [" + roles + "]",
                    unloaded > 0 ? "load missing chunks; survey is partial"
                            : "a declared port is not proof of a valid cable or live signal");
        }
        if (block instanceof AnalogIndicatorBlock indicator) {
            var input = indicator.inputObservation(level, pos, state);
            return new Inspection("INLINE-VISUAL PROCESS INDICATOR",
                    "display=" + state.getValue(AnalogIndicatorBlock.LEVEL) + "/15"
                            + "; source=" + input.value() + "/15; quality=" + input.quality()
                            + "; last displayed value may be retained when STALE",
                    "verify the BACK measurement face; zero and absent input differ");
        }
        if (block instanceof HoneyVibrationDamperBlock) {
            var packet = InformationRuntime.snapshot(level, "mech_wave", pos);
            var wave = VibrationNetwork.sample(level, pos);
            return new Inspection("MECHANICAL VIBRATION DAMPING TEST",
                    "sample amplitude=" + wave.amplitude() + "/15; sample valid=" + wave.valid()
                            + "; local packet=" + packet.value() + "; quality=" + packet.qualityPercent()
                            + "%; next local decay=" + Math.max(0, packet.value() - 4),
                    "compare upstream and downstream samples; local decay is not network insertion loss");
        }
        if (block instanceof IronCoreBlock) {
            var field = IronCoreBlock.appliedFieldSample(level, pos);
            int margin = field.field() - IronCoreBlock.magnetizeThreshold();
            return new Inspection("MAGNETIC REMANENCE / THRESHOLD CHECK",
                    "remanent=" + state.getValue(IronCoreBlock.MAGNETIZED)
                            + "; applied field=" + field.field() + "/15; complete=" + field.complete()
                            + "; threshold margin=" + margin,
                    field.complete() ? "compare applied field with threshold; remanence needs explicit demagnetize"
                            : "load the entire radius-2 field sample before interpreting its strength");
        }
        if (block instanceof LapisNoiseSourceBlock) {
            int base = state.getValue(LapisNoiseSourceBlock.BASELINE) * 5;
            int range = state.getValue(LapisNoiseSourceBlock.NOISE) * 2;
            int now = LapisNoiseSourceBlock.currentValue(level, pos, state);
            return new Inspection("REPRODUCIBLE PRECISION NOISE INJECTION",
                    "initialized=" + LapisNoiseSourceBlock.sampleInitialized(level, pos)
                            + "; baseline=" + base + "/100; configured envelope=["
                            + Math.max(0, base - range) + "," + Math.min(100, base + range)
                            + "]; sampled=" + now + "; deviation=" + (now - base),
                    "compare measured downstream deviation to the configured envelope; zero is valid");
        }
        if (block instanceof MolecularCloudReceiverBlock) {
            var cloud = MolecularCloudReceiverBlock.sample(level, pos, state);
            return new Inspection("FREE-SPACE TRACER DETECTION",
                    "coverage complete=" + cloud.complete() + "; live=" + cloud.value()
                            + "/15; filtered=" + MolecularCloudReceiverBlock.filtered(level, pos)
                            + "; peak=" + MolecularCloudReceiverBlock.peak(level, pos)
                            + "; sensitivity gain=" + MolecularCloudReceiverBlock.gainFor(
                                    state.getValue(MolecularCloudReceiverBlock.SENSITIVITY)),
                    cloud.complete() ? "compare peak and filtered reading with tracer proximity"
                            : "load the full aperture before interpreting an empty reading");
        }
        if (block instanceof PhononConduitBlock) {
            var packet = InformationRuntime.snapshot(level, "thermal_pulse", pos);
            return new Inspection("LOW-BANDWIDTH THERMAL-PULSE TRANSPORT",
                    "packet=" + packet.value() + "/15; age=" + packet.ageTicks()
                            + "t; quality=" + packet.qualityPercent()
                            + "%; local next decay=" + Math.max(0, packet.value() - 2),
                    "test transmitter-to-receiver at increasing lengths; no packet is not measured zero");
        }
        if (block instanceof SculkVibrationInterfaceBlock detector) {
            int[] data = dev.redstoneengineering.physics.RuntimeIntStore.peek(level, "sculk_interface", pos);
            long age = data == null || data.length < 5 || data[1] <= 0
                    ? -1 : Math.max(0L, level.getGameTime() - data[3]);
            return new Inspection("SCULK EVENT-CODE CAPTURE",
                    "events=" + detector.eventCount(level, pos)
                            + "; last code=" + detector.lastEventCode(level, pos)
                            + "; transitions=" + detector.transitionCount(level, pos)
                            + "; last event age=" + age + "t; output=" + state.getValue(SculkVibrationInterfaceBlock.OUTPUT),
                    "verify the BACK event-code source; count captures rising events, not every game event");
        }
        if (block instanceof SoulFluxInjectorBlock) {
            var command = SoulFluxInjectorBlock.commandObservation(level, pos);
            int nodes = SoulFluxInjectorBlock.attachedNodeCount(level, pos);
            return new Inspection("SOUL-FLUX SOURCE COMMISSIONING [FICTIONAL]",
                    "UP command=" + command.value() + "/15; quality=" + command.quality()
                            + "; adjacent recipients=" + nodes
                            + "; offered packet=" + (command.valid() ? command.value() * 4 : 0),
                    nodes == 0 ? "attach a soul reservoir or conduit to a non-UP face"
                            : "verify receiver charge and graph scan limits; a command is not delivery proof");
        }
        if (block instanceof SoulFluxMeterBlock) {
            var measurement = SoulFluxMeterBlock.inputObservation(level, pos, state);
            int translated = measurement.value() * 15 / 100;
            return new Inspection("SOUL-FLUX TO REDSTONE METER [FICTIONAL]",
                    "BACK charge=" + measurement.value() + "/100; quality=" + measurement.quality()
                            + "; ideal quantized output=" + translated
                            + "; actual output=" + state.getValue(SoulFluxMeterBlock.OUTPUT),
                    "check measurement validity and 100-to-15 quantization before alarm decisions");
        }
        if (block instanceof SoulSandReservoirBlock) {
            var packet = SoulFluxNetwork.chargeSnapshot(level, pos);
            int charge = SoulFluxNetwork.charge(level, pos);
            return new Inspection("LONG-HOLD SOUL CHARGE STORAGE [FICTIONAL]",
                    "stored=" + charge + "/100; valid=" + packet.valid() + "; age=" + packet.ageTicks()
                            + "t; decay=1 unit per " + SoulSandReservoirBlock.decayPeriodTicks() + "t"
                            + "; no-recharge empty estimate=" + (charge * SoulSandReservoirBlock.decayPeriodTicks()) + "t",
                    "estimate applies only without new injection; zero can be valid empty storage");
        }
        if (block instanceof SoulSoilConduitBlock) {
            var packet = SoulFluxNetwork.chargeSnapshot(level, pos);
            return new Inspection("FAST-DECAY SOUL-FLUX TRANSPORT [FICTIONAL]",
                    "charge=" + packet.value() + "/100; valid=" + packet.valid()
                            + "; age=" + packet.ageTicks() + "t; decay=1 unit per "
                            + SoulSoilConduitBlock.decayPeriodTicks() + "t",
                    "compare with the 40t reservoir; transport and storage have different roles");
        }
        if (block instanceof ThermalPulseEncoderBlock) {
            var drive = ThermalPulseEncoderBlock.inputObservation(level, pos);
            var sent = InformationRuntime.snapshot(level, "thermal_encoder", pos);
            return new Inspection("REDSTONE TO THERMAL-PULSE ENCODER",
                    "DOWN drive=" + drive.value() + "/15; quality=" + drive.quality()
                            + "; local emitted packet=" + sent.value() + "/15"
                            + "; packet age=" + sent.ageTicks() + "t; 5 declared output faces",
                    "a sender-side pulse is not guaranteed arrival; inspect receiver and route quality");
        }
        if (block instanceof ThermalPulseReceiverBlock) {
            var received = InformationRuntime.snapshot(level, "thermal_pulse", pos);
            return new Inspection("THERMAL-PULSE TO REDSTONE RECEIVER",
                    "received=" + received.value() + "/15; age=" + received.ageTicks()
                            + "t; quality=" + received.qualityPercent()
                            + "%; FRONT output=" + state.getValue(ThermalPulseReceiverBlock.OUTPUT),
                    "compare to encoder input; propagation losses and low bandwidth are intentional");
        }
        return new Inspection("UNSUPPORTED", "not one of the fourteen targeted blocks",
                "use the standard RSE engineering diagnostics");
    }
}
