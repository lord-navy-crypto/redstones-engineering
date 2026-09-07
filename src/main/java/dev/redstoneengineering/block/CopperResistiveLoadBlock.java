package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.physics.CopperNetworkSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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

/** Terminal copper load: it may be fed from any face but never propagates through itself. */
public class CopperResistiveLoadBlock extends DomainBlock implements EngineeringPortProvider {
    public static final IntegerProperty RESISTANCE = IntegerProperty.create("resistance", 1, 15);
    /** Coarse cached body voltage retained for compatibility; snapshots resolve the actual input face. */
    public static final IntegerProperty VOLTAGE = IntegerProperty.create("voltage", 0, 15);

    public CopperResistiveLoadBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(RESISTANCE, 4).setValue(VOLTAGE, 0));
    }

    @Override
    public MapCodec<CopperResistiveLoadBlock> codec() {
        return RedstoneEngineering.COPPER_RESISTIVE_LOAD_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RESISTANCE, VOLTAGE);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return Arrays.stream(Direction.values())
                .map(side -> new EngineeringPort(
                        "COPPER LOAD", side, EngineeringDomain.COPPER,
                        PortKind.ELECTRICAL, PortDirection.INPUT, false, "V-eq"))
                .toList();
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> descriptor = engineeringPort(state, side);
        if (descriptor.isEmpty()) return Optional.empty();
        CopperNetworkSupport.TerminalInput input = CopperNetworkSupport.terminalInputOnSide(level, pos, side);
        return Optional.of(new EngineeringPortSnapshot(
                descriptor.get(), input.voltage(), 0.0, 15.0, input.quality()));
    }

    /** Aggregate terminal evidence for UI/diagnostics; INPUT-only neighbors never count as feeds. */
    public static CopperNetworkSupport.TerminalInput input(Level level, BlockPos pos) {
        return CopperNetworkSupport.terminalInput(level, pos);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, movedByPiston);
        if (level instanceof ServerLevel serverLevel) CopperNetworkSupport.recomputeAround(serverLevel, pos);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        // A load is always a sink. Recompute from adjacent components so it can never
        // become the traversal seed that accidentally joins two otherwise isolated nets.
        if (level instanceof ServerLevel serverLevel) CopperNetworkSupport.recomputeAround(serverLevel, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            CopperNetworkSupport.recomputeAround(serverLevel, pos);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockState next = state;
            if (!player.isShiftKeyDown()) {
                int resistance = state.getValue(RESISTANCE);
                next = state.setValue(RESISTANCE, resistance >= 15 ? 1 : resistance + 1);
                level.setBlock(pos, next, Block.UPDATE_CLIENTS);
                if (level instanceof ServerLevel serverLevel) CopperNetworkSupport.recomputeAround(serverLevel, pos);
            }
            CopperNetworkSupport.TerminalInput input = CopperNetworkSupport.terminalInput(level, pos);
            double voltage = input.voltage();
            double resistance = next.getValue(RESISTANCE);
            double current = voltage / resistance;
            double power = voltage * current;
            player.displayClientMessage(Component.literal(String.format(
                    "Electrical load | terminal sink | feeds=%d | %s | V=%.1f | R=%.1f | I=V/R=%.2f | P=VI=%.2f",
                    input.connectedFeeds(), input.quality(), voltage, resistance, current, power)), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
