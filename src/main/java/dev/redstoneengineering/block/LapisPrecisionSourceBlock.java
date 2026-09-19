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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/** Precision Lapis source with one configurable horizontal output face. */
public class LapisPrecisionSourceBlock extends DirectionalDomainSourceBlock implements EngineeringPortProvider {
    public static final IntegerProperty VALUE = IntegerProperty.create("value",0,100);

    public LapisPrecisionSourceBlock(Properties p){
        super(p);
        registerDefaultState(defaultBlockState().setValue(VALUE,50));
    }

    @Override public MapCodec<LapisPrecisionSourceBlock> codec(){ return RedstoneEngineering.LAPIS_PRECISION_SOURCE_CODEC.value(); }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){
        super.createBlockStateDefinition(b);
        b.add(VALUE);
    }

    private static EngineeringPort port(Direction side){
        return new EngineeringPort("LAPIS PRECISION OUT",side, EngineeringDomain.LAPIS, PortKind.BUS, PortDirection.OUTPUT,false,"precision");
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState s){
        return List.of(port(outputSide(s)));
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level l,BlockPos p,BlockState s,Direction side){
        Optional<EngineeringPort>d=engineeringPort(s,side);
        return d.map(port->new EngineeringPortSnapshot(port,s.getValue(VALUE),0.0,100.0,PortQuality.VALID));
    }

    @Override
    protected void onPlace(BlockState s, Level l, BlockPos p, BlockState old, boolean moved){
        super.onPlace(s,l,p,old,moved);
        if(l instanceof ServerLevel sl) DomainNetwork.recomputeLapis(sl,p);
    }

    @Override
    protected void onRemove(BlockState s, Level l, BlockPos p, BlockState ns, boolean moved){
        if(l instanceof ServerLevel sl && !s.is(ns.getBlock())) DomainNetwork.recomputeLapisAround(sl,p);
        super.onRemove(s,l,p,ns,moved);
    }

    public static boolean stepValue(Level level, BlockPos pos, int delta) {
        if (level.isClientSide || delta == 0) return false;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof LapisPrecisionSourceBlock source)) return false;
        int current = state.getValue(VALUE);
        int next = Math.max(0, Math.min(100, current + Integer.signum(delta)));
        if (next == current) return false;
        BlockState updated = state.setValue(VALUE, next);
        level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
        if (level instanceof ServerLevel serverLevel) DomainNetwork.recomputeLapis(serverLevel, pos);
        return true;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player pl, BlockHitResult hit){
        if(!l.isClientSide && pl instanceof ServerPlayer serverPlayer){
            if(!pl.isShiftKeyDown()){
                FieldDeviceUi.open(serverPlayer,p);
                return InteractionResult.CONSUME;
            }

            if (hit.getDirection().getAxis().isHorizontal()) {
                if (rotateOutput(l, p, true) && l instanceof ServerLevel sl) {
                    DomainNetwork.recomputeLapisAround(sl, p);
                    BlockState next = l.getBlockState(p);
                    pl.displayClientMessage(Component.literal(
                            "Lapis precision source OUT=" + outputSide(next).getName().toUpperCase()
                                    + " | shift-click UP/DOWN adjusts value by 0.01"), true);
                }
            } else {
                int delta = hit.getDirection() == Direction.DOWN ? -1 : 1;
                stepValue(l, p, delta);
                BlockState n = l.getBlockState(p);
                int v = n.getValue(VALUE);
                pl.displayClientMessage(Component.literal(
                        "Lapis precision source = " + String.format("%.2f", v / 100.0)
                                + " | fine step=0.01"
                                + " | OUT=" + outputSide(n).getName().toUpperCase()), true);
            }
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
