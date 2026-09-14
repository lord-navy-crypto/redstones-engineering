package dev.redstoneengineering.client;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.RoboticsEntityModule;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client-only registration boundary for RSE robotics visuals. */
@Mod(value = RedstoneEngineering.MOD_ID, dist = Dist.CLIENT)
public final class RoboticsClientModule {
    public RoboticsClientModule(IEventBus modEventBus) {
        modEventBus.addListener(RoboticsClientModule::registerRenderers);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                RoboticsEntityModule.ENGINEERING_MOBILE_ROBOT.get(),
                EngineeringMobileRobotRenderer::new
        );
    }
}
