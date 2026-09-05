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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/** Facing-only, observer-neutral Lapis precision meter. */
public class LapisPrecisionMeterBlock extends DomainBlock implements EngineeringPortProvider {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public LapisPrecisionMeterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override public MapCodec<LapisPrecisionMeterBlock> codec() { return RedstoneEngineering.LAPIS_PRECISION_METER_CODEC.value(); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite()); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(new EngineeringPort(
                "LAPIS MEASURE",
                state.getValue(FACING),
                EngineeringDomain.LAPIS,
                PortKind.MEASUREMENT,
                PortDirection.INPUT,
                false,
                "precision"
        ));
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        DomainNetwork.LapisSample sample = DomainNetwork.sampleLapis(level, pos.relative(state.getValue(FACING)));
        return Optional.of(new EngineeringPortSnapshot(
                port.get(), sample.value(), 0.0, 100.0,
                sample.valid() ? PortQuality.VALID : PortQuality.NO_SIGNAL));
    }

    public static DomainNetwork.LapisSample sampledValue(Level level, BlockPos pos, BlockState state) {
        return DomainNetwork.sampleLapis(level, pos.relative(state.getValue(FACING)));
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            DomainNetwork.LapisSample sample = sampledValue(level, pos, state);
            player.displayClientMessage(Component.literal(sample.valid()
                    ? "Lapis precision meter | observer only | value=" + String.format("%.3f", sample.value() / 100.0) + " | resolution=0.01"
                    : "Lapis precision meter | observer only | INVALID / no unique source"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
