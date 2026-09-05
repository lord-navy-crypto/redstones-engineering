package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.blockentity.MechatronicsVisualBlockEntity;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.physics.PneumaticNetwork;
import dev.redstoneengineering.ui.FieldDeviceUi;
import dev.redstoneengineering.visualization.MechatronicsVisualState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/** Redstone-commanded inline valve. BACK is pneumatic inlet, FRONT outlet, UP is opening command. */
public class PneumaticProportionalValveBlock extends DirectionalDomainBlock implements EntityBlock, EngineeringPortProvider {
    public PneumaticProportionalValveBlock(Properties properties) { super(properties); }

    @Override public MapCodec<PneumaticProportionalValveBlock> codec() {
        return RedstoneEngineering.PNEUMATIC_PROPORTIONAL_VALVE_CODEC.value();
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort(
                        "PNEUMATIC IN", inputSide(state), EngineeringDomain.PNEUMATIC,
                        PortKind.CONTROL, PortDirection.INPUT, false, "pressure"
                ),
                new EngineeringPort(
                        "PNEUMATIC OUT", outputSide(state), EngineeringDomain.PNEUMATIC,
                        PortKind.CONTROL, PortDirection.OUTPUT, false, "pressure"
                ),
                new EngineeringPort(
                        "OPENING COMMAND", Direction.UP, EngineeringDomain.REDSTONE,
                        PortKind.CONTROL, PortDirection.INPUT, true, "signal"
                )
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> descriptor = engineeringPort(state, side);
        if (descriptor.isEmpty()) return Optional.empty();
        if (side == Direction.UP) {
            return Optional.of(EngineeringPortSnapshot.redstone(
                    descriptor.get(), opening(level, pos), PortQuality.VALID
            ));
        }
        int pressure = PneumaticNetwork.pressure(level, pos.relative(side));
        return Optional.of(new EngineeringPortSnapshot(
                descriptor.get(), pressure, 0.0, 100.0,
                pressure > 0 ? PortQuality.VALID : PortQuality.NO_SIGNAL
        ));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MechatronicsVisualBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction side) {
        return side != null && side.getOpposite() == Direction.UP;
    }

    public static int opening(Level level, BlockPos pos) {
        return Math.max(0, Math.min(15, level.getSignal(pos.above(), Direction.UP)));
    }

    /** Renderer-facing immutable projection; reads command/pressure but never writes simulation state. */
    public static MechatronicsVisualState visualState(Level level, BlockPos pos) {
        return MechatronicsVisualState.valve(opening(level, pos), PneumaticNetwork.pressure(level, pos));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (level instanceof ServerLevel server) {
            PneumaticNetwork.recomputeAround(server, pos);
            MechatronicsVisualBlockEntity.push(server, pos, visualState(server, pos));
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean moved) {
        if (level instanceof ServerLevel server) {
            PneumaticNetwork.recompute(server, pos);
            MechatronicsVisualBlockEntity.push(server, pos, visualState(server, pos));
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            InformationRuntime.clear(level, "pneumatic", pos);
            PneumaticNetwork.recomputeAround(server, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                player.displayClientMessage(Component.literal(
                        "Proportional valve opening=" + opening(level, pos) + "/15 pressure="
                                + PneumaticNetwork.pressure(level, pos) + "/100 | UP=command BACK→FRONT"
                ), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
