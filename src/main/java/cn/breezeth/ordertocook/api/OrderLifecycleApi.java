package cn.breezeth.ordertocook.api;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * OrderToCook 兼容 API 的编译期签名占位（compile-only stub），不会进 jar。
 * 运行期由 OrderToCook 本体提供真实实现。
 */
public final class OrderLifecycleApi {
    public enum Type { GENERATED, ACCEPTED, PACKED, COMPLETED, EXPIRED, CANCELLED }

    public record OrderView(String orderId, String customerName, int machineId, boolean delivery, boolean longDistance,
                            boolean urgent, long expiryTick, int prestige, Map<String, Integer> foods) {
    }

    public record Event(Type type, ServerLevel world, Player player, OrderView order) {
    }

    @FunctionalInterface
    public interface Listener {
        void onOrderEvent(Event event);
    }

    public static void register(ResourceLocation id, int priority, Listener listener) {
        throw new UnsupportedOperationException("OrderToCook 兼容 API 编译期占位类不应在运行期被执行");
    }

    public static void emit(Type type, ServerLevel world, Player player, ItemStack stack) {
        throw new UnsupportedOperationException("OrderToCook 兼容 API 编译期占位类不应在运行期被执行");
    }

    public static void emit(Type type, ServerLevel world, Player player, OrderView view) {
        throw new UnsupportedOperationException("OrderToCook 兼容 API 编译期占位类不应在运行期被执行");
    }

    private OrderLifecycleApi() {
    }
}
