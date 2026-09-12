package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.block.*;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.ui.EngineeringUiRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Server-authoritative HMI projection for amethyst resonance sources, processors, and metrology. */
public final class AmethystSystemMenu extends EngineeringDeviceMenu {
    public static final int KIND_SOURCE = 0;
    public static final int KIND_FILTER = 1;
    public static final int KIND_TUNED = 2;
    public static final int KIND_SPECTRUM = 3;

    public static final int BUTTON_PRIMARY_PREVIOUS = 0;
    public static final int BUTTON_PRIMARY_NEXT = 1;
    public static final int BUTTON_SECONDARY_PREVIOUS = 2;
    public static final int BUTTON_SECONDARY_NEXT = 3;
    public static final int BUTTON_ROTATE_LEFT = 4;
    public static final int BUTTON_ROTATE_RIGHT = 5;
    public static final int BUTTON_PULSE = 6;
    public static final int BUTTON_INPUT_LEFT = 7;
    public static final int BUTTON_INPUT_RIGHT = 8;
    public static final int BUTTON_OUTPUT_LEFT = 9;
    public static final int BUTTON_OUTPUT_RIGHT = 10;

    private final DataSlot kind = trackedInt();
    private final DataSlot primary = trackedInt();
    private final DataSlot secondary = trackedInt();
    private final DataSlot tertiary = trackedInt();
    private final DataSlot auxiliary = trackedInt();
    private final DataSlot extraA = trackedInt();
    private final DataSlot extraB = trackedInt();
    private final DataSlot quality = trackedInt();
    private final DataSlot inputFacing = trackedInt();
    private final DataSlot outputFacing = trackedInt();
    private final DataSlot stateFlag = trackedInt();

    public AmethystSystemMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBlockPos());
    }

    public AmethystSystemMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(EngineeringUiRegistration.AMETHYST_SYSTEM.get(), containerId, inventory, pos,
                inventory.player.level().getBlockState(pos).getBlock());
        if (!level.isClientSide) refreshAuthoritativeSnapshot();
    }

    @Override
    protected void refreshAuthoritativeSnapshot() {
        BlockState state = level.getBlockState(blockPos);
        Block block = state.getBlock();
        primary.set(0); secondary.set(0); tertiary.set(0); auxiliary.set(0); extraA.set(0); extraB.set(0);
        quality.set(PortQuality.NO_SIGNAL.ordinal()); inputFacing.set(-1); outputFacing.set(-1); stateFlag.set(0);

        if (block instanceof AmethystResonatorBlock) {
            kind.set(KIND_SOURCE);
            primary.set(state.getValue(AmethystResonatorBlock.FREQUENCY));
            secondary.set(state.getValue(AmethystResonatorBlock.AMPLITUDE));
            stateFlag.set(AmethystResonatorBlock.isActive(level, blockPos) ? 1 : 0);
            quality.set(stateFlag.get() == 1 ? PortQuality.VALID.ordinal() : PortQuality.NO_SIGNAL.ordinal());
        } else if (block instanceof AmethystFrequencyFilterBlock) {
            kind.set(KIND_FILTER);
            AmethystFrequencyFilterBlock.FilterEvidence e = AmethystFrequencyFilterBlock.evidence(level, blockPos, state);
            primary.set(e.inputFrequency()); secondary.set(e.inputAmplitude()); tertiary.set(e.targetFrequency());
            auxiliary.set(e.expectedOutputAmplitude()); stateFlag.set(e.matched() ? 1 : 0);
            quality.set(e.inputQuality().ordinal());
            inputFacing.set(DirectionalDomainBlock.seriesInputSide(state).ordinal());
            outputFacing.set(DirectionalDomainBlock.seriesOutputSide(state).ordinal());
        } else if (block instanceof AmethystTunedResonatorBlock) {
            kind.set(KIND_TUNED);
            AmethystTunedResonatorBlock.ResponseEvidence e = AmethystTunedResonatorBlock.response(level, blockPos, state);
            primary.set(e.inputFrequency()); secondary.set(e.inputAmplitude()); tertiary.set(e.naturalFrequency());
            auxiliary.set(e.qIndex()); extraA.set(e.bandwidth()); extraB.set(e.outputAmplitude());
            stateFlag.set(e.saturated() ? 2 : e.responding() ? 1 : 0);
            quality.set(e.inputQuality().ordinal());
            inputFacing.set(DirectionalDomainBlock.seriesInputSide(state).ordinal());
            outputFacing.set(DirectionalDomainBlock.seriesOutputSide(state).ordinal());
        } else if (block instanceof AmethystSpectrumAnalyzerBlock) {
            kind.set(KIND_SPECTRUM);
            AmethystSpectrumAnalyzerBlock.Spectrum s = AmethystSpectrumAnalyzerBlock.spectrum(level, blockPos);
            primary.set(s.dominantFrequency()); secondary.set(s.energy()); tertiary.set(s.activeBands());
            auxiliary.set(s.samples()); extraA.set(s.conflicts()); extraB.set(s.scannedCells()); stateFlag.set(s.expectedCells());
            quality.set(AmethystSpectrumAnalyzerBlock.quality(level, blockPos).ordinal());
        } else kind.set(-1);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (level.isClientSide) return true;
        if (!stillValid(player)) return false;
        BlockState state = level.getBlockState(blockPos);
        Block block = state.getBlock();
        boolean changed = false;

        if (block instanceof AmethystResonatorBlock resonator) {
            if (id == BUTTON_PRIMARY_PREVIOUS || id == BUTTON_PRIMARY_NEXT) {
                int f = state.getValue(AmethystResonatorBlock.FREQUENCY);
                f = id == BUTTON_PRIMARY_NEXT ? (f >= 15 ? 1 : f + 1) : (f <= 1 ? 15 : f - 1);
                state = state.setValue(AmethystResonatorBlock.FREQUENCY, f);
            } else if (id == BUTTON_SECONDARY_PREVIOUS || id == BUTTON_SECONDARY_NEXT) {
                int a = state.getValue(AmethystResonatorBlock.AMPLITUDE);
                a = id == BUTTON_SECONDARY_NEXT ? (a >= 15 ? 1 : a + 1) : (a <= 1 ? 15 : a - 1);
                state = state.setValue(AmethystResonatorBlock.AMPLITUDE, a);
            } else if (id == BUTTON_PULSE) {
                RuntimeIntStore.get(level, "amethyst_resonator", blockPos, 1)[0] = 1;
                level.scheduleTick(blockPos, resonator, 4);
                if (level instanceof ServerLevel server) DomainNetwork.recomputeAmethyst(server, blockPos);
                changed = true;
                refreshAuthoritativeSnapshot(); broadcastChanges(); return true;
            } else return false;
            level.setBlock(blockPos, state, Block.UPDATE_CLIENTS);
            if (level instanceof ServerLevel server) DomainNetwork.recomputeAmethyst(server, blockPos);
            changed = true;
        } else if (block instanceof AmethystFrequencyFilterBlock filter) {
            if (id == BUTTON_PRIMARY_PREVIOUS || id == BUTTON_PRIMARY_NEXT) {
                int f = state.getValue(AmethystFrequencyFilterBlock.TARGET);
                f = id == BUTTON_PRIMARY_NEXT ? (f >= 15 ? 1 : f + 1) : (f <= 1 ? 15 : f - 1);
                level.setBlock(blockPos, state.setValue(AmethystFrequencyFilterBlock.TARGET, f), Block.UPDATE_CLIENTS);
                level.scheduleTick(blockPos, filter, 1); changed = true;
            } else changed = route(id);
        } else if (block instanceof AmethystTunedResonatorBlock tuned) {
            if (id == BUTTON_PRIMARY_PREVIOUS || id == BUTTON_PRIMARY_NEXT) {
                int f = state.getValue(AmethystTunedResonatorBlock.NATURAL);
                f = id == BUTTON_PRIMARY_NEXT ? (f >= 15 ? 1 : f + 1) : (f <= 1 ? 15 : f - 1);
                level.setBlock(blockPos, state.setValue(AmethystTunedResonatorBlock.NATURAL, f), Block.UPDATE_CLIENTS);
                level.scheduleTick(blockPos, tuned, 1); changed = true;
            } else if (id == BUTTON_SECONDARY_PREVIOUS || id == BUTTON_SECONDARY_NEXT) {
                int q = state.getValue(AmethystTunedResonatorBlock.Q_INDEX);
                q = id == BUTTON_SECONDARY_NEXT ? (q >= 4 ? 1 : q + 1) : (q <= 1 ? 4 : q - 1);
                level.setBlock(blockPos, state.setValue(AmethystTunedResonatorBlock.Q_INDEX, q), Block.UPDATE_CLIENTS);
                level.scheduleTick(blockPos, tuned, 1); changed = true;
            } else changed = route(id);
        } else return false;

        if (changed) { refreshAuthoritativeSnapshot(); broadcastChanges(); }
        return changed;
    }

    private boolean route(int id) {
        return switch (id) {
            case BUTTON_ROTATE_LEFT -> DirectionalDomainBlock.rotateWholeRoute(level, blockPos, false);
            case BUTTON_ROTATE_RIGHT -> DirectionalDomainBlock.rotateWholeRoute(level, blockPos, true);
            case BUTTON_INPUT_LEFT -> DirectionalDomainBlock.rotateSeriesInput(level, blockPos, false);
            case BUTTON_INPUT_RIGHT -> DirectionalDomainBlock.rotateSeriesInput(level, blockPos, true);
            case BUTTON_OUTPUT_LEFT -> DirectionalDomainBlock.rotateSeriesOutput(level, blockPos, false);
            case BUTTON_OUTPUT_RIGHT -> DirectionalDomainBlock.rotateSeriesOutput(level, blockPos, true);
            default -> false;
        };
    }

    public int kind() { return kind.get(); } public int primary() { return primary.get(); }
    public int secondary() { return secondary.get(); } public int tertiary() { return tertiary.get(); }
    public int auxiliary() { return auxiliary.get(); } public int extraA() { return extraA.get(); }
    public int extraB() { return extraB.get(); } public int stateFlag() { return stateFlag.get(); }
    public PortQuality quality() { int o=quality.get(); PortQuality[] a=PortQuality.values(); return o<0||o>=a.length?PortQuality.NO_SIGNAL:a[o]; }
    public Direction facing() { int o=outputFacing.get(); Direction[] a=Direction.values(); return o<0||o>=a.length?Direction.NORTH:a[o]; }
    public boolean directional() { return kind.get()==KIND_FILTER || kind.get()==KIND_TUNED; }
    public boolean hasInputEndpoint() { return directional() && inputFacing.get() >= 0; }
    public boolean hasOutputEndpoint() { return directional() && outputFacing.get() >= 0; }
}
