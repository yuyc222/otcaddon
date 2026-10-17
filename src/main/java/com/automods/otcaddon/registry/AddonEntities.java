package com.automods.otcaddon.registry;

import com.automods.otcaddon.OtcAddon;
import com.automods.otcaddon.waiter.AllayWaiter;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 附属的实体注册器：悦灵服务员。
 *
 * <p>它被登记在 {@link MobCategory#MISC}（杂项）里，不是生物类别——既不占生物上限，
 * 也不参与自然生成，符合"只是一具会飞的模型"的定位。</p>
 */
public final class AddonEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, OtcAddon.MOD_ID);

    /** 悦灵服务员实体类型。 */
    public static final DeferredHolder<EntityType<?>, EntityType<AllayWaiter>> ALLAY_WAITER =
            ENTITY_TYPES.register("allay_waiter", () -> EntityType.Builder
                    .of(AllayWaiter::new, MobCategory.MISC)
                    .sized(0.6F, 0.6F)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build(OtcAddon.MOD_ID + ":allay_waiter"));

    private AddonEntities() {
    }

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
    }
}
