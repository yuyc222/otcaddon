package com.automods.otcaddon.compat;

import cn.breezeth.ordertocook.api.OrderLifecycleApi;
import com.automods.otcaddon.api.AddonApi;
import com.automods.otcaddon.network.AddonNetwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 监听"订单被打包／装盘成功"的通知，把这一单用到的菜品 ID 推给对应客户端，
 * 让客户端撤掉本次由操作台缺料查询按钮加进 JEI 收藏的食物。
 *
 * <p>为什么要在服务端做：打包／装盘是服务端判定的，失败原因很多（缺菜、没餐盘、出口被堵…），
 * 而且成功失败都会把界面关掉，客户端光看界面分不出来。OrderToCook 的公开兼容 API
 * {@link OrderLifecycleApi} 会在真正成功时发一条 {@code PACKED} 事件，这是唯一可靠的"成功"信号。</p>
 *
 * <p>事件里带着这一单的完整菜谱，所以客户端只撤"这次加过、又正好属于这一单"的收藏，
 * 不会误删玩家自己钉的收藏或别的订单加的东西。没有玩家（自动化设备打包）时直接跳过。</p>
 */
public final class PackedBookmarkCleanup {
    private PackedBookmarkCleanup() {
    }

    public static void register() {
        AddonApi.registerOrderListener("packed_bookmark_cleanup", 0, event -> {
            if (event.type() != OrderLifecycleApi.Type.PACKED) return;
            if (!(event.player() instanceof ServerPlayer player)) return;
            Map<String, Integer> foods = event.order().foods();
            if (foods == null || foods.isEmpty()) return;
            List<ResourceLocation> itemIds = new ArrayList<>(foods.size());
            for (String key : foods.keySet()) {
                ResourceLocation id = ResourceLocation.tryParse(key);
                if (id != null) itemIds.add(id);
            }
            if (itemIds.isEmpty()) return;
            AddonNetwork.sendPackedBookmarks(player, itemIds);
        });
    }
}
