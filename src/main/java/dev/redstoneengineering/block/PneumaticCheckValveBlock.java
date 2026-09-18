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
import dev.redstoneengineering.signal.PneumaticCheckValveLogic;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/**
 * One-way pneumatic element with a finite cracking pressure.
 *
 * <p>Flow is permitted BACK -> FRONT only. Forward pressure must first overcome the poppet/spring
 * cracking pressure; the pressure solver then carries the resulting extra drop downstream.</p>
 */
public class PneumaticCheckValveBlock extends DirectionalDomainBlock implements EngineeringPortProvider {
    private static final int CRACKING_PRESSURE = 4;

    public PneumaticCheckValveBlock(Properties properties) { super(properties); }

    public static int crackingPressure() { return CRACKING_PRESSURE; }

    public static int transmittedPressure(int upstreamPressure) {
        return PneumaticCheckValveLogic.transmittedPressure(upstreamPressure, CRACKING_PRESSURE);
    }

    public static boolean crackedOpen(int upstreamPressure) {
        return PneumaticCheckValveLogic.crackedOpen(upstreamPressure, CRACKING_PRESSURE);
    }

    @Override public MapCodec<PneumaticCheckValveBlock> codec() {
        return RedstoneEngineering.PNEUMATIC_CHECK_VALVE_CODEC.value();
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort(
                        "PNEUMATIC IN", inputSide(state), EngineeringDomain.PNEUMATIC,
                        PortKind.SAFETY, PortDirection.INPUT, false, "pressure"
                ),
                new EngineeringPort(
                        "PNEUMATIC OUT", outputSide(state), EngineeringDomain.PNEUMATIC,
                        PortKind.SAFETY, PortDirection.OUTPUT, false, "pressure"
                )
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> descriptor = engineeringPort(state, side);
        if (descriptor.isEmpty()) return Optional.empty();
        PneumaticObservationSupport.Observation observation =
                PneumaticObservationSupport.observe(level, pos.relative(side));
        return Optional.of(new EngineeringPortSnapshot(
                descriptor.get(), observation.pressure(), 0.0, 100.0, observation.quality()
        ));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (level instanceof ServerLevel server) PneumaticNetwork.recomputeAround(server, pos);
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
                int upstreamPressure = PneumaticObservationSupport
                        .observe(level, pos.relative(inputSide(state))).pressure();
                player.displayClientMessage(Component.literal(
                        "Check valve | allowed " + inputSide(state) + " → " + outputSide(state)
                                + " | cracking=" + CRACKING_PRESSURE + "/100"
                                + " | upstream=" + upstreamPressure + "/100"
                                + " valve=" + PneumaticNetwork.pressure(level, pos) + "/100"
                                + " | state=" + (crackedOpen(upstreamPressure)
                                ? "CRACKED OPEN" : "SEATED")
                ), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
