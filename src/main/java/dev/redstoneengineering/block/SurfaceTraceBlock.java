package dev.redstoneengineering.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Redstone-like floor trace. It auto-connects only in N/E/S/W and never fakes
 * vertical connectivity. Runtime signal values must NOT be stored in BlockState.
 */
public abstract class SurfaceTraceBlock extends DomainBlock {
    public static final BooleanProperty NORTH=BooleanProperty.create("north");
    public static final BooleanProperty EAST=BooleanProperty.create("east");
    public static final BooleanProperty SOUTH=BooleanProperty.create("south");
    public static final BooleanProperty WEST=BooleanProperty.create("west");

    private static final VoxelShape CENTER=Block.box(6,0,6,10,2,10);
    private static final VoxelShape N=Block.box(6,0,0,10,2,6);
    private static final VoxelShape E=Block.box(10,0,6,16,2,10);
    private static final VoxelShape S=Block.box(6,0,10,10,2,16);
    private static final VoxelShape W=Block.box(0,0,6,6,2,10);

    protected SurfaceTraceBlock(Properties p){
        super(p);
        registerDefaultState(stateDefinition.any().setValue(NORTH,false).setValue(EAST,false).setValue(SOUTH,false).setValue(WEST,false));
    }

    protected abstract boolean canConnectTo(BlockGetter level, BlockPos self, Direction direction, BlockState neighbor);

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>b){
        b.add(NORTH,EAST,SOUTH,WEST);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext c){
        return withConnections(defaultBlockState(),c.getLevel(),c.getClickedPos());
    }

    protected final BlockState withConnections(BlockState s, BlockGetter level, BlockPos pos){
        return s.setValue(NORTH,connect(level,pos,Direction.NORTH))
                .setValue(EAST,connect(level,pos,Direction.EAST))
                .setValue(SOUTH,connect(level,pos,Direction.SOUTH))
                .setValue(WEST,connect(level,pos,Direction.WEST));
    }

    private boolean connect(BlockGetter level,BlockPos pos,Direction d){
        return canConnectTo(level,pos,d,level.getBlockState(pos.relative(d)));
    }

    protected final void refreshConnections(Level level,BlockPos pos,BlockState state){
        BlockState next=withConnections(state,level,pos);
        if(next!=state) level.setBlock(pos,next,Block.UPDATE_CLIENTS);
    }

    public static boolean connected(BlockState s,Direction d){
        return switch(d){
            case NORTH->s.getValue(NORTH);case EAST->s.getValue(EAST);case SOUTH->s.getValue(SOUTH);case WEST->s.getValue(WEST);default->false;
        };
    }

    @Override protected void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moved){
        super.onPlace(s,l,p,old,moved);refreshConnections(l,p,s);
    }

    @Override protected void neighborChanged(BlockState s,Level l,BlockPos p,Block nb,BlockPos np,boolean moved){
        refreshConnections(l,p,s);
    }

    @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){
        VoxelShape shape=CENTER;
        if(s.getValue(NORTH))shape=Shapes.joinUnoptimized(shape,N,BooleanOp.OR);
        if(s.getValue(EAST))shape=Shapes.joinUnoptimized(shape,E,BooleanOp.OR);
        if(s.getValue(SOUTH))shape=Shapes.joinUnoptimized(shape,S,BooleanOp.OR);
        if(s.getValue(WEST))shape=Shapes.joinUnoptimized(shape,W,BooleanOp.OR);
        return shape;
    }
}
