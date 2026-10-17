package cn.breezeth.ordertocook.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * OrderToCook 兼容 API 的编译期签名占位（compile-only stub），不会进 jar。
 * 运行期由 OrderToCook 本体提供真实实现。
 */
public final class CountertopAutomationApi {
    public enum Action { PACK, PLATE }

    public enum Status {
        SUCCESS, INVALID_TARGET, CLIENT_SIDE, NO_ORDER, EXPIRED, DELIVERY_REQUIRES_PACKAGING,
        MISSING_FOOD, MISSING_PACKAGING, MISSING_PLATE, OUTPUT_BLOCKED
    }

    public record Result(Status status) {
        public boolean success() {
            return status == Status.SUCCESS;
        }
    }

    public static Result execute(Level level, BlockPos pos, Action action, Player player, boolean simulate) {
        throw new UnsupportedOperationException("OrderToCook 兼容 API 编译期占位类不应在运行期被执行");
    }

    private CountertopAutomationApi() {
    }
}
