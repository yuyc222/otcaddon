package com.automods.otcaddon.client;

import com.automods.otcaddon.OtcAddon;
import java.lang.reflect.Method;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 把 GeckoLib 因为「某一个坏掉的动画文件」而整体丢掉的动画缓存，从资源包重新烘焙补回来。
 *
 * <p><b>问题到底是什么</b>：GeckoLib 载入动画时，会先把所有模组里
 * {@code assets/<命名空间>/animations/} 下的文件列出来，然后<b>用一个循环</b>逐个烘焙、塞进一张
 * 全局缓存表。这个循环没有做逐个文件的容错：只要中途有<b>任意一个</b>文件解不开（例如某个模组把
 * {@code .geo.json} 模型文件错放进了 {@code animations} 目录，或者文件的 JSON 结构不合规），
 * 循环就直接中断，<b>排在它后面的所有动画文件全部被丢掉，而且一条日志都不会留</b> —— 异常被挂在
 * 一个没人接的异步任务上，被静默吞掉了。</p>
 *
 * <p>后果：Order To Cook 的顾客动画（{@code customer_sit_1~4}、{@code customer_wait_1~4}、
 * {@code npc_sitspawn}）和打单机动画（{@code neworder}）明明文件就在 jar 里、内容也完全正常，
 * 却<b>整批不进缓存</b>。顾客一渲染，GeckoLib 找不到动画就每帧打印一条</p>
 *
 * <pre>[Render thread/ERROR] [GeckoLib/]: Unable to find animation: customer_sit_1 for CustomerEntity</pre>
 *
 * <p>所以刷屏的根因不是「前置缺动画名」，而是「前置的动画文件被别的模组挤出了缓存」。这也解释了
 * 为什么顾客会僵着不动 —— 拿不到动画就没有骨骼动作可播。</p>
 *
 * <p><b>这里怎么修</b>：不动任何一个模组的文件。资源重载完成后，我们自己列一遍
 * {@code animations/} 下的全部文件，凡是缓存里没有的，就用 GeckoLib 自己的
 * {@code FileLoader.loadAnimationsFile} 重新烘焙一份塞回去；那个真正的坏文件当然还是烘不出来，
 * 单独跳过即可，不会连累其他文件。补完之后顾客动画照常播放，报错自然也就不再产生。</p>
 *
 * <p>整个过程全部走反射 + try/catch：GeckoLib 没装、改了方法名、缓存表不可写……最坏结果都只是
 * 「补不上，回到原来的样子」，绝不会因为这段代码让游戏崩掉。</p>
 */
@EventBusSubscriber(modid = OtcAddon.MOD_ID, value = Dist.CLIENT)
public final class GeckoLibAnimationRepair {

    /** 存放「烘焙好的动画」的类与读取方法（GeckoLib 的公开接口）。 */
    private static final String CACHE_CLASS = "software.bernie.geckolib.cache.GeckoLibCache";
    private static final String CACHE_METHOD = "getBakedAnimations";

    /** 真正负责烘焙单个动画文件的类与静态方法。 */
    private static final String LOADER_CLASS = "software.bernie.geckolib.loading.FileLoader";
    private static final String LOADER_METHOD = "loadAnimationsFile";

    /** 动画文件所在的目录名（即 {@code assets/<命名空间>/animations/}）。 */
    private static final String ANIMATION_FOLDER = "animations";

    /** 资源重载是异步的，先等 3 秒再动手，免得和 GeckoLib 自己的烘焙抢同一批文件。 */
    private static final int FIRST_DELAY_TICKS = 60;

    /** 补过一轮后，再隔 3 秒复查一次的间隔。 */
    private static final int RETRY_DELAY_TICKS = 60;

    /** 最多复查几轮，防止异常情况下无限重试。 */
    private static final int MAX_ROUNDS = 4;

    /** 反射拿到的两个方法句柄；为 {@code null} 表示还没查到或根本不存在。 */
    private static Method cacheGetter;
    private static Method animationsLoader;

    /** 反射查找只认真做一次，失败过一次就不再反复报错。 */
    private static boolean lookupTried;

    /** 当前正在处理的缓存表实例（每次重载 GeckoLib 都会换一张新表）。 */
    private static Object pendingCache;

    /** 这一张缓存表是否已经处理完。 */
    private static boolean settled;

    /** 距离下一次动手还有几 tick。 */
    private static int countdown;

    /** 这一张表还允许复查几轮。 */
    private static int rounds;

    private GeckoLibAnimationRepair() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        try {
            tick();
        } catch (Throwable ignored) {
            // 补缓存是纯兜底功能，任何意外都不该影响到游戏本身。
        }
    }

    /** 每 tick 走一次的状态机：等重载稳定 → 补一轮 → 再复查一轮 → 收工。 */
    private static void tick() throws ReflectiveOperationException {
        if (!lookup()) {
            return;
        }
        Object cache = cacheGetter.invoke(null);
        if (!(cache instanceof Map<?, ?>)) {
            return;
        }
        if (cache != pendingCache) {
            // GeckoLib 重载后会换一张新表，这里重新开始计时。
            pendingCache = cache;
            settled = false;
            rounds = MAX_ROUNDS;
            countdown = FIRST_DELAY_TICKS;
            return;
        }
        if (settled) {
            return;
        }
        if (--countdown > 0) {
            return;
        }
        settled = repair(cache);
        if (!settled && --rounds <= 0) {
            // 该补的都补过了（剩下的是文件本身有问题），不再空转。
            settled = true;
        }
        if (!settled) {
            countdown = RETRY_DELAY_TICKS;
        }
    }

    /**
     * 把缓存表里缺的动画文件逐个重新烘焙补上。
     *
     * @return {@code true} 表示这一轮没有再补进任何东西，可以收工了
     */
    private static boolean repair(Object rawCache) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return false;
        }
        // 客户端资源包管理器，模组自带的资源也在里面。
        ResourceManager resources = client.getResourceManager();
        if (resources == null) {
            return false;
        }
        Map<ResourceLocation, Resource> files;
        try {
            files = resources.listResources(ANIMATION_FOLDER, GeckoLibAnimationRepair::isAnimationFile);
        } catch (Throwable failure) {
            return false;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        Map<Object, Object> cache = (Map) rawCache;
        int restored = 0;
        for (ResourceLocation location : files.keySet()) {
            if (cache.containsKey(location)) {
                continue;
            }
            try {
                Object baked = animationsLoader.invoke(null, location, resources);
                if (baked != null) {
                    cache.put(location, baked);
                    restored++;
                }
            } catch (Throwable skipped) {
                // 这个文件本身有问题（例如把模型文件错放进了 animations 目录），跳过它就行。
            }
        }
        if (restored > 0) {
            OtcAddon.LOGGER.info(
                    "GeckoLib 动画缓存里少了 {} 个文件，已按资源包重新烘焙补回；Order To Cook 的顾客动画就在这批里",
                    restored);
        }
        return restored == 0;
    }

    /** 只处理 {@code animations/} 下的 JSON，和 GeckoLib 自己的扫描范围保持一致。 */
    private static boolean isAnimationFile(ResourceLocation location) {
        return location.getPath().endsWith(".json");
    }

    /** 反射取两个方法句柄；只尝试一次，取不到就彻底放弃（并留一条说明）。 */
    private static boolean lookup() {
        if (cacheGetter != null && animationsLoader != null) {
            return true;
        }
        if (lookupTried) {
            return false;
        }
        lookupTried = true;
        try {
            cacheGetter = Class.forName(CACHE_CLASS).getMethod(CACHE_METHOD);
            animationsLoader = Class.forName(LOADER_CLASS)
                    .getMethod(LOADER_METHOD, ResourceLocation.class, ResourceManager.class);
            return true;
        } catch (Throwable missing) {
            OtcAddon.LOGGER.warn(
                    "没有找到 GeckoLib 的动画缓存接口，无法自动补回被挤掉的动画（未安装 GeckoLib 时可忽略本条）",
                    missing);
            return false;
        }
    }
}
