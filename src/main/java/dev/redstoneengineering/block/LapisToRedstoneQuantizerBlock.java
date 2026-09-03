package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.DomainNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Explicit Lapis continuous-like -> vanilla Redstone 0..15 quantizer. */
public class LapisToRedstoneQuantizerBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty POWER = IntegerProperty.create("power", 0, 15);

    public LapisToRedstoneQuantizerBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWER, 0));
    }

    @Override public MapCodec<LapisToRedstoneQuantizerBlock> codec() { return RedstoneEngineering.LAPIS_TO_REDSTONE_QUANTIZER_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, POWER); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite()); }
    private Direction outputSide(BlockState s) { return s.getValue(FACING); }
    private Direction inputSide(BlockState s) { return outputSide(s).getOpposite(); }
    @Override public boolean canConnectRedstone(BlockState s, BlockGetter l, BlockPos p, @Nullable Direction d) { return d != null && d == outputSide(s).getOpposite(); }
    @Override protected boolean isSignalSource(BlockState s) { return true; }
    @Override protected int getSignal(BlockState s, BlockGetter l, BlockPos p, Direction d) { return d == outputSide(s).getOpposite() ? s.getValue(POWER) : 0; }
    @Override protected void onPlace(BlockState s, Level l, BlockPos p, BlockState old, boolean moved) { super.onPlace(s,l,p,old,moved); if(!l.isClientSide) l.scheduleTick(p,this,1); }
    @Override protected void neighborChanged(BlockState s, Level l, BlockPos p, Block nb, BlockPos np, boolean moved) { if(!l.isClientSide) l.scheduleTick(p,this,1); }
    @Override protected void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource r) {
        var sample = DomainNetwork.sampleLapis(l, p.relative(inputSide(s)));
        int power = sample.valid() ? Math.round(sample.value() * 15.0f / 100.0f) : 0;
        if(power != s.getValue(POWER)) {
            l.setBlock(p, s.setValue(POWER, power), Block.UPDATE_CLIENTS);
            l.updateNeighborsAt(p, this);
            l.updateNeighborsAt(p.relative(outputSide(s)), this);
        }
        l.scheduleTick(p,this,2);
    }
    @Override protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player pl, BlockHitResult h) {
        if(!l.isClientSide) {
            var sample=DomainNetwork.sampleLapis(l,p.relative(inputSide(s)));
            pl.displayClientMessage(Component.literal("Lapis → Redstone Quantizer | input="+(sample.valid()?String.format("%.2f",sample.value()/100.0):"INVALID")+" | output="+s.getValue(POWER)+"/15"),true);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
