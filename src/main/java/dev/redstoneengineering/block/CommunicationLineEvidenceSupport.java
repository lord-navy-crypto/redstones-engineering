package dev.redstoneengineering.block;

import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.InformationRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Shared observation-only framing for serial bytes and differential bits.
 *
 * Deliberately does not decide topology, driver conflicts, frequency, route
 * continuity, encoding or signal validity: each medium's network owns them.
 */
final class CommunicationLineEvidenceSupport {
    private CommunicationLineEvidenceSupport() {}

    static EngineeringPortSnapshot snapshot(Level level, BlockPos pos,
                                             EngineeringPort port, String carrier,
                                             int mask, int maximum, PortQuality quality) {
        var packet = InformationRuntime.snapshot(level, carrier, pos);
        return new EngineeringPortSnapshot(
                port, packet.value() & mask, 0.0, maximum, quality);
    }

    static String report(Level level, BlockPos pos, String carrier, int mask,
                         String medium, PortQuality quality, int drivers,
                         int connectedPorts) {
        var packet = InformationRuntime.snapshot(level, carrier, pos);
        String unit = "serial".equals(carrier) ? "byte" : "bit";
        String payload = quality == PortQuality.VALID
                ? Integer.toString(packet.value() & mask) : "UNTRUSTED";
        String remedy = switch (quality) {
            case VALID -> "verify receiving device and physical routing";
            case TOPOLOGY_ERROR -> "inspect competing transmitters / driver ownership";
            case STALE -> "load affected route and recheck network budget";
            case NO_SIGNAL -> "connect exactly one compatible live transmitter";
            default -> "inspect link quality and device status";
        };
        return medium + " FIELD TEST | " + unit + "=" + payload
                + " | quality=" + quality
                + " | drivers=" + drivers
                + " | attached-faces=" + connectedPorts
                + " | sample-age=" + packet.ageTicks() + "t"
                + " | packet-quality=" + packet.qualityPercent() + "%"
                + " | next=" + remedy
                + " | a cable port declaration does not prove route continuity";
    }
}
