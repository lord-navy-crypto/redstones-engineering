package dev.redstoneengineering.blockentity;

import dev.redstoneengineering.RedstoneEngineering;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Registers the shared GeckoLib visualization block entity without touching physics registration. */
@EventBusSubscriber(modid = RedstoneEngineering.MOD_ID)
public final class MechatronicsBlockEntityRegistry {
    private static BlockEntityType<MechatronicsVisualBlockEntity> TYPE;

    private MechatronicsBlockEntityRegistry() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.BLOCK_ENTITY_TYPE, registry -> {
            TYPE = BlockEntityType.Builder.of(
                    MechatronicsVisualBlockEntity::new,
                    RedstoneEngineering.SERVO_ACTUATOR.get(),
                    RedstoneEngineering.PNEUMATIC_CYLINDER.get(),
                    RedstoneEngineering.PNEUMATIC_PROPORTIONAL_VALVE.get()
            ).build(null);
            registry.register(
                    ResourceLocation.fromNamespaceAndPath(RedstoneEngineering.MOD_ID, "mechatronics_visual"),
                    TYPE
            );
        });
    }

    public static BlockEntityType<MechatronicsVisualBlockEntity> type() {
        if (TYPE == null) {
            throw new IllegalStateException("Mechatronics visual block entity type requested before registry initialization");
        }
        return TYPE;
    }
}