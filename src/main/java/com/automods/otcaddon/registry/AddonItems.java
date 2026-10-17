package com.automods.otcaddon.registry;

import com.automods.otcaddon.OtcAddon;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 附属的物品注册器。
 *
 * <p>这里注册物品，再加对应的模型（{@code models/item/xxx.json}）、贴图和语言条目即可。
 * 这一版没有新增物品，只把注册通道搭好。</p>
 */
public final class AddonItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(OtcAddon.MOD_ID);

    public static final net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.BlockItem> MONEY_JAR =
            ITEMS.registerSimpleBlockItem("money_jar", AddonBlocks.MONEY_JAR);

    private AddonItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(AddonItems::creative);
    }

    private static void creative(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(net.minecraft.world.item.CreativeModeTabs.FUNCTIONAL_BLOCKS)) {
            event.accept(MONEY_JAR.get());
        }
    }
}
