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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Static Minecraft-scale scalar magnetic field source; field coupling is free-space, never wired. */
public class PermanentMagnetBlock extends DomainBlock implements EngineeringPortProvider {
    public static final int MIN_STRENGTH = 1;
    public static final int MAX_STRENGTH = 15;
    public static final int DEFAULT_STRENGTH = 8;
    public static final int STRENGTH_STEP = 1;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty STRENGTH =
            IntegerProperty.create("strength", MIN_STRENGTH, MAX_STRENGTH);

    public record SourceEvidence(int strength, Direction northMarker, boolean scalarFieldModel, boolean wired) {}

    public PermanentMagnetBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(STRENGTH, DEFAULT_STRENGTH));
    }

    @Override public MapCodec<PermanentMagnetBlock> codec() { return RedstoneEngineering.PERMANENT_MAGNET_CODEC.value(); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, STRENGTH); }

    public static SourceEvidence evidence(BlockState state) {
        return new SourceEvidence(state.getValue(STRENGTH), state.getValue(FACING), true, false);
    }

    public static int boundedStrength(int strength) {
        return Math.max(MIN_STRENGTH, Math.min(MAX_STRENGTH, strength));
    }

    public static boolean stepStrength(Level level, BlockPos pos, boolean forward) {
        if (level.isClientSide) return false;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PermanentMagnetBlock)) return false;
        int current = boundedStrength(state.getValue(STRENGTH));
        int span = MAX_STRENGTH - MIN_STRENGTH + 1;
        int next = MIN_STRENGTH + Math.floorMod(
                current - MIN_STRENGTH + (forward ? STRENGTH_STEP : -STRENGTH_STEP), span);
        if (next == current) return false;
        level.setBlock(pos, state.setValue(STRENGTH, next), Block.UPDATE_CLIENTS);
        return true;
    }

    /** Six diagnostic faces describe free-space observation of one scalar source, not six wired outputs. */
    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return Arrays.stream(Direction.values())
                .map(side -> new EngineeringPort(
                        "MAGNETIC FIELD " + side.getName().toUpperCase(), side,
                        EngineeringDomain.IRON_MAGNETIC, PortKind.AUXILIARY,
                        PortDirection.OUTPUT, false, "field"))
                .toList();
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        SourceEvidence evidence = evidence(state);
        return engineeringPort(state, side).map(port -> new EngineeringPortSnapshot(
                port, evidence.strength(), 0.0, MAX_STRENGTH, PortQuality.VALID));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!player.isShiftKeyDown()) {
                FieldDeviceUi.open(serverPlayer, pos);
                return InteractionResult.CONSUME;
            }
            stepStrength(level, pos, true);
            BlockState next = level.getBlockState(pos);
            SourceEvidence evidence = evidence(next);
            player.displayClientMessage(Component.literal(
                    "Permanent magnet | scalar free-space B-source=" + evidence.strength() + "/" + MAX_STRENGTH
                            + " | N-marker=" + evidence.northMarker() + " (orientation marker only in scalar solver)"
                            + " | wired=" + evidence.wired()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
