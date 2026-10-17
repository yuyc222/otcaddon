package cn.breezeth.ordertocook.api;

import java.util.Collection;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * OrderToCook 兼容 API 的编译期签名占位（compile-only stub），不会进 jar。
 * 运行期由 OrderToCook 本体提供真实实现。
 */
public final class IngredientSourceCompatApi {
    public enum Probe { NOT_SOURCE, SOURCE }

    public interface Handler {
        Probe probe(Level level, BlockPos pos);

        int count(Level level, BlockPos pos, String itemId);

        int extract(Level level, BlockPos pos, String itemId, int amount, boolean simulate);
    }

    public static void register(ResourceLocation id, int priority, Handler handler) {
        throw new UnsupportedOperationException("OrderToCook 兼容 API 编译期占位类不应在运行期被执行");
    }

    public static int countNearby(Level level, BlockPos origin, int radius, String itemId) {
        throw new UnsupportedOperationException("OrderToCook 兼容 API 编译期占位类不应在运行期被执行");
    }

    public static Map<String, Integer> countAllNearby(Level level, BlockPos origin, int radius, Collection<String> itemIds) {
        throw new UnsupportedOperationException("OrderToCook 兼容 API 编译期占位类不应在运行期被执行");
    }

    public static int extractNearby(Level level, BlockPos origin, int radius, String itemId, int amount, boolean simulate) {
        throw new UnsupportedOperationException("OrderToCook 兼容 API 编译期占位类不应在运行期被执行");
    }

    private IngredientSourceCompatApi() {
    }
}
