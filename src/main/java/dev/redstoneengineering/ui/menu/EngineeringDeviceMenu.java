package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.PneumaticReliefValveBlock;
import dev.redstoneengineering.block.ServoActuatorBlock;
import dev.redstoneengineering.block.WatchdogBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Shared no-inventory menu base for RSE engineering instruments and controllers. */
public abstract class EngineeringDeviceMenu extends AbstractContainerMenu {
    public static final int HEALTH_NOMINAL = 0;
    public static final int HEALTH_ACTIVE = 1;
    public static final int HEALTH_PROTECTIVE = 2;
    public static final int HEALTH_DEGRADED = 3;
    public static final int HEALTH_FAULT = 4;

    protected final Inventory playerInventory;
    protected final Level level;
    protected final BlockPos blockPos;
    private final Block expectedBlock;
    private final DataSlot operationalHealth;

    protected EngineeringDeviceMenu(
            MenuType<?> type,
            int containerId,
            Inventory playerInventory,
            BlockPos blockPos,
            Block expectedBlock
    ) {
        super(type, containerId);
        this.playerInventory = playerInventory;
        this.level = playerInventory.player.level();
        this.blockPos = blockPos;
        this.expectedBlock = expectedBlock;
        this.operationalHealth = trackedInt();
    }

    protected DataSlot trackedInt() {
        DataSlot slot = DataSlot.standalone();
        addDataSlot(slot);
        return slot;
    }

    /** Allocate a compact fixed-size synchronized integer vector for bounded engineering telemetry. */
    protected DataSlot[] trackedInts(int count) {
        int bounded = Math.max(0, count);
        DataSlot[] slots = new DataSlot[bounded];
        for (int i = 0; i < bounded; i++) slots[i] = trackedInt();
        return slots;
    }

    public BlockPos blockPos() {
        return blockPos;
    }

    /**
     * Operational/safety state is intentionally independent from evidence validity.
     * A watchdog timeout, relief event, or commanded brake can be authoritative data
     * while the device is simultaneously in a protective operating state.
     */
    public int operationalHealth() {
        return operationalHealth.get();
    }

    public String operationalHealthLabel() {
        return switch (operationalHealth()) {
            case HEALTH_ACTIVE -> "ACTIVE";
            case HEALTH_PROTECTIVE -> "PROTECTIVE";
            case HEALTH_DEGRADED -> "DEGRADED";
            case HEALTH_FAULT -> "FAULT";
            default -> "NOMINAL";
        };
    }

    @Override
    public boolean stillValid(Player player) {
        if (!player.level().getBlockState(blockPos).is(expectedBlock)) return false;
        double dx = player.getX() - (blockPos.getX() + 0.5D);
        double dy = player.getY() - (blockPos.getY() + 0.5D);
        double dz = player.getZ() - (blockPos.getZ() + 0.5D);
        return dx * dx + dy * dy + dz * dz <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public void broadcastChanges() {
        if (!level.isClientSide) {
            refreshAuthoritativeSnapshot();
            refreshOperationalHealth();
        }
        super.broadcastChanges();
    }

    private void refreshOperationalHealth() {
        BlockState state = level.getBlockState(blockPos);
        Block block = state.getBlock();
        int health = HEALTH_NOMINAL;

        if (block instanceof PneumaticReliefValveBlock) {
            health = PneumaticReliefValveBlock.venting(level, blockPos)
                    ? HEALTH_PROTECTIVE : HEALTH_NOMINAL;
        } else if (block instanceof WatchdogBlock) {
            health = state.getValue(DirectionalSignalBlock.OUTPUT) > 0
                    ? HEALTH_PROTECTIVE : HEALTH_NOMINAL;
        } else if (block instanceof ServoActuatorBlock) {
            health = ServoActuatorBlock.braking(level, blockPos)
                    ? HEALTH_PROTECTIVE : HEALTH_ACTIVE;
        }

        operationalHealth.set(health);
    }

    protected abstract void refreshAuthoritativeSnapshot();
}
