package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
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
import net.minecraft.world.phys.BlockHitResult;

public class IronCoreBlock extends DomainBlock {
    public static final BooleanProperty MAGNETIZED=BooleanProperty.create("magnetized");
    public IronCoreBlock(Properties p){super(p);registerDefaultState(defaultBlockState().setValue(MAGNETIZED,false));}
    @Override public MapCodec<IronCoreBlock> codec(){return RedstoneEngineering.IRON_CORE_CODEC.value();}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>b){b.add(MAGNETIZED);}
    @Override protected void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moved){super.onPlace(s,l,p,old,moved);if(!l.isClientSide)l.scheduleTick(p,this,5);}
    @Override protected void tick(BlockState s,ServerLevel l,BlockPos p,RandomSource r){boolean strong=false;for(var d:net.minecraft.core.Direction.values()){var n=l.getBlockState(p.relative(d));if(n.getBlock() instanceof ElectromagnetBlock && n.getValue(ElectromagnetBlock.FIELD)>=8){strong=true;break;}}if(strong&&!s.getValue(MAGNETIZED))l.setBlock(p,s.setValue(MAGNETIZED,true),Block.UPDATE_CLIENTS);l.scheduleTick(p,this,5);}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player pl,BlockHitResult hit){if(!l.isClientSide){BlockState n=s;if(pl.isShiftKeyDown()&&s.getValue(MAGNETIZED)){n=s.setValue(MAGNETIZED,false);l.setBlock(p,n,Block.UPDATE_CLIENTS);}pl.displayClientMessage(Component.literal("Iron core | "+(n.getValue(MAGNETIZED)?"magnetized field source":"soft magnetic core")+(pl.isShiftKeyDown()?" | demagnetize":"")),true);}return InteractionResult.sidedSuccess(l.isClientSide);}
}
