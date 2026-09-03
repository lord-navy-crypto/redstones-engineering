package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.RadioKernel;
import dev.redstoneengineering.physics.RuntimeIntStore;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Radio receiver: decoded payload remains a 0..15 signal while link diagnostics
 * accumulate separately in transient runtime state.
 */
public class RadioReceiverBlock extends PassiveDirectionalSignalBlock {
    public static final IntegerProperty CHANNEL = IntegerProperty.create("channel", 0, 3);

    private static final String DIAG_KEY = "radio_rx_diag";
    private static final int DIAG_SIZE = 10;

    public RadioReceiverBlock(Properties p) {
        super(p);
        registerDefaultState(defaultBlockState().setValue(CHANNEL, 0));
    }

    @Override
    public MapCodec<RadioReceiverBlock> codec() {
        return RedstoneEngineering.RADIO_RECEIVER_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        super.createBlockStateDefinition(b);
        b.add(CHANNEL);
    }

    @Override
    protected int computeOutput(Level l, BlockPos p, BlockState s) {
        return RadioKernel.receivePacket(l, p, s.getValue(CHANNEL)).value();
    }

    @Override
    protected void onPlace(BlockState s, Level l, BlockPos p, BlockState o, boolean m) {
        super.onPlace(s, l, p, o, m);
        if (l instanceof ServerLevel sl) sl.scheduleTick(p, this, 5);
    }

    @Override
    protected void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource random) {
        var rx = RadioKernel.receivePacket(l, p, s.getValue(CHANNEL));
        int[] d = RuntimeIntStore.get(l, DIAG_KEY, p, DIAG_SIZE);

        int linkQuality = rx.quality();
        int noiseStrength = Math.min(100, rx.interference() * 8 + rx.obstacles() * 2);
        int droppedThisTick = d[6] != 0 && !rx.valid() ? 1 : 0;

        d[0]++; // samples
        if (rx.valid()) d[1]++;
        else if (rx.collision()) d[3]++;
        else d[2]++; // undecodable frames without a same-channel collision
        d[4] += droppedThisTick; // dropouts after a previously valid sample
        d[6] = rx.valid() ? 1 : 0;
        d[7] = linkQuality;
        d[8] = noiseStrength;
        d[9] = s.getValue(CHANNEL);

        updateOutput(l, p, s, rx.value());
        l.scheduleTick(p, this, 5);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player pl, BlockHitResult h) {
        if (!l.isClientSide) {
            if (pl.isShiftKeyDown()) {
                RuntimeIntStore.remove(l, DIAG_KEY, p);
                pl.displayClientMessage(Component.literal("Radio RX accumulated diagnostics reset"), true);
            } else {
                int oldChannel = s.getValue(CHANNEL);
                int channel = (oldChannel + 1) % 4;
                BlockState ns = s.setValue(CHANNEL, channel);
                l.setBlock(p, ns, Block.UPDATE_CLIENTS);

                int[] d = RuntimeIntStore.get(l, DIAG_KEY, p, DIAG_SIZE);
                if (channel != oldChannel) d[5]++; // operator/tuner handoffs
                d[9] = channel;

                var rx = RadioKernel.receivePacket(l, p, channel);
                int linkQuality = rx.quality();
                int noiseStrength = Math.min(100, rx.interference() * 8 + rx.obstacles() * 2);
                int dropouts = d[4];
                int handoffs = d[5];

                pl.displayClientMessage(Component.literal(
                        "Radio RX ch=" + channel
                                + " payload=" + rx.value() + "/15"
                                + " linkQuality=" + linkQuality + "%"
                                + " noiseStrength=" + noiseStrength + "%"
                                + " drivers=" + rx.drivers()
                                + " adjacentInterference=" + rx.interference()
                                + " obstacles=" + rx.obstacles()
                                + " latency≈" + rx.latencyTicks() + "t"
                                + " | samples=" + d[0]
                                + " valid=" + d[1]
                                + " undecodable=" + d[2]
                                + " collisions=" + d[3]
                                + " dropouts=" + dropouts
                                + " handoffs=" + handoffs
                                + " | " + (rx.collision() ? "COLLISION" : rx.valid() ? "VALID" : "UNDECODABLE")), true);
            }
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
