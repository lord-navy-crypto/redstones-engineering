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
import dev.redstoneengineering.physics.EngineeringMath;
import dev.redstoneengineering.physics.MagneticPhysics;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

public class InductionCoilBlock extends DirectionalDomainBlock implements EngineeringPortProvider {
    public static final IntegerProperty TURNS = IntegerProperty.create("turns", 1, 4);
    private static final String KEY = "induction_coil";

    public InductionCoilBlock(Properties p) { super(p); registerDefaultState(defaultBlockState().setValue(TURNS, 2)); }
    @Override public MapCodec<InductionCoilBlock> codec() { return RedstoneEngineering.INDUCTION_COIL_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { super.createBlockStateDefinition(b); b.add(TURNS); }

    @Override public List<EngineeringPort> engineeringPorts(BlockState s){return List.of(
            new EngineeringPort("MAGNETIC SENSE",inputSide(s), EngineeringDomain.IRON_MAGNETIC, PortKind.MEASUREMENT, PortDirection.INPUT,false,"field"),
            new EngineeringPort("INDUCED COPPER OUT",outputSide(s), EngineeringDomain.COPPER, PortKind.CONVERTER, PortDirection.OUTPUT,false,"voltage"));}
    @Override public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level l,BlockPos p,BlockState s,Direction side){Optional<EngineeringPort> d=engineeringPort(s,side);if(d.isEmpty())return Optional.empty();if(side==inputSide(s)){int field=MagneticPhysics.fieldAt(l,p,6);return Optional.of(new EngineeringPortSnapshot(d.get(),field,0.0,15.0,field>0?PortQuality.VALID:PortQuality.NO_SIGNAL));}return Optional.of(new EngineeringPortSnapshot(d.get(),outputVoltage(l,p),0.0,15.0,PortQuality.VALID));}

    @Override protected void onPlace(BlockState s, Level l, BlockPos p, BlockState old, boolean moved) {
        super.onPlace(s, l, p, old, moved);
        if (!l.isClientSide) {
            RuntimeIntStore.get(l, KEY, p, 2)[0] = MagneticPhysics.fieldAt(l, p, 6);
            l.scheduleTick(p, this, 2);
        }
    }

    @Override protected void onRemove(BlockState s, Level l, BlockPos p, BlockState ns, boolean moved) {
        if (!s.is(ns.getBlock())) {
            if (l instanceof ServerLevel sl) DomainNetwork.driveCopper(sl, outputPos(p,s), p, 0);
            RuntimeIntStore.remove(l, KEY, p);
        }
        super.onRemove(s, l, p, ns, moved);
    }

    @Override protected void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource r) {
        int[] rt = RuntimeIntStore.get(l, KEY, p, 2); // previous flux, emf
        int flux = MagneticPhysics.fieldAt(l, p, 6);
        int delta = Math.abs(flux - rt[0]);
        rt[0] = flux;
        rt[1] = EngineeringMath.clamp(delta * s.getValue(TURNS), 0, 15);
        DomainNetwork.driveCopper(l, outputPos(p, s), p, rt[1]);
        l.scheduleTick(p, this, 2);
    }

    public static int outputVoltage(Level level, BlockPos pos) {
        int[] runtime=RuntimeIntStore.peek(level,KEY,pos);
        return runtime==null||runtime.length<2?0:runtime[1];
    }

    @Override protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player pl, BlockHitResult hit) {
        if (!l.isClientSide && pl instanceof ServerPlayer sp) {
            if(!pl.isShiftKeyDown()){FieldDeviceUi.open(sp,p);return InteractionResult.CONSUME;}
            int t = s.getValue(TURNS); t = t >= 4 ? 1 : t + 1;
            BlockState n = s.setValue(TURNS, t); l.setBlock(p, n, Block.UPDATE_CLIENTS);
            int emf = RuntimeIntStore.get(l, KEY, p, 2)[1];
            pl.displayClientMessage(Component.literal("Induction coil | turns-index=" + t + " | |emf| ∝ N·|ΔΦ/Δt| | current emf=" + emf + "/15"), true);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
