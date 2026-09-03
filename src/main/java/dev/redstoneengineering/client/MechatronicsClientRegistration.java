package dev.redstoneengineering.client;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.blockentity.MechatronicsBlockEntityRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client-only GeckoLib renderer registration for Alpha 1.0.13 visualization. */
@EventBusSubscriber(modid = RedstoneEngineering.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class MechatronicsClientRegistration {
    private MechatronicsClientRegistration() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                MechatronicsBlockEntityRegistry.type(),
                context -> new MechatronicsGeoRenderer()
        );
    }
}
