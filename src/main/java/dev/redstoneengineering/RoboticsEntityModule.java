package dev.redstoneengineering;

import dev.redstoneengineering.entity.EngineeringMobileRobotEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** Dedicated registry boundary for RSE mobile robotics entities. */
public final class RoboticsEntityModule {
    private RoboticsEntityModule() {}

    public static final DeferredRegister.Entities ENTITY_TYPES =
            DeferredRegister.createEntities(RedstoneEngineering.MOD_ID);

    public static final Supplier<EntityType<EngineeringMobileRobotEntity>> ENGINEERING_MOBILE_ROBOT =
            ENTITY_TYPES.registerEntityType(
                    "engineering_mobile_robot",
                    EngineeringMobileRobotEntity::new,
                    MobCategory.MISC,
                    builder -> builder.sized(0.9F, 0.72F).clientTrackingRange(8).updateInterval(2)
            );

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
