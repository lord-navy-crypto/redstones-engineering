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
import dev.redstoneengineering.physics.DomainNetwork;
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

/** Multi-face copper-domain source node. */
public class CopperVoltageSourceBlock extends DomainBlock implements EngineeringPortProvider {
    public static final IntegerProperty VOLTAGE = IntegerProperty.create("voltage", 0, 15);

    public CopperVoltageSourceBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(VOLTAGE, 12));
    }

    @Override
    public MapCodec<CopperVoltageSourceBlock> codec() {
        return RedstoneEngineering.COPPER_VOLTAGE_SOURCE_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(VOLTAGE);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return Arrays.stream(Direction.values())
                .map(side -> new EngineeringPort(
                        "SOURCE",
                        side,
                        EngineeringDomain.COPPER,
                        PortKind.ELECTRICAL,
                        PortDirection.OUTPUT,
                        false,
                        "V-eq"
                ))
                .toList();
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level,
            BlockPos pos,
            BlockState state,
            Direction side
    ) {
        Optional<EngineeringPort> descriptor = engineeringPort(state, side);
        return descriptor.map(port -> new EngineeringPortSnapshot(
                port,
                state.getValue(VOLTAGE),
                0.0,
                15.0,
                PortQuality.VALID
        ));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel serverLevel) DomainNetwork.recomputeCopper(serverLevel, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel && !state.is(newState.getBlock())) {
            DomainNetwork.recomputeCopper(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            int voltage = state.getValue(VOLTAGE);
            voltage = player.isShiftKeyDown() ? Math.max(0, voltage - 1) : (voltage >= 15 ? 0 : voltage + 1);
            BlockState next = state.setValue(VOLTAGE, voltage);
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            if (level instanceof ServerLevel serverLevel) DomainNetwork.recomputeCopper(serverLevel, pos);
            player.displayClientMessage(Component.literal(
                    "Copper voltage source | multi-face source node | V-level=" + voltage + "/15"
            ), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
