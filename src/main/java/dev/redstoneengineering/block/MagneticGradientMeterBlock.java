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
    private static final int RADIUS = 6;

    public record GradientSample(int value, int scannedCells, int expectedCells, boolean complete) {}

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
        GradientSample sample = gradient(level, pos, side.getAxis());
        int component = side.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? -sample.value() : sample.value();
        return Optional.of(new EngineeringPortSnapshot(
                port.get(), component, -15.0, 15.0,
                sample.complete() ? PortQuality.VALID : PortQuality.STALE));
    }

    /** A true zero spatial difference is a valid measurement when both apertures are fully covered. */
    public static GradientSample gradient(Level level, BlockPos pos, Direction.Axis axis) {
        BlockPos positive = switch (axis) {
            case X -> pos.east();
            case Y -> pos.above();
            case Z -> pos.south();
        };
        BlockPos negative = switch (axis) {
            case X -> pos.west();
            case Y -> pos.below();
            case Z -> pos.north();
        };
        MagneticPhysics.FieldSample plus = MagneticPhysics.fieldSample(level, positive, RADIUS);
        MagneticPhysics.FieldSample minus = MagneticPhysics.fieldSample(level, negative, RADIUS);
        return new GradientSample(
                plus.field() - minus.field(),
                plus.scannedCells() + minus.scannedCells(),
                plus.expectedCells() + minus.expectedCells(),
                plus.complete() && minus.complete());
    }

    public static int gradientX(Level level, BlockPos pos) { return gradient(level, pos, Direction.Axis.X).value(); }
    public static int gradientY(Level level, BlockPos pos) { return gradient(level, pos, Direction.Axis.Y).value(); }
    public static int gradientZ(Level level, BlockPos pos) { return gradient(level, pos, Direction.Axis.Z).value(); }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!player.isShiftKeyDown()) {
                FieldDeviceUi.open(serverPlayer, pos);
                return InteractionResult.CONSUME;
            }
            GradientSample gx = gradient(level, pos, Direction.Axis.X);
            GradientSample gy = gradient(level, pos, Direction.Axis.Y);
            GradientSample gz = gradient(level, pos, Direction.Axis.Z);
            boolean complete = gx.complete() && gy.complete() && gz.complete();
            player.displayClientMessage(Component.literal(
                    "Magnetic gradient meter | ΔBx=" + gx.value()
                            + " | ΔBy=" + gy.value()
                            + " | ΔBz=" + gz.value()
                            + " | local B=" + MagneticPhysics.fieldAt(level, pos, RADIUS)
                            + " | " + (complete ? "VALID" : "INCOMPLETE")), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
