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
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.RuntimeIntStore;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/** Quartz clock source with one configurable horizontal output face. */
public class QuartzOscillatorBlock extends DirectionalDomainSourceBlock implements EngineeringPortProvider {
    public static final BooleanProperty ACTIVE=BooleanProperty.create("active");
    public static final IntegerProperty PERIOD_INDEX=IntegerProperty.create("period",0,4);
    private static final String KEY = "quartz_oscillator";
    private static final int EFFECTIVE_PERIOD_INDEX = 0;
    private static final int INITIALIZED = 1;
    private static final int EDGE_COUNT = 2;
    private static final int RUNTIME_SIZE = 3;

    public QuartzOscillatorBlock(Properties p){
        super(p);
        registerDefaultState(defaultBlockState().setValue(ACTIVE,false).setValue(PERIOD_INDEX,2));
    }

    @Override public MapCodec<QuartzOscillatorBlock> codec(){return RedstoneEngineering.QUARTZ_OSCILLATOR_CODEC.value();}

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>b){
        super.createBlockStateDefinition(b);
        b.add(ACTIVE,PERIOD_INDEX);
    }

    private static EngineeringPort port(Direction side){
        return new EngineeringPort("QUARTZ CLOCK OUT",side, EngineeringDomain.QUARTZ, PortKind.TRIGGER, PortDirection.OUTPUT,false,"clock");
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState s){
        return List.of(port(outputSide(s)));
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level l,BlockPos p,BlockState s,Direction side){
        Optional<EngineeringPort>d=engineeringPort(s,side);
        return d.map(port->new EngineeringPortSnapshot(port,s.getValue(ACTIVE)?1.0:0.0,0.0,1.0,PortQuality.VALID));
    }

    private static int[] snapshot(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime != null && runtime.length == RUNTIME_SIZE ? runtime : null;
    }

    public static int configuredPeriodTicks(BlockState state) {
        return QuartzTimingLineBlock.periodTicks(state.getValue(PERIOD_INDEX));
    }

    public static int effectivePeriodTicks(Level level, BlockPos pos, BlockState state) {
        int[] runtime = snapshot(level, pos);
        int index = runtime == null || runtime[INITIALIZED] == 0
                ? state.getValue(PERIOD_INDEX)
                : runtime[EFFECTIVE_PERIOD_INDEX];
        return QuartzTimingLineBlock.periodTicks(index);
    }

    public static boolean periodChangePending(Level level, BlockPos pos, BlockState state) {
        int[] runtime = snapshot(level, pos);
        return runtime != null && runtime[INITIALIZED] != 0
                && runtime[EFFECTIVE_PERIOD_INDEX] != state.getValue(PERIOD_INDEX);
    }

    public static int edgeCount(Level level, BlockPos pos) {
        int[] runtime = snapshot(level, pos);
        return runtime == null ? 0 : Math.max(0, runtime[EDGE_COUNT]);
    }

    @Override
    protected void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moved){
        super.onPlace(s,l,p,old,moved);
        if(!l.isClientSide&&!old.is(s.getBlock()))l.scheduleTick(p,this,1);
    }

    @Override
    protected void onRemove(BlockState s,Level l,BlockPos p,BlockState ns,boolean moved){
        if(!s.is(ns.getBlock())) {
            RuntimeIntStore.remove(l, KEY, p);
            if(l instanceof ServerLevel sl) DomainNetwork.recomputeQuartzAround(sl,p);
        }
        super.onRemove(s,l,p,ns,moved);
    }

    @Override
    protected void tick(BlockState s,ServerLevel l,BlockPos p,RandomSource r){
        int[] runtime = RuntimeIntStore.get(l, KEY, p, RUNTIME_SIZE);
        if (runtime[INITIALIZED] == 0) {
            runtime[EFFECTIVE_PERIOD_INDEX] = s.getValue(PERIOD_INDEX);
            runtime[INITIALIZED] = 1;
        }

        BlockState n=s.setValue(ACTIVE,!s.getValue(ACTIVE));
        l.setBlock(p,n,Block.UPDATE_CLIENTS);

        // A configured period change becomes effective only at this real waveform transition.
        runtime[EFFECTIVE_PERIOD_INDEX] = n.getValue(PERIOD_INDEX);
        if (runtime[EDGE_COUNT] < Integer.MAX_VALUE) runtime[EDGE_COUNT]++;

        DomainNetwork.recomputeQuartz(l,p);
        int effectivePeriod = effectivePeriodTicks(l, p, n);
        l.scheduleTick(p,this,Math.max(1,effectivePeriod/2));
    }

    public static boolean stepPeriod(Level level, BlockPos pos, boolean forward) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof QuartzOscillatorBlock oscillator)) return false;
        int current = state.getValue(PERIOD_INDEX);
        int next = Math.floorMod(current + (forward ? 1 : -1), 5);
        BlockState updated = state.setValue(PERIOD_INDEX, next);
        level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
        // Do not create an early edge. The new period is latched by the next real oscillator
        // transition; until then the timing network keeps the old effective-period evidence.
        return true;
    }

    public static int periodTicks(BlockState state) {
        return configuredPeriodTicks(state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player pl,BlockHitResult hit){
        if(!l.isClientSide&&pl instanceof ServerPlayer serverPlayer){
            if(!pl.isShiftKeyDown()){
                FieldDeviceUi.open(serverPlayer,p);
                return InteractionResult.CONSUME;
            }

            if (hit.getDirection().getAxis().isHorizontal()) {
                if (rotateOutput(l, p, true) && l instanceof ServerLevel sl) {
                    DomainNetwork.recomputeQuartzAround(sl, p);
                    BlockState next = l.getBlockState(p);
                    pl.displayClientMessage(Component.literal(
                            "Quartz oscillator OUT=" + outputSide(next).getName().toUpperCase()
                                    + " | shift-click UP/DOWN changes period"), true);
                }
            } else {
                stepPeriod(l, p, true);
                BlockState n=l.getBlockState(p);
                pl.displayClientMessage(Component.literal(
                        "Quartz oscillator | configured=" + configuredPeriodTicks(n) + "t"
                                + " effective=" + effectivePeriodTicks(l, p, n) + "t"
                                + (periodChangePending(l, p, n) ? " (LATCHES NEXT EDGE)" : "")
                                + " | edges=" + edgeCount(l, p)
                                + " | OUT=" + outputSide(n).getName().toUpperCase()),true);
            }
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
