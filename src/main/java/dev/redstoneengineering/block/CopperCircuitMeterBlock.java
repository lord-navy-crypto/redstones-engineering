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
import dev.redstoneengineering.physics.CircuitPhysics;
import dev.redstoneengineering.physics.DomainNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/** Non-invasive copper-domain meter. FACING is the single measurement face. */
public class CopperCircuitMeterBlock extends DomainBlock implements EngineeringPortProvider {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public CopperCircuitMeterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    public MapCodec<CopperCircuitMeterBlock> codec() {
        return RedstoneEngineering.COPPER_CIRCUIT_METER_CODEC.value();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(new EngineeringPort(
                "MEASURE",
                state.getValue(FACING),
                EngineeringDomain.COPPER,
                PortKind.MEASUREMENT,
                PortDirection.INPUT,
                false,
                "V-eq"
        ));
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level,
            BlockPos pos,
            BlockState state,
            Direction side
    ) {
        Optional<EngineeringPort> descriptor = engineeringPort(state, side);
        if (descriptor.isEmpty()) return Optional.empty();
        BlockPos target = pos.relative(side);
        int voltage = DomainNetwork.sampleCopperVoltage(level, target, pos);
        return Optional.of(new EngineeringPortSnapshot(
                descriptor.get(),
                voltage,
                0.0,
                15.0,
                PortQuality.VALID
        ));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockPos target = pos.relative(state.getValue(FACING));
            BlockState targetState = level.getBlockState(target);
            int voltage = DomainNetwork.sampleCopperVoltage(level, target, pos);
            double resistance = targetState.getBlock() instanceof CopperResistiveLoadBlock
                    ? targetState.getValue(CopperResistiveLoadBlock.RESISTANCE)
                    : CircuitPhysics.equivalentLoadResistance(level, target, 128);
            double current = CircuitPhysics.current(voltage, resistance);
            double power = voltage * current;
            player.displayClientMessage(Component.literal(String.format(
                    "Copper circuit meter | FACING measurement probe | V=%.2f | Req=%.2f | I≈%.3f | P≈%.3f",
                    (double) voltage,
                    resistance,
                    current,
                    power
            )), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
