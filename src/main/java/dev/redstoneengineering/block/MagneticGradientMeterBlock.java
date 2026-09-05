package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.physics.MagneticPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

public class MagneticGradientMeterBlock extends DomainBlock implements EngineeringPortProvider {
    public MagneticGradientMeterBlock(Properties p){super(p);}
    @Override public MapCodec<MagneticGradientMeterBlock> codec(){return RedstoneEngineering.MAGNETIC_GRADIENT_METER_CODEC.value();}
    /** Differential free-space measurement; no wired endpoint. */
    @Override public List<EngineeringPort> engineeringPorts(BlockState s){return List.of();}
    public static int gradientX(Level l,BlockPos p){return MagneticPhysics.fieldAt(l,p.east(),6)-MagneticPhysics.fieldAt(l,p.west(),6);}
    public static int gradientY(Level l,BlockPos p){return MagneticPhysics.fieldAt(l,p.above(),6)-MagneticPhysics.fieldAt(l,p.below(),6);}
    public static int gradientZ(Level l,BlockPos p){return MagneticPhysics.fieldAt(l,p.south(),6)-MagneticPhysics.fieldAt(l,p.north(),6);}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player pl,BlockHitResult hit){if(!l.isClientSide&&pl instanceof ServerPlayer sp){if(!pl.isShiftKeyDown()){FieldDeviceUi.open(sp,p);return InteractionResult.CONSUME;}pl.displayClientMessage(Component.literal("Magnetic gradient meter | ΔBx="+gradientX(l,p)+" | ΔBy="+gradientY(l,p)+" | ΔBz="+gradientZ(l,p)+" | local B="+MagneticPhysics.fieldAt(l,p,6)),true);}return InteractionResult.sidedSuccess(l.isClientSide);}
}
