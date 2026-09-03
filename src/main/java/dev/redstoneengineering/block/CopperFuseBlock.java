package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.CircuitPhysics;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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

public class CopperFuseBlock extends DirectionalDomainBlock {
    private static final String KEY = "copper_fuse";
    public static final IntegerProperty RATING=IntegerProperty.create("rating",1,15);
    public static final BooleanProperty TRIPPED=BooleanProperty.create("tripped");
    public CopperFuseBlock(Properties p){super(p);registerDefaultState(defaultBlockState().setValue(RATING,4).setValue(TRIPPED,false));}
    @Override public MapCodec<CopperFuseBlock> codec(){return RedstoneEngineering.COPPER_FUSE_CODEC.value();}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>b){super.createBlockStateDefinition(b);b.add(RATING,TRIPPED);}
    @Override protected void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moved){super.onPlace(s,l,p,old,moved);if(!l.isClientSide)l.scheduleTick(p,this,2);}
    @Override protected void tick(BlockState s,ServerLevel l,BlockPos p,RandomSource r){int vin=DomainNetwork.sampleCopperVoltage(l,inputPos(p,s));double loadR=CircuitPhysics.equivalentLoadResistance(l,outputPos(p,s),128);double current=CircuitPhysics.current(vin,loadR);boolean trip=s.getValue(TRIPPED)||current>s.getValue(RATING);BlockState n=s.setValue(TRIPPED,trip);if(n!=s)l.setBlock(p,n,Block.UPDATE_CLIENTS);int out=trip?0:vin;RuntimeIntStore.get(l,KEY,p,1)[0]=out;DomainNetwork.driveCopper(l,outputPos(p,n),p,out);l.scheduleTick(p,this,2);}
    public static int outputVoltage(Level level, BlockPos pos){return RuntimeIntStore.get(level,KEY,pos,1)[0];}
    @Override protected void onRemove(BlockState s,Level l,BlockPos p,BlockState ns,boolean moved){if(!s.is(ns.getBlock())){if(l instanceof ServerLevel sl)DomainNetwork.driveCopper(sl,outputPos(p,s),p,0);RuntimeIntStore.remove(l,KEY,p);}super.onRemove(s,l,p,ns,moved);}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player pl,BlockHitResult hit){if(!l.isClientSide){BlockState n=s;if(pl.isShiftKeyDown())n=s.setValue(TRIPPED,false);else{int rating=s.getValue(RATING);n=s.setValue(RATING,rating>=15?1:rating+1);}l.setBlock(p,n,Block.UPDATE_CLIENTS);pl.displayClientMessage(Component.literal("Copper fuse | current rating="+n.getValue(RATING)+" | "+(n.getValue(TRIPPED)?"TRIPPED":"armed")+(pl.isShiftKeyDown()?" | reset":"")),true);}return InteractionResult.sidedSuccess(l.isClientSide);}
}
