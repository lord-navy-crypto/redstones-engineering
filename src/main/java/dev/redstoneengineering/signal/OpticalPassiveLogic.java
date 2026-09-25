package dev.redstoneengineering.signal;

/**
 * Pure transfer authority for simple passive guided-optical processors.
 *
 * <p>Intensity, attenuation and channel-selection math lives here so the server blocks, HMI
 * equations and semantic verifiers cannot silently drift apart. The attenuator retains a narrow
 * legacy BlockState fallback while exact server configuration spans the full 0..15 loss range.</p>
 */
public final class OpticalPassiveLogic {
    public static final int MIN_INTENSITY = 0;
    public static final int MAX_INTENSITY = 15;

    public static final int MIN_CONFIGURED_LOSS = 0;
    public static final int MAX_CONFIGURED_LOSS = 15;
    public static final int MIN_LEGACY_LOSS = 0;
    public static final int MAX_LEGACY_LOSS = 8;
    public static final int DEFAULT_LEGACY_LOSS = 2;

    public static final int MIN_CHANNEL = 0;
    public static final int MAX_CHANNEL = 15;
    public static final int DEFAULT_CHANNEL = 0;
    public static final int FILTER_INSERTION_LOSS = 1;

    public static final int TRANSFER_TICK_TICKS = 2;
    public static final int CONFIGURATION_RECHECK_TICKS = 1;

    private OpticalPassiveLogic() {}

    public static int boundedIntensity(int intensity) {
        return Math.max(MIN_INTENSITY, Math.min(MAX_INTENSITY, intensity));
    }

    public static int boundedConfiguredLoss(int loss) {
        return Math.max(MIN_CONFIGURED_LOSS, Math.min(MAX_CONFIGURED_LOSS, loss));
    }

    public static int boundedLegacyLoss(int loss) {
        return Math.max(MIN_LEGACY_LOSS, Math.min(MAX_LEGACY_LOSS, loss));
    }

    public static int boundedChannel(int channel) {
        return Math.max(MIN_CHANNEL, Math.min(MAX_CHANNEL, channel));
    }

    public static int attenuatedIntensity(int inputIntensity, int loss) {
        return Math.max(MIN_INTENSITY,
                boundedIntensity(inputIntensity) - boundedConfiguredLoss(loss));
    }

    public static boolean fullyAttenuated(int inputIntensity, int loss, boolean validInput) {
        int input = boundedIntensity(inputIntensity);
        return validInput && input > MIN_INTENSITY
                && attenuatedIntensity(input, loss) == MIN_INTENSITY;
    }

    public static boolean channelMatched(
            int inputIntensity, int inputChannel, int targetChannel, boolean validInput
    ) {
        return validInput
                && boundedIntensity(inputIntensity) > MIN_INTENSITY
                && boundedChannel(inputChannel) == boundedChannel(targetChannel);
    }

    public static int filteredIntensity(
            int inputIntensity, int inputChannel, int targetChannel, boolean validInput
    ) {
        if (!channelMatched(inputIntensity, inputChannel, targetChannel, validInput)) {
            return MIN_INTENSITY;
        }
        return Math.max(MIN_INTENSITY,
                boundedIntensity(inputIntensity) - FILTER_INSERTION_LOSS);
    }
}
