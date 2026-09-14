package dev.redstoneengineering;

import dev.redstoneengineering.entity.EngineeringMobileRobotEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Dedicated registry boundary for RSE mobile robotics entities. */
@Mod(RedstoneEngineering.MOD_ID)
public final class RoboticsEntityModule {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, RedstoneEngineering.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<EngineeringMobileRobotEntity>> ENGINEERING_MOBILE_ROBOT =
            ENTITY_TYPES.register("engineering_mobile_robot", () ->
                    EntityType.Builder.of(EngineeringMobileRobotEntity::new, MobCategory.MISC)
                            .sized(0.9F, 0.72F)
                            .clientTrackingRange(8)
                            .updateInterval(2)
                            .build("engineering_mobile_robot"));

    public RoboticsEntityModule(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
