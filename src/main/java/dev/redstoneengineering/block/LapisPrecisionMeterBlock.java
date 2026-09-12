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
import dev.redstoneengineering.physics.PrecisionObservationSupport;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
    private static final Direction[] ROUTE_ORDER = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP, Direction.DOWN
    };

    public record MeterReading(int value, PortQuality quality) {}

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
                "LAPIS MEASURE", state.getValue(FACING), EngineeringDomain.LAPIS,
                PortKind.MEASUREMENT, PortDirection.INPUT, false, "precision"));
    }

    public static MeterReading reading(Level level, BlockPos pos, BlockState state) {
        PrecisionObservationSupport.LapisObservation observation = PrecisionObservationSupport.lapis(
                level, pos.relative(state.getValue(FACING)));
        return new MeterReading(observation.value(), observation.quality());
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        MeterReading reading = reading(level, pos, state);
        return Optional.of(new EngineeringPortSnapshot(
                port.get(), reading.value(), 0.0, 100.0, reading.quality()));
    }

    public static DomainNetwork.LapisSample sampledValue(Level level, BlockPos pos, BlockState state) {
        MeterReading reading = reading(level, pos, state);
        return new DomainNetwork.LapisSample(reading.value(), reading.quality() == PortQuality.VALID);
    }

    /** Server-authoritative six-face measurement aperture routing. */
    public static boolean rotateMeasurementFace(Level level, BlockPos pos, boolean forward) {
        if (level.isClientSide) return false;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof LapisPrecisionMeterBlock block)) return false;
        Direction oldFace = state.getValue(FACING);
        Direction newFace = cycleFace(oldFace, forward);
        if (newFace == oldFace) return false;
        level.setBlock(pos, state.setValue(FACING, newFace), Block.UPDATE_CLIENTS);
        level.updateNeighborsAt(pos, block);
        return true;
    }

    private static Direction cycleFace(Direction current, boolean forward) {
        int index = 0;
        for (int i = 0; i < ROUTE_ORDER.length; i++) {
            if (ROUTE_ORDER[i] == current) { index = i; break; }
        }
        int next = Math.floorMod(index + (forward ? 1 : -1), ROUTE_ORDER.length);
        return ROUTE_ORDER[next];
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                MeterReading reading = reading(level, pos, state);
                player.displayClientMessage(Component.literal(switch (reading.quality()) {
                    case VALID -> "Lapis precision meter | observer only | face=" + state.getValue(FACING).getName().toUpperCase()
                            + " | value=" + String.format("%.3f", reading.value() / 100.0) + " | resolution=0.01";
                    case TOPOLOGY_ERROR -> "Lapis precision meter | observer only | face=" + state.getValue(FACING).getName().toUpperCase()
                            + " | SOURCE CONFLICT — no arbitrary source selected";
                    case STALE -> "Lapis precision meter | observer only | face=" + state.getValue(FACING).getName().toUpperCase()
                            + " | STALE / sample aperture not currently observable";
                    default -> "Lapis precision meter | observer only | face=" + state.getValue(FACING).getName().toUpperCase()
                            + " | INVALID / no unique source";
                }), true);
            } else {
                FieldDeviceUi.openUniversal(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
