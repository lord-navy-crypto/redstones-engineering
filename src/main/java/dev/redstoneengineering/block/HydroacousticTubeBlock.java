package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Configurable six-way hydroacoustic waveguide with transient pressure-wave diagnostics. */
public class HydroacousticTubeBlock extends Block implements EngineeringPortProvider {
    public static final IntegerProperty MEDIUM = IntegerProperty.create("medium", 0, 2);
    public static final int PACKET_TTL_TICKS = 4;

    public HydroacousticTubeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(MEDIUM, 0));
    }

    @Override
    public MapCodec<HydroacousticTubeBlock> codec() {
        return RedstoneEngineering.HYDROACOUSTIC_TUBE_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MEDIUM);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return Arrays.stream(Direction.values())
                .map(side -> new EngineeringPort(
                        "PRESSURE WAVE", side, EngineeringDomain.HYDROACOUSTIC,
                        PortKind.BUS, PortDirection.BIDIRECTIONAL, false, "amplitude"))
                .toList();
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        int amplitude = InformationRuntime.value(level, "hydro", pos);
        boolean valid = InformationRuntime.valid(level, "hydro", pos) && amplitude > 0;
        return Optional.of(new EngineeringPortSnapshot(
                port.get(), amplitude, 0.0, 15.0,
                valid ? PortQuality.VALID : PortQuality.NO_SIGNAL));
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int amplitude = InformationRuntime.value(level, "hydro", pos);
        if (amplitude <= 0 || !InformationRuntime.valid(level, "hydro", pos)) {
            InformationRuntime.clear(level, "hydro", pos);
            return;
        }
        int next = Math.max(0, amplitude - 2);
        if (next == 0) {
            InformationRuntime.clear(level, "hydro", pos);
        } else {
            InformationRuntime.write(level, "hydro", pos, next,
                    InformationRuntime.aux(level, "hydro", pos), true,
                    Math.max(0, InformationRuntime.quality(level, "hydro", pos) - 10));
            level.scheduleTick(pos, this, PACKET_TTL_TICKS);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) InformationRuntime.clear(level, "hydro", pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit
    ) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                int medium = (state.getValue(MEDIUM) + 1) % 3;
                level.setBlock(pos, state.setValue(MEDIUM, medium), Block.UPDATE_CLIENTS);
                String name = medium == 0 ? "water" : medium == 1 ? "milk-model" : "lava";
                player.displayClientMessage(Component.literal("Hydroacoustic medium=" + name), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
