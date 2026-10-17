package com.automods.otcaddon.api;

import cn.breezeth.ordertocook.api.IngredientSourceCompatApi;
import cn.breezeth.ordertocook.api.MenuFoodCompatApi;
import cn.breezeth.ordertocook.api.OrderLifecycleApi;
import cn.breezeth.ordertocook.api.OrderToCookApi;
import cn.breezeth.ordertocook.api.RestaurantSupplyCompatApi;
import com.automods.otcaddon.OtcAddon;
import net.minecraft.resources.ResourceLocation;

/**
 * 附属接入 OrderToCook 兼容 API（v1）的统一入口。
 *
 * <p>下一轮做具体内容时，只需要调用这里的 {@code registerXxx} 方法，不用再关心命名空间前缀、
 * 注册 ID 冲突和异常处理：ID 会自动加上 {@code otcaddon:} 前缀，注册失败也只会记一条错误日志，
 * 不会把 OrderToCook 主流程或游戏拖崩。</p>
 *
 * <p>OrderToCook 兼容 API 的规则见其仓库中的 {@code COMPATIBILITY_API.md}：注册 ID 必须唯一，
 * 优先级数字越大越先执行，处理器抛出的异常会被隔离。</p>
 */
public final class AddonApi {
    private AddonApi() {
    }

    /** 生成属于本附属的资源 ID，例如 {@code otcaddon:order_trace}。 */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(OtcAddon.MOD_ID, path);
    }

    /**
     * 在附属启动时调用一次。这里只挂框架自带的调试监听，确认 API 通道是通的；
     * 真正的功能注册应当另开方法，在具体内容里调用 registerXxx。
     */
    public static void bootstrap() {
        try {
            OtcAddon.LOGGER.info("已检测到 OrderToCook 兼容 API v{}", OrderToCookApi.API_VERSION);
            registerOrderListener("order_trace", 0, event -> {
                if (OtcAddon.LOGGER.isDebugEnabled()) {
                    OtcAddon.LOGGER.debug("订单事件 {}：{}（顾客 {}）",
                            event.type(), event.order().orderId(), event.order().customerName());
                }
            });
        } catch (Throwable error) {
            OtcAddon.LOGGER.error("接入 OrderToCook 兼容 API 失败；本附属需要 NeoForge 1.21.1 的 OrderToCook 1.3.x 及以上", error);
        }
    }

    /** 注册菜单食物判定规则：决定某个物品能不能进餐厅菜单、算多少饱食度。 */
    public static void registerMenuRule(String path, int priority, MenuFoodCompatApi.Rule rule) {
        try {
            MenuFoodCompatApi.register(id(path), priority, rule);
            registered("菜单规则", path);
        } catch (Throwable error) {
            failed("菜单规则", path, error);
        }
    }

    /** 注册食材来源：让操作台能从你自己的方块/容器里取食材。 */
    public static void registerIngredientSource(String path, int priority, IngredientSourceCompatApi.Handler handler) {
        try {
            IngredientSourceCompatApi.register(id(path), priority, handler);
            registered("食材来源", path);
        } catch (Throwable error) {
            failed("食材来源", path, error);
        }
    }

    /** 注册餐厅耗材来源：包装材料、干净餐盘、脏餐盘。 */
    public static void registerSupplySource(String path, int priority, RestaurantSupplyCompatApi.Handler handler) {
        try {
            RestaurantSupplyCompatApi.register(id(path), priority, handler);
            registered("餐厅耗材来源", path);
        } catch (Throwable error) {
            failed("餐厅耗材来源", path, error);
        }
    }

    /** 注册订单生命周期监听：订单生成、接取、打包、完成、过期时收到只读通知。 */
    public static void registerOrderListener(String path, int priority, OrderLifecycleApi.Listener listener) {
        try {
            OrderLifecycleApi.register(id(path), priority, listener);
            registered("订单监听", path);
        } catch (Throwable error) {
            failed("订单监听", path, error);
        }
    }

    private static void registered(String what, String path) {
        OtcAddon.LOGGER.info("已注册{}：{}", what, id(path));
    }

    private static void failed(String what, String path, Throwable error) {
        OtcAddon.LOGGER.error("注册{} {} 失败", what, id(path), error);
    }
}
