package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.EngineeringSystemsModule;
import dev.redstoneengineering.block.IndustrialBufferBlock;
import dev.redstoneengineering.operations.OperationBufferLot;
import dev.redstoneengineering.operations.OperationBufferSnapshot;
import dev.redstoneengineering.operations.world.OperationPlantSavedData;
import dev.redstoneengineering.operations.world.OperationWorkcellBufferBinding;
import dev.redstoneengineering.ui.EngineeringUiRegistration;
import dev.redstoneengineering.ui.IndustrialBufferUi;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.DataSlot;

import java.util.ArrayList;
import java.util.List;

/** Read-only synchronized Industrial Buffer console with exact bounded lot identity snapshot. */
public final class IndustrialBufferMenu extends EngineeringDeviceMenu {
    public record LotView(long outputId, long jobId, int units) {}

    private record Payload(
            BlockPos pos,
            int capacityUnits,
            int usedUnits,
            int totalLotCount,
            List<LotView> visibleLots
    ) {}

    private final DataSlot capacityUnits = trackedInt();
    private final DataSlot usedUnits = trackedInt();
    private final DataSlot totalLotCount = trackedInt();
    private final DataSlot inputConsumerWorkcells = trackedInt();
    private final DataSlot outputProducerWorkcells = trackedInt();
    private final List<LotView> visibleLots;

    public IndustrialBufferMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, readPayload(data));
    }

    public IndustrialBufferMenu(int containerId, Inventory inventory, BlockPos pos) {
        this(containerId, inventory, serverPayload(inventory, pos));
    }

    private IndustrialBufferMenu(int containerId, Inventory inventory, Payload payload) {
        super(EngineeringUiRegistration.INDUSTRIAL_BUFFER.get(), containerId, inventory, payload.pos(),
                EngineeringSystemsModule.INDUSTRIAL_BUFFER.get());
        capacityUnits.set(Math.max(0, payload.capacityUnits()));
        usedUnits.set(Math.max(0, payload.usedUnits()));
        totalLotCount.set(Math.max(0, payload.totalLotCount()));
        visibleLots = List.copyOf(payload.visibleLots());
        if (!level.isClientSide) refreshAuthoritativeSnapshot();
    }

    @Override
    protected void refreshAuthoritativeSnapshot() {
        OperationBufferSnapshot snapshot = IndustrialBufferBlock.snapshot(level, blockPos);
        if (snapshot == null) {
            capacityUnits.set(0);
            usedUnits.set(0);
            totalLotCount.set(0);
        } else {
            capacityUnits.set(snapshot.capacityUnits());
            usedUnits.set(snapshot.usedUnits());
            totalLotCount.set(snapshot.lots().size());
        }
        refreshWorkcellRoles();
    }

    private void refreshWorkcellRoles() {
        inputConsumerWorkcells.set(0);
        outputProducerWorkcells.set(0);
        if (!(level instanceof ServerLevel server)) return;
        String bufferId = IndustrialBufferBlock.bufferId(blockPos);
        int inputCount = 0;
        int outputCount = 0;
        for (OperationWorkcellBufferBinding binding : OperationPlantSavedData.get(server).workcellBufferBindings()) {
            if (bufferId.equals(binding.inputBufferId())) inputCount++;
            if (bufferId.equals(binding.outputBufferId())) outputCount++;
        }
        inputConsumerWorkcells.set(inputCount);
        outputProducerWorkcells.set(outputCount);
    }

    public int capacityUnits() { return capacityUnits.get(); }
    public int usedUnits() { return usedUnits.get(); }
    public int availableUnits() { return Math.max(0, capacityUnits() - usedUnits()); }
    public int totalLotCount() { return totalLotCount.get(); }
    public List<LotView> visibleLots() { return visibleLots; }
    public int inputConsumerWorkcells() { return inputConsumerWorkcells.get(); }
    public int outputProducerWorkcells() { return outputProducerWorkcells.get(); }
    public int wipPressurePercent() {
        if (capacityUnits() <= 0) return 0;
        return Math.max(0, Math.min(100, (int) Math.round(usedUnits() * 100.0 / capacityUnits())));
    }
    public int wipSignal() {
        if (capacityUnits() <= 0 || usedUnits() <= 0) return 0;
        int scaled = (int) Math.round(usedUnits() * 15.0 / capacityUnits());
        return Math.max(1, Math.min(15, scaled));
    }

    private static Payload readPayload(RegistryFriendlyByteBuf data) {
        BlockPos pos = data.readBlockPos();
        int capacity = Math.max(0, data.readVarInt());
        int used = Math.max(0, data.readVarInt());
        int total = Math.max(0, data.readVarInt());
        int visibleCount = Math.max(0, Math.min(IndustrialBufferUi.MAX_VISIBLE_LOTS, data.readVarInt()));
        ArrayList<LotView> visibleLots = new ArrayList<>(visibleCount);
        for (int index = 0; index < visibleCount; index++) {
            long outputId = data.readVarLong();
            long jobId = data.readVarLong();
            int units = Math.max(0, data.readVarInt());
            visibleLots.add(new LotView(outputId, jobId, units));
        }
        return new Payload(pos, capacity, used, total, visibleLots);
    }

    private static Payload serverPayload(Inventory inventory, BlockPos pos) {
        if (!(inventory.player.level() instanceof ServerLevel server)) {
            return new Payload(pos, 0, 0, 0, List.of());
        }
        OperationBufferSnapshot snapshot = IndustrialBufferBlock.snapshot(server, pos);
        if (snapshot == null) return new Payload(pos, 0, 0, 0, List.of());
        int visibleCount = Math.min(IndustrialBufferUi.MAX_VISIBLE_LOTS, snapshot.lots().size());
        ArrayList<LotView> visible = new ArrayList<>(visibleCount);
        for (int index = 0; index < visibleCount; index++) {
            OperationBufferLot lot = snapshot.lots().get(index);
            visible.add(new LotView(lot.outputId(), lot.jobId(), lot.units()));
        }
        return new Payload(pos, snapshot.capacityUnits(), snapshot.usedUnits(), snapshot.lots().size(), visible);
    }
}
