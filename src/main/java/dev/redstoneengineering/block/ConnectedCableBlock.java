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
 * Six-direction 3-D cable topology. Live voltage/light/signal values belong in
 * runtime storage, leaving only 2^6 = 64 cheap topology states.
 */
public abstract class ConnectedCableBlock extends DomainBlock {
    public static final BooleanProperty NORTH=BooleanProperty.create("north");
    public static final BooleanProperty EAST=BooleanProperty.create("east");
    public static final BooleanProperty SOUTH=BooleanProperty.create("south");
    public static final BooleanProperty WEST=BooleanProperty.create("west");
    public static final BooleanProperty UP=BooleanProperty.create("up");
    public static final BooleanProperty DOWN=BooleanProperty.create("down");

    private static final VoxelShape C=Block.box(6,6,6,10,10,10);
    private static final VoxelShape N=Block.box(6,6,0,10,10,6),E=Block.box(10,6,6,16,10,10),S=Block.box(6,6,10,10,10,16),W=Block.box(0,6,6,6,10,10),U=Block.box(6,10,6,10,16,10),D=Block.box(6,0,6,10,6,10);

    protected ConnectedCableBlock(Properties p){
        super(p);
        registerDefaultState(stateDefinition.any().setValue(NORTH,false).setValue(EAST,false).setValue(SOUTH,false).setValue(WEST,false).setValue(UP,false).setValue(DOWN,false));
    }

    protected abstract boolean canConnectTo(BlockGetter level,BlockPos self,Direction direction,BlockState neighbor);
    /** Plain cable has two ends. Branches should use a Junction/Splitter. */
    protected int maxConnections(){return 2;}

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>b){b.add(NORTH,EAST,SOUTH,WEST,UP,DOWN);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){return withConnections(defaultBlockState(),c.getLevel(),c.getClickedPos());}

    protected final BlockState withConnections(BlockState s,BlockGetter level,BlockPos pos){
        for(Direction d:Direction.values())s=s.setValue(prop(d),canConnectTo(level,pos,d,level.getBlockState(pos.relative(d))));
        return s;
    }
    protected final void refreshConnections(Level level,BlockPos pos,BlockState state){BlockState n=withConnections(state,level,pos);if(n!=state)level.setBlock(pos,n,Block.UPDATE_CLIENTS);}
    public final boolean topologyValid(BlockState s){return connectionCount(s)<=maxConnections();}
    public static int connectionCount(BlockState s){int n=0;for(Direction d:Direction.values())if(connected(s,d))n++;return n;}
    public static boolean connected(BlockState s,Direction d){return s.hasProperty(prop(d))&&s.getValue(prop(d));}
    private static BooleanProperty prop(Direction d){return switch(d){case NORTH->NORTH;case EAST->EAST;case SOUTH->SOUTH;case WEST->WEST;case UP->UP;case DOWN->DOWN;};}

    @Override protected void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moved){super.onPlace(s,l,p,old,moved);refreshConnections(l,p,s);}
    @Override protected void neighborChanged(BlockState s,Level l,BlockPos p,Block nb,BlockPos np,boolean moved){refreshConnections(l,p,s);}

    @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){
        VoxelShape sh=C;
        if(s.getValue(NORTH))sh=Shapes.joinUnoptimized(sh,N,BooleanOp.OR);if(s.getValue(EAST))sh=Shapes.joinUnoptimized(sh,E,BooleanOp.OR);if(s.getValue(SOUTH))sh=Shapes.joinUnoptimized(sh,S,BooleanOp.OR);if(s.getValue(WEST))sh=Shapes.joinUnoptimized(sh,W,BooleanOp.OR);if(s.getValue(UP))sh=Shapes.joinUnoptimized(sh,U,BooleanOp.OR);if(s.getValue(DOWN))sh=Shapes.joinUnoptimized(sh,D,BooleanOp.OR);
        return sh;
    }
}
