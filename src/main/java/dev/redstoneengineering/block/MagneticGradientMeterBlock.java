package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.MagneticPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class MagneticGradientMeterBlock extends DomainBlock {
    public MagneticGradientMeterBlock(Properties p){super(p);}
    @Override public MapCodec<MagneticGradientMeterBlock> codec(){return RedstoneEngineering.MAGNETIC_GRADIENT_METER_CODEC.value();}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player pl,BlockHitResult hit){if(!l.isClientSide){int xp=MagneticPhysics.fieldAt(l,p.east(),6),xm=MagneticPhysics.fieldAt(l,p.west(),6),yp=MagneticPhysics.fieldAt(l,p.above(),6),ym=MagneticPhysics.fieldAt(l,p.below(),6),zp=MagneticPhysics.fieldAt(l,p.south(),6),zm=MagneticPhysics.fieldAt(l,p.north(),6);pl.displayClientMessage(Component.literal("Magnetic gradient meter | ΔBx="+(xp-xm)+" | ΔBy="+(yp-ym)+" | ΔBz="+(zp-zm)+" | local B="+MagneticPhysics.fieldAt(l,p,6)),true);}return InteractionResult.sidedSuccess(l.isClientSide);}
}
