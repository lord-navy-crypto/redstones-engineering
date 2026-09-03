package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import net.minecraft.world.level.block.Block;

public class InstrumentCableBlock extends Block {
    public InstrumentCableBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends InstrumentCableBlock> codec() {
        return RedstoneEngineering.INSTRUMENT_CABLE_CODEC.value();
    }
}
