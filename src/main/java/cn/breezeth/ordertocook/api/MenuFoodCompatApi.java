package cn.breezeth.ordertocook.api;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * OrderToCook 兼容 API 的编译期签名占位（compile-only stub），不会进 jar。
 * 运行期由 OrderToCook 本体提供真实实现。
 */
public final class MenuFoodCompatApi {
    public enum Decision { PASS, ALLOW, DENY }

    public enum Purpose { MENU, ORDER_GENERATION, REWARD }

    public record Context(Purpose purpose) {
        public Context {
            Objects.requireNonNull(purpose, "purpose");
        }
    }

    public record Result(Decision decision, int nutrition, ResourceLocation handlerId) {
        public Result {
            Objects.requireNonNull(decision, "decision");
            nutrition = Math.max(0, nutrition);
        }

        public boolean allowed() {
            return decision == Decision.ALLOW && nutrition > 0;
        }

        public static Result pass() {
            return new Result(Decision.PASS, 0, null);
        }

        public static Result allow(int nutrition) {
            return new Result(Decision.ALLOW, nutrition, null);
        }

        public static Result deny() {
            return new Result(Decision.DENY, 0, null);
        }
    }

    @FunctionalInterface
    public interface Rule {
        Result evaluate(ItemStack stack, Context context);
    }

    public static void register(ResourceLocation id, int priority, Rule rule) {
        throw new UnsupportedOperationException("OrderToCook 兼容 API 编译期占位类不应在运行期被执行");
    }

    public static Result resolve(ItemStack stack, Purpose purpose) {
        throw new UnsupportedOperationException("OrderToCook 兼容 API 编译期占位类不应在运行期被执行");
    }

    private MenuFoodCompatApi() {
    }
}
