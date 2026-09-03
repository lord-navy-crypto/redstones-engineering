package dev.redstoneengineering.blockentity;

import dev.redstoneengineering.visualization.MechatronicsVisualState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * GeckoLib-facing mirror of authoritative mechatronics simulation state.
 *
 * This block entity NEVER computes physics. Server-side actuator blocks push an
 * immutable MechatronicsVisualState after their physics step; the client only
 * receives that mirror for rendering. No renderer API is exposed back to the
 * simulation layer.
 */
public final class MechatronicsVisualBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    private double position01;
    private double velocitySigned;
    private boolean braked;
    private double opening01;
    private double pressure01;

    public MechatronicsVisualBlockEntity(BlockPos pos, BlockState state) {
        super(MechatronicsBlockEntityRegistry.type(), pos, state);
    }

    public static void push(Level level, BlockPos pos, MechatronicsVisualState state) {
        if (level.isClientSide) return;
        if (level.getBlockEntity(pos) instanceof MechatronicsVisualBlockEntity visual) {
            visual.acceptAuthoritativeSnapshot(state);
        }
    }

    private void acceptAuthoritativeSnapshot(MechatronicsVisualState next) {
        if (same(next)) return;
        position01 = next.position01();
        velocitySigned = next.velocitySigned();
        braked = next.braked();
        opening01 = next.opening01();
        pressure01 = next.pressure01();
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private boolean same(MechatronicsVisualState next) {
        return Double.compare(position01, next.position01()) == 0
                && Double.compare(velocitySigned, next.velocitySigned()) == 0
                && braked == next.braked()
                && Double.compare(opening01, next.opening01()) == 0
                && Double.compare(pressure01, next.pressure01()) == 0;
    }

    public MechatronicsVisualState visualState() {
        return new MechatronicsVisualState(position01, velocitySigned, braked, opening01, pressure01);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Custom bone transforms are driven directly by read-only simulation snapshots.
        // No animation controller is permitted to write simulation state.
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble("visual_position", position01);
        tag.putDouble("visual_velocity", velocitySigned);
        tag.putBoolean("visual_braked", braked);
        tag.putDouble("visual_opening", opening01);
        tag.putDouble("visual_pressure", pressure01);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        position01 = clamp01(tag.getDouble("visual_position"));
        velocitySigned = Math.max(-1.0, Math.min(1.0, tag.getDouble("visual_velocity")));
        braked = tag.getBoolean("visual_braked");
        opening01 = clamp01(tag.getDouble("visual_opening"));
        pressure01 = clamp01(tag.getDouble("visual_pressure"));
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
