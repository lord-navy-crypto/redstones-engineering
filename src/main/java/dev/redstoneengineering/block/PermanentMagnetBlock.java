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
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty STRENGTH = IntegerProperty.create("strength", 1, 15);

    public record SourceEvidence(int strength, Direction northMarker, boolean scalarFieldModel, boolean wired) {}

    public PermanentMagnetBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(STRENGTH, 8));
    }

    @Override public MapCodec<PermanentMagnetBlock> codec() { return RedstoneEngineering.PERMANENT_MAGNET_CODEC.value(); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, STRENGTH); }

    public static SourceEvidence evidence(BlockState state) {
        return new SourceEvidence(state.getValue(STRENGTH), state.getValue(FACING), true, false);
    }

    /** Six diagnostic faces describe free-space observation of one scalar source, not six wired outputs. */
    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return Arrays.stream(Direction.values())
                .map(side -> new EngineeringPort(
                        "SCALAR MAGNETIC FIELD " + side.getName().toUpperCase(), side,
                        EngineeringDomain.IRON_MAGNETIC, PortKind.AUXILIARY,
                        PortDirection.OUTPUT, false, "field"))
                .toList();
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        SourceEvidence evidence = evidence(state);
        return engineeringPort(state, side).map(port -> new EngineeringPortSnapshot(
                port, evidence.strength(), 0.0, 15.0, PortQuality.VALID));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!player.isShiftKeyDown()) {
                FieldDeviceUi.open(serverPlayer, pos);
                return InteractionResult.CONSUME;
            }
            int strength = state.getValue(STRENGTH);
            BlockState next = state.setValue(STRENGTH, strength >= 15 ? 1 : strength + 1);
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            SourceEvidence evidence = evidence(next);
            player.displayClientMessage(Component.literal(
                    "Permanent magnet | scalar free-space B-source=" + evidence.strength() + "/15"
                            + " | N-marker=" + evidence.northMarker() + " (orientation marker only in scalar solver)"
                            + " | wired=" + evidence.wired()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
