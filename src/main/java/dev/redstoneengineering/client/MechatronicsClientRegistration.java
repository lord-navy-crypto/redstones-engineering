package dev.redstoneengineering.client;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.blockentity.MechatronicsBlockEntityRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client-only GeckoLib renderer registration for Alpha 1.0.13 visualization. */
@Mod(value = RedstoneEngineering.MOD_ID, dist = Dist.CLIENT)
public final class MechatronicsClientRegistration {
    public MechatronicsClientRegistration(IEventBus modBus) {
        modBus.addListener(MechatronicsClientRegistration::registerRenderers);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                MechatronicsBlockEntityRegistry.type(),
                context -> new MechatronicsGeoRenderer()
        );
    }
}