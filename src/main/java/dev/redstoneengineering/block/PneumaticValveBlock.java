package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.physics.PneumaticNetwork;
import dev.redstoneengineering.physics.PneumaticObservationSupport;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/** Manual axial isolation valve. BACK and FRONT are the only pneumatic ports; OPEN permits bidirectional flow. */
public class PneumaticValveBlock extends DirectionalDomainBlock implements EngineeringPortProvider {
    public static final BooleanProperty OPEN = BooleanProperty.create("open");

    public PneumaticValveBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(OPEN, true));
    }

    @Override public MapCodec<PneumaticValveBlock> codec() {
        return RedstoneEngineering.PNEUMATIC_VALVE_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(OPEN);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort(
                        "PNEUMATIC BACK", inputSide(state), EngineeringDomain.PNEUMATIC,
                        PortKind.CONTROL, PortDirection.BIDIRECTIONAL, false, "pressure"
                ),
                new EngineeringPort(
                        "PNEUMATIC FRONT", outputSide(state), EngineeringDomain.PNEUMATIC,
                        PortKind.CONTROL, PortDirection.BIDIRECTIONAL, false, "pressure"
                )
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> descriptor = engineeringPort(state, side);
        if (descriptor.isEmpty()) return Optional.empty();
        return Optional.of(PneumaticValveSupport.snapshot(level, pos, side, descriptor.get()));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        PneumaticValveSupport.placed(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        PneumaticValveSupport.removed(state, level, pos, newState);
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                boolean nextOpen = !state.getValue(OPEN);
                BlockState nextState = state.setValue(OPEN, nextOpen);
                level.setBlock(pos, nextState, Block.UPDATE_CLIENTS);
                if (level instanceof ServerLevel server) PneumaticNetwork.recomputeAround(server, pos);
                player.displayClientMessage(Component.literal(
                        "Pneumatic valve " + (nextOpen ? "OPEN" : "CLOSED") + " | BACK↔FRONT only"
                ), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
