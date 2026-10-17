package com.automods.otcaddon;

import com.automods.otcaddon.api.AddonApi;
import com.automods.otcaddon.compat.OrderToCookCompat;
import com.automods.otcaddon.compat.OtcAnimationSpamFilter;
import com.automods.otcaddon.compat.PackedBookmarkCleanup;
import com.automods.otcaddon.network.AddonNetwork;
import com.automods.otcaddon.registry.AddonBlocks;
import com.automods.otcaddon.registry.AddonEntities;
import com.automods.otcaddon.registry.AddonItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Order To Cook（下单了）附属模组的主入口。
 *
 * <p>负责把注册器、通信包和 OrderToCook 兼容 API 接到模组事件总线上。主要功能有立牌菜单的
 * JEI 快捷添加（按住 Shift 点击食物）、主手拿着外卖袋时的指路箭头，以及针对 GeckoLib 动画缓存
 * 被别的模组挤掉时的兼容处理。</p>
 */
@Mod(OtcAddon.MOD_ID)
public final class OtcAddon {
    /** 本附属的模组 ID，同时是资源命名空间（assets/otcaddon）。 */
    public static final String MOD_ID = "otcaddon";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public OtcAddon(IEventBus modBus, ModContainer modContainer) {
        // 注册游戏内配置：client 类型，改 config/otcaddon-client.toml 就能调指路箭头。
        modContainer.registerConfig(ModConfig.Type.CLIENT, AddonConfig.SPEC);

        // 先把注册器挂到模组事件总线上，之后往里面加东西就会自动生效。
        AddonItems.register(modBus);
        AddonBlocks.register(modBus);
        AddonEntities.register(modBus);

        // 挂上立牌菜单相关的通信包。
        AddonNetwork.register(modBus);

        // 接入 OrderToCook 的公开兼容 API，并启用菜单食物与相连操作台共享库存。
        AddonApi.bootstrap();
        OrderToCookCompat.register();

        // 打包／装盘成功后，通知客户端撤掉本次由缺料查询按钮加进 JEI 收藏的食物。
        PackedBookmarkCleanup.register();

        // GeckoLib 的动画载入循环不带逐文件容错：别的模组只要有一个动画文件解不开，循环就会静默中断，
        // 排在后面的动画整批进不了缓存，Order To Cook 的顾客动画因此被挤掉、每帧刷 ERROR。真正的修复是
        // 客户端侧的 GeckoLibAnimationRepair（重新烘焙补回缓存）；这里装的是日志出口的兜底过滤网。
        OtcAnimationSpamFilter.install();

        LOGGER.info("Order To Cook 附属功能已就绪（mod id: {}）", MOD_ID);
    }
}
