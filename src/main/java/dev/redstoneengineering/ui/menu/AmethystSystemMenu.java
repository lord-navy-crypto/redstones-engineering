package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.block.*;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.signal.AmethystTunedResonatorLogic;
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
    public static final int BUTTON_COUPLING_PREVIOUS = 11;
    public static final int BUTTON_COUPLING_NEXT = 12;
    public static final int BUTTON_DECAY_PREVIOUS = 13;
    public static final int BUTTON_DECAY_NEXT = 14;

    private final DataSlot kind = trackedInt();
    private final DataSlot primary = trackedInt();
    private final DataSlot secondary = trackedInt();
    private final DataSlot tertiary = trackedInt();
    private final DataSlot auxiliary = trackedInt();
    private final DataSlot extraA = trackedInt();
    private final DataSlot extraB = trackedInt();
    private final DataSlot extraC = trackedInt();
    private final DataSlot extraD = trackedInt();
    private final DataSlot extraE = trackedInt();
    private final DataSlot extraF = trackedInt();
    private final DataSlot extraG = trackedInt();
    private final DataSlot extraH = trackedInt();
    private final DataSlot quality = trackedInt();
    private final DataSlot outputQuality = trackedInt();
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
        extraC.set(0); extraD.set(0); extraE.set(0); extraF.set(0); extraG.set(0); extraH.set(0);
        quality.set(PortQuality.NO_SIGNAL.ordinal()); outputQuality.set(PortQuality.NO_SIGNAL.ordinal());
        inputFacing.set(-1); outputFacing.set(-1); stateFlag.set(0);

        if (block instanceof AmethystResonatorBlock) {
            kind.set(KIND_SOURCE);
            primary.set(state.getValue(AmethystResonatorBlock.FREQUENCY));
            secondary.set(state.getValue(AmethystResonatorBlock.AMPLITUDE));
            tertiary.set(AmethystResonatorBlock.currentAmplitude(level, blockPos));
            auxiliary.set(AmethystResonatorBlock.excitationCount(level, blockPos));
            stateFlag.set(AmethystResonatorBlock.isActive(level, blockPos) ? 1 : 0);
            int sourceQuality = stateFlag.get() == 1 ? PortQuality.VALID.ordinal() : PortQuality.NO_SIGNAL.ordinal();
            quality.set(sourceQuality);
            outputQuality.set(sourceQuality);
        } else if (block instanceof AmethystFrequencyFilterBlock filter) {
            kind.set(KIND_FILTER);
            AmethystFrequencyFilterBlock.FilterEvidence e = AmethystFrequencyFilterBlock.evidence(level, blockPos, state);
            primary.set(e.inputFrequency()); secondary.set(e.inputAmplitude()); tertiary.set(e.targetFrequency());
            auxiliary.set(e.expectedOutputAmplitude()); stateFlag.set(e.matched() ? 1 : 0);
            quality.set(e.inputQuality().ordinal());
            Direction filterInput = DirectionalDomainBlock.seriesInputSide(state);
            Direction filterOutput = DirectionalDomainBlock.seriesOutputSide(state);
            inputFacing.set(filterInput.ordinal());
            outputFacing.set(filterOutput.ordinal());
            outputQuality.set(filter.engineeringSnapshot(level, blockPos, state, filterOutput)
                    .map(snapshot -> snapshot.quality().ordinal()).orElse(PortQuality.NO_SIGNAL.ordinal()));
        } else if (block instanceof AmethystTunedResonatorBlock tuned) {
            kind.set(KIND_TUNED);
            AmethystTunedResonatorBlock.ResponseEvidence e = AmethystTunedResonatorBlock.response(level, blockPos, state);
            primary.set(e.inputFrequency()); secondary.set(e.inputAmplitude()); tertiary.set(e.naturalFrequency());
            auxiliary.set(e.qIndex()); extraA.set(e.bandwidth()); extraB.set(e.targetAmplitude());
            extraC.set(e.actualAmplitude()); extraD.set(e.outputFrequency()); extraE.set(e.frequencyError());
            extraF.set(AmethystTunedResonatorLogic.responseStep(e.qIndex()));
            extraG.set(e.couplingIndex());
            extraH.set(e.decayRate());
            stateFlag.set(e.saturated() ? 2 : e.ringDown() ? 3 : e.responding() ? 1 : 0);
            quality.set(e.inputQuality().ordinal());
            Direction tunedInput = DirectionalDomainBlock.seriesInputSide(state);
            Direction tunedOutput = DirectionalDomainBlock.seriesOutputSide(state);
            inputFacing.set(tunedInput.ordinal());
            outputFacing.set(tunedOutput.ordinal());
            outputQuality.set(tuned.engineeringSnapshot(level, blockPos, state, tunedOutput)
                    .map(snapshot -> snapshot.quality().ordinal()).orElse(PortQuality.NO_SIGNAL.ordinal()));
        } else if (block instanceof AmethystSpectrumAnalyzerBlock) {
            kind.set(KIND_SPECTRUM);
            AmethystSpectrumAnalyzerBlock.Spectrum s = AmethystSpectrumAnalyzerBlock.spectrum(level, blockPos);
            primary.set(s.dominantFrequency()); secondary.set(s.energy()); tertiary.set(s.activeBands());
            auxiliary.set(s.samples()); extraA.set(s.conflicts()); extraB.set(s.scannedCells()); stateFlag.set(s.expectedCells());
            int spectrumQuality = AmethystSpectrumAnalyzerBlock.quality(level, blockPos).ordinal();
            quality.set(spectrumQuality);
            outputQuality.set(spectrumQuality);
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
                AmethystResonatorBlock.excite(level, blockPos, state);
                level.scheduleTick(blockPos, resonator, 2);
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
            } else if (id == BUTTON_COUPLING_PREVIOUS || id == BUTTON_COUPLING_NEXT) {
                int coupling = state.getValue(AmethystTunedResonatorBlock.COUPLING);
                coupling = id == BUTTON_COUPLING_NEXT
                        ? (coupling >= 4 ? 1 : coupling + 1)
                        : (coupling <= 1 ? 4 : coupling - 1);
                level.setBlock(blockPos, state.setValue(AmethystTunedResonatorBlock.COUPLING, coupling), Block.UPDATE_CLIENTS);
                level.scheduleTick(blockPos, tuned, 1); changed = true;
            } else if (id == BUTTON_DECAY_PREVIOUS || id == BUTTON_DECAY_NEXT) {
                int decay = state.getValue(AmethystTunedResonatorBlock.DECAY_RATE);
                decay = id == BUTTON_DECAY_NEXT ? (decay >= 4 ? 1 : decay + 1) : (decay <= 1 ? 4 : decay - 1);
                level.setBlock(blockPos, state.setValue(AmethystTunedResonatorBlock.DECAY_RATE, decay), Block.UPDATE_CLIENTS);
                level.scheduleTick(blockPos, tuned, 1); changed = true;
            } else changed = route(id);
        } else return false;

        if (changed) { refreshAuthoritativeSnapshot(); broadcastChanges(); }
        return changed;
    }

    private boolean route(int id) {
        // Amethyst filter/resonator blocks are rigid two-port series devices: RX is always the
        // face opposite TX. Legacy endpoint button IDs are accepted, but every action rotates
        // the complete block route so an old client cannot create a bent RX/TX path.
        return switch (id) {
            case BUTTON_ROTATE_LEFT, BUTTON_INPUT_LEFT, BUTTON_OUTPUT_LEFT ->
                    DirectionalDomainBlock.rotateRigidSeriesAxis(level, blockPos, false);
            case BUTTON_ROTATE_RIGHT, BUTTON_INPUT_RIGHT, BUTTON_OUTPUT_RIGHT ->
                    DirectionalDomainBlock.rotateRigidSeriesAxis(level, blockPos, true);
            default -> false;
        };
    }

    public int kind() { return kind.get(); } public int primary() { return primary.get(); }
    public int secondary() { return secondary.get(); } public int tertiary() { return tertiary.get(); }
    public int auxiliary() { return auxiliary.get(); } public int extraA() { return extraA.get(); }
    public int extraB() { return extraB.get(); } public int extraC() { return extraC.get(); }
    public int extraD() { return extraD.get(); } public int extraE() { return extraE.get(); }
    public int extraF() { return extraF.get(); } public int extraG() { return extraG.get(); }
    public int extraH() { return extraH.get(); } public int stateFlag() { return stateFlag.get(); }
    public PortQuality quality() { int o=quality.get(); PortQuality[] a=PortQuality.values(); return o<0||o>=a.length?PortQuality.NO_SIGNAL:a[o]; }
    public PortQuality outputQuality() { int o=outputQuality.get(); PortQuality[] a=PortQuality.values(); return o<0||o>=a.length?PortQuality.NO_SIGNAL:a[o]; }
    public Direction facing() { int o=outputFacing.get(); Direction[] a=Direction.values(); return o<0||o>=a.length?Direction.NORTH:a[o]; }
    public boolean directional() { return kind.get()==KIND_FILTER || kind.get()==KIND_TUNED; }
    public boolean hasInputEndpoint() { return directional() && inputFacing.get() >= 0; }
    public boolean hasOutputEndpoint() { return directional() && outputFacing.get() >= 0; }
}
