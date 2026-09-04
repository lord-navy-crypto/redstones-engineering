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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/** Non-invasive channel probe for the RSE instrument network. */
public class SignalProbeBlock extends Block implements EngineeringPortProvider {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final IntegerProperty CHANNEL = IntegerProperty.create("channel", 0, 3);

    public SignalProbeBlock(Properties properties) {
        super(properties);
        registerDefaultState(
                stateDefinition.any()
                        .setValue(FACING, Direction.NORTH)
                        .setValue(CHANNEL, 0)
        );
    }

    @Override
    public MapCodec<SignalProbeBlock> codec() {
        return RedstoneEngineering.SIGNAL_PROBE_CODEC.value();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getClickedFace().getOpposite())
                .setValue(CHANNEL, 0);
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder
    ) {
        builder.add(FACING, CHANNEL);
    }

    private static Direction testSide(BlockState state) {
        return state.getValue(FACING);
    }

    private static Direction busSide(BlockState state) {
        return testSide(state).getOpposite();
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort(
                        "TEST",
                        testSide(state),
                        EngineeringDomain.REDSTONE,
                        PortKind.MEASUREMENT,
                        PortDirection.INPUT,
                        false,
                        "signal"
                ),
                new EngineeringPort(
                        "INSTRUMENT BUS CH " + channelName(state.getValue(CHANNEL)),
                        busSide(state),
                        EngineeringDomain.INSTRUMENT_BUS,
                        PortKind.BUS,
                        PortDirection.OUTPUT,
                        false,
                        "channel"
                )
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level,
            BlockPos pos,
            BlockState state,
            Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        return Optional.of(EngineeringPortSnapshot.redstone(
                port.get(),
                sample(level, pos, state),
                PortQuality.VALID
        ));
    }

    @Override
    public boolean canConnectRedstone(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            @Nullable Direction direction
    ) {
        return false;
    }

    public int sample(Level level, BlockPos pos, BlockState state) {
        Direction targetSide = testSide(state);
        BlockPos targetPos = pos.relative(targetSide);

        // Pass the physical probe direction so directional sources are measured
        // on the face actually connected to the instrument, not by strongest-side output.
        return SignalAnalyzerBlock.measureNode(
                level,
                targetPos,
                level.getBlockState(targetPos),
                targetSide
        );
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                int value = sample(level, pos, state);
                player.displayClientMessage(
                        Component.literal(
                                "Probe " + channelName(state.getValue(CHANNEL))
                                        + " | TEST=" + testSide(state).getName()
                                        + " | BUS=" + busSide(state).getName()
                                        + " | value=" + value + "/15"
                                        + " | direction-aware • non-invasive"
                        ),
                        true
                );
            } else {
                int nextChannel = (state.getValue(CHANNEL) + 1) % 4;
                BlockState next = state.setValue(CHANNEL, nextChannel);
                level.setBlock(pos, next, Block.UPDATE_CLIENTS);

                player.displayClientMessage(
                        Component.literal(
                                "Probe channel → " + channelName(nextChannel)
                                        + " | TEST=" + testSide(next).getName()
                                        + " | BUS=" + busSide(next).getName()
                        ),
                        true
                );
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static String channelName(int channel) {
        return switch (channel) {
            case 0 -> "A";
            case 1 -> "B";
            case 2 -> "C";
            case 3 -> "D";
            default -> "?";
        };
    }
}
