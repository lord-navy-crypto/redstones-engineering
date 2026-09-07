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
import dev.redstoneengineering.physics.OpticalObservationSupport;
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

/** Direct, non-invasive single-point optical carrier meter. */
public class OpticalPowerMeterBlock extends DomainBlock implements EngineeringPortProvider {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public record Measurement(int intensity, int channel, PortQuality quality) {}

    public OpticalPowerMeterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override public MapCodec<OpticalPowerMeterBlock> codec() { return RedstoneEngineering.OPTICAL_POWER_METER_CODEC.value(); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite()); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }

    public static Measurement measurement(Level level, BlockPos pos, BlockState state) {
        OpticalObservationSupport.Observation observation = OpticalObservationSupport.observe(
                level, pos.relative(state.getValue(FACING)));
        return new Measurement(observation.intensity(), observation.channel(), observation.quality());
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(new EngineeringPort(
                "OPTICAL POWER INPUT", state.getValue(FACING), EngineeringDomain.OPTICAL,
                PortKind.MEASUREMENT, PortDirection.INPUT, false, "intensity"));
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        Measurement measurement = measurement(level, pos, state);
        return Optional.of(new EngineeringPortSnapshot(
                port.get(), measurement.intensity(), 0.0, 15.0, measurement.quality()));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && !player.isShiftKeyDown()) {
            FieldDeviceUi.open(serverPlayer, pos);
        } else if (!level.isClientSide) {
            Measurement measurement = measurement(level, pos, state);
            String status = switch (measurement.quality()) {
                case VALID -> "P-index=" + measurement.intensity() + "/15 | channel=" + measurement.channel();
                case TOPOLOGY_ERROR -> "OPTICAL SOURCE / TOPOLOGY CONFLICT";
                case NO_SIGNAL -> "DARK / NO CARRIER";
                default -> measurement.quality().name();
            };
            player.displayClientMessage(Component.literal(
                    "Optical power meter | observer-only | " + status + " | approximate loss observable"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
