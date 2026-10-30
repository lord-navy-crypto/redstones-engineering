package dev.redstoneengineering.block;

import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.InformationRuntime;

/**
 * Pure shared channel/payload evidence for line-of-sight TX/RX devices.
 * Optical ray propagation and receiving redstone transfer remain device-specific.
 */
public final class FreeSpaceOpticalChannelSupport {
    public static final int FRESH_TICKS = 8;
    public static final int RETAIN_STALE_TICKS = 40;
    private FreeSpaceOpticalChannelSupport() {}

    public static int nextChannel(int channel) {
        return Math.floorMod(channel + 1, 4);
    }

    public static boolean supportedChannel(int channel) {
        return channel >= 0 && channel < 4;
    }

    public static int boundedPower(int power) {
        return Math.max(0, Math.min(15, power));
    }

    public static PortQuality receiveQuality(InformationRuntime.Snapshot packet, int selectedChannel) {
        if (packet.ageTicks() < 0 || packet.qualityPercent() <= 0)
            return PortQuality.NO_SIGNAL;
        // An envelope remains visible for diagnosis, but only actual recent
        // TX writes renew the freshness timestamp. Receiver ticks never do.
        if (packet.ageTicks() > FRESH_TICKS) return PortQuality.STALE;
        if (packet.selector() != selectedChannel) return PortQuality.DOMAIN_MISMATCH;
        return packet.valid() ? PortQuality.VALID : PortQuality.STALE;
    }
}
