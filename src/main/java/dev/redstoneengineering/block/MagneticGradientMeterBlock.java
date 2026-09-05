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
import dev.redstoneengineering.physics.MagneticPhysics;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Differential observer of the free-space magnetic field. */
public class MagneticGradientMeterBlock extends DomainBlock implements EngineeringPortProvider {
    public MagneticGradientMeterBlock(Properties properties) { super(properties); }
    @Override public MapCodec<MagneticGradientMeterBlock> codec() { return RedstoneEngineering.MAGNETIC_GRADIENT_METER_CODEC.value(); }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return Arrays.stream(Direction.values())
                .map(side -> new EngineeringPort(
                        "GRADIENT APERTURE " + side.getName().toUpperCase(), side,
                        EngineeringDomain.IRON_MAGNETIC, PortKind.MEASUREMENT,
                        PortDirection.INPUT, false, "field-gradient"))
                .toList();
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        int component = switch (side.getAxis()) {
            case X -> gradientX(level, pos);
            case Y -> gradientY(level, pos);
            case Z -> gradientZ(level, pos);
        };
        if (side.getAxisDirection() == Direction.AxisDirection.NEGATIVE) component = -component;
        return Optional.of(new EngineeringPortSnapshot(
                port.get(), component, -15.0, 15.0, component == 0 ? PortQuality.NO_SIGNAL : PortQuality.VALID));
    }

    public static int gradientX(Level level, BlockPos pos) { return MagneticPhysics.fieldAt(level, pos.east(), 6) - MagneticPhysics.fieldAt(level, pos.west(), 6); }
    public static int gradientY(Level level, BlockPos pos) { return MagneticPhysics.fieldAt(level, pos.above(), 6) - MagneticPhysics.fieldAt(level, pos.below(), 6); }
    public static int gradientZ(Level level, BlockPos pos) { return MagneticPhysics.fieldAt(level, pos.south(), 6) - MagneticPhysics.fieldAt(level, pos.north(), 6); }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!player.isShiftKeyDown()) {
                FieldDeviceUi.open(serverPlayer, pos);
                return InteractionResult.CONSUME;
            }
            player.displayClientMessage(Component.literal(
                    "Magnetic gradient meter | ΔBx=" + gradientX(level, pos)
                            + " | ΔBy=" + gradientY(level, pos)
                            + " | ΔBz=" + gradientZ(level, pos)
                            + " | local B=" + MagneticPhysics.fieldAt(level, pos, 6)), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
