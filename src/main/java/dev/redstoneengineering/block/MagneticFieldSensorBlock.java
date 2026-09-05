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

/** Observer-only scalar magnetic-field sensor with six free-space apertures. */
public class MagneticFieldSensorBlock extends DomainBlock implements EngineeringPortProvider {
    public static final IntegerProperty FIELD = IntegerProperty.create("field", 0, 15);

    public MagneticFieldSensorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FIELD, 0));
    }

    @Override public MapCodec<MagneticFieldSensorBlock> codec() { return RedstoneEngineering.MAGNETIC_FIELD_SENSOR_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FIELD); }

    /** Non-wired apertures document omnidirectional free-space sensing for the engineering HUD. */
    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return Arrays.stream(Direction.values())
                .map(side -> new EngineeringPort(
                        "MAGNETIC APERTURE " + side.getName().toUpperCase(), side,
                        EngineeringDomain.IRON_MAGNETIC, PortKind.MEASUREMENT,
                        PortDirection.INPUT, false, "field"))
                .toList();
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        return engineeringPort(state, side).map(port -> EngineeringPortSnapshot.redstone(
                port, state.getValue(FIELD), state.getValue(FIELD) > 0 ? PortQuality.VALID : PortQuality.NO_SIGNAL));
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide) level.scheduleTick(pos, this, 5);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int field = MagneticPhysics.fieldAt(level, pos, 6);
        if (field != state.getValue(FIELD)) level.setBlock(pos, state.setValue(FIELD, field), Block.UPDATE_CLIENTS);
        level.scheduleTick(pos, this, 5);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!player.isShiftKeyDown()) {
                FieldDeviceUi.open(serverPlayer, pos);
                return InteractionResult.CONSUME;
            }
            player.displayClientMessage(Component.literal(
                    "Magnetic field sensor | B-level=" + state.getValue(FIELD)
                            + "/15 | free-space radius=6 | observer-only"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
