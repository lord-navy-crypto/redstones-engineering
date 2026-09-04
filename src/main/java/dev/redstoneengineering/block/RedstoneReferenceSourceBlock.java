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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/** Adjustable 0..15 laboratory reference source with a single FRONT output port. */
public class RedstoneReferenceSourceBlock extends DirectionalRedstoneEndpointBlock implements EngineeringPortProvider {
    public static final IntegerProperty POWER = IntegerProperty.create("power", 0, 15);

    public RedstoneReferenceSourceBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(POWER, 0));
    }

    @Override public MapCodec<RedstoneReferenceSourceBlock> codec() { return RedstoneEngineering.REDSTONE_REFERENCE_SOURCE_CODEC.value(); }
    @Override public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return direction != null && connectionMatches(direction, frontSide(state));
    }
    @Override protected boolean isSignalSource(BlockState state) { return true; }
    @Override protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return isQueriedFrom(state, direction, frontSide(state)) ? state.getValue(POWER) : 0;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWER);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(new EngineeringPort(
                "REFERENCE OUT",
                frontSide(state),
                EngineeringDomain.REDSTONE,
                PortKind.REDSTONE_ANALOG,
                PortDirection.OUTPUT,
                true,
                "signal"
        ));
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        return engineeringPort(state, side).map(port -> EngineeringPortSnapshot.redstone(
                port, state.getValue(POWER), PortQuality.VALID));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                int value = state.getValue(POWER) - 1;
                if (value < 0) value = 15;
                BlockState next = state.setValue(POWER, value);
                level.setBlock(pos, next, Block.UPDATE_CLIENTS);
                notifyFrontOutput(level, pos, next);
                player.displayClientMessage(Component.literal(
                        "Redstone Reference Source = " + value + "/15 | FRONT OUT=" + frontSide(next).getName()), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
