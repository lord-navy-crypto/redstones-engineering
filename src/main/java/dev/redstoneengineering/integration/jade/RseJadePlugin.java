package dev.redstoneengineering.integration.jade;

import net.minecraft.world.level.block.Block;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade adapter for the RSE engineering-port contract.
 *
 * <p>Jade is a read-only presentation layer here. It never decides topology,
 * signal propagation, control output, or physical compatibility.</p>
 */
@WailaPlugin
public final class RseJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(EngineeringPortJadeProvider.INSTANCE, Block.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(EngineeringPortJadeProvider.INSTANCE, Block.class);
    }
}
