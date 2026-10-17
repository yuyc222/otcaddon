package com.automods.otcaddon.client;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 记录"由操作台缺料查询按钮加进 JEI 收藏列表"的食物，并在打包／装盘成功后把这些收藏撤掉。
 *
 * <p>为什么要单独记一份名单：JEI 收藏（bookmark，就是 JEI 界面左侧那一列钉住的东西）是玩家
 * 自己的常用清单，附属看不到它当初是谁加进去的。所以这里只记"本次确实由这个按钮加进去的"，
 * 撤除时也只动这些；玩家自己钉上去的收藏一个都不碰。</p>
 *
 * <p>本类刻意不引用任何 JEI 类型：JEI 对本模组是可选依赖，没装 JEI 时这个类也必须能正常加载。
 * 真正的收藏读写由 JEI 插件在运行时通过 {@link BookmarkAccess} 挂进来，没装 JEI 时它一直是 null，
 * 于是类加载器根本不会碰到 JEI 的那些类。</p>
 */
public final class MissingFoodBookmarks {
    /** 由 JEI 插件在运行时装进来的收藏读写实现。 */
    interface BookmarkAccess {
        /** 把物品加进收藏，返回确实由这次操作加进去的那部分。 */
        List<ResourceLocation> add(List<ResourceLocation> itemIds);

        /** 从收藏里移除物品。 */
        void remove(List<ResourceLocation> itemIds);
    }

    /** 本次由查询按钮加进 JEI 收藏、且还没被撤掉的食物。 */
    private static final Set<ResourceLocation> addedByQuery = new LinkedHashSet<>();
    private static volatile BookmarkAccess bookmarkAccess;

    private MissingFoodBookmarks() {
    }

    /** JEI 运行时可用时，由插件把读写实现挂进来。 */
    static void onJeiRuntimeAvailable(BookmarkAccess access) {
        bookmarkAccess = access;
    }

    /** JEI 运行时销毁时摘掉实现并清空名单，避免留下幽灵记录。 */
    static void onJeiRuntimeUnavailable() {
        bookmarkAccess = null;
        addedByQuery.clear();
    }

    static boolean jeiReady() {
        return bookmarkAccess != null;
    }

    /** 点击"缺料查询"按钮时调用：把缺的菜加进 JEI 收藏，并记下这是本次加的。 */
    public static void addMissing(List<ResourceLocation> missingIds) {
        if (missingIds == null || missingIds.isEmpty()) return;
        BookmarkAccess access = bookmarkAccess;
        if (access == null) {
            BoardFoodFeedback.showOverlayOrActionBar(Component.translatable("message.otcaddon.query.no_jei"));
            return;
        }
        List<ResourceLocation> added = access.add(missingIds);
        addedByQuery.addAll(added);
        if (added.isEmpty()) {
            BoardFoodFeedback.showOverlayOrActionBar(Component.translatable("message.otcaddon.query.exists"));
        } else {
            BoardFoodFeedback.showOverlayOrActionBar(
                    Component.translatable("message.otcaddon.query.added", added.size()));
        }
    }

    /**
     * 打包／装盘成功后调用（由服务端的订单 PACKED 事件推过来）：只撤掉"本次由查询按钮加进去、
     * 而且正好属于这一单"的食物，所以玩家手动收藏的东西、别的订单加的东西都不会被误删。
     */
    public static void clearPacked(List<ResourceLocation> packedIds) {
        if (addedByQuery.isEmpty() || packedIds == null || packedIds.isEmpty()) return;
        List<ResourceLocation> toRemove = new ArrayList<>();
        for (ResourceLocation id : packedIds) {
            if (addedByQuery.contains(id)) toRemove.add(id);
        }
        if (toRemove.isEmpty()) return;
        BookmarkAccess access = bookmarkAccess;
        if (access != null) access.remove(toRemove);
        addedByQuery.removeAll(toRemove);
        BoardFoodFeedback.showOverlayOrActionBar(
                Component.translatable("message.otcaddon.query.cleared", toRemove.size()));
    }

    /** 退出世界时清掉名单，免得下一个存档里莫名为上一局的收藏买单。 */
    public static void reset() {
        addedByQuery.clear();
    }
}
