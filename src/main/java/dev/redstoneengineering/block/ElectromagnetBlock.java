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
import dev.redstoneengineering.physics.CopperNetworkSupport;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
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

/** Copper-powered magnetic actuator. INPUT-only Copper neighbors can never back-drive its coil. */
public class ElectromagnetBlock extends DomainBlock implements EngineeringPortProvider {
    public static final IntegerProperty FIELD = IntegerProperty.create("field", 0, 15);

    public ElectromagnetBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FIELD, 0));
    }

    @Override public MapCodec<ElectromagnetBlock> codec() { return RedstoneEngineering.ELECTROMAGNET_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FIELD); }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return Arrays.stream(Direction.values()).map(side ->
                new EngineeringPort("COPPER COIL INPUT", side, EngineeringDomain.COPPER,
                        PortKind.ACTUATOR, PortDirection.INPUT, false, "voltage")).toList();
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        CopperNetworkSupport.TerminalInput input = CopperNetworkSupport.terminalInputOnSide(level, pos, side);
        return Optional.of(new EngineeringPortSnapshot(
                port.get(), input.voltage(), 0.0, 15.0, input.quality()));
    }

    public static CopperNetworkSupport.TerminalInput input(Level level, BlockPos pos) {
        return CopperNetworkSupport.terminalInput(level, pos);
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean moved) {
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        CopperNetworkSupport.TerminalInput input = CopperNetworkSupport.terminalInput(level, pos);
        int field = input.quality() == PortQuality.VALID ? input.voltage() : 0;
        if (field != state.getValue(FIELD)) level.setBlock(pos, state.setValue(FIELD, field), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState nextState, boolean moved) {
        if (!state.is(nextState.getBlock()) && level instanceof ServerLevel serverLevel) {
            for (Direction direction : Direction.values()) DomainNetwork.recomputeCopper(serverLevel, pos.relative(direction));
        }
        super.onRemove(state, level, pos, nextState, moved);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!player.isShiftKeyDown()) {
                FieldDeviceUi.open(serverPlayer, pos);
                return InteractionResult.CONSUME;
            }
            CopperNetworkSupport.TerminalInput input = CopperNetworkSupport.terminalInput(level, pos);
            player.displayClientMessage(Component.literal(
                    "Electromagnet | B-level=" + state.getValue(FIELD) + "/15"
                            + " | copper feeds=" + input.connectedFeeds()
                            + " | V=" + input.voltage() + "/15"
                            + " | " + input.quality()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
