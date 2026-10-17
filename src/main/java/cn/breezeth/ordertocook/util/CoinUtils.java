package cn.breezeth.ordertocook.util;

import net.minecraft.world.entity.player.Player;

/**
 * OrderToCook「otc 币」钱包的编译期占位（compile-only stub），不会进 jar。
 *
 * <p>签名对齐 OrderToCook 1.3.7a：{@code countCoins} 数玩家身上有多少币，
 * {@code tryConsumeWithChange} 扣钱并找回零钱，{@code giveCoins} 发钱。
 * 本体已内置金币物品与 SDM 商店货币两种模式，这里只是转调。</p>
 */
public final class CoinUtils {
    public static int countCoins(Player player) {
        throw new UnsupportedOperationException("OrderToCook 兼容 API 编译期占位类不应在运行期被执行");
    }

    public static boolean tryConsumeWithChange(Player player, int amount) {
        throw new UnsupportedOperationException("OrderToCook 兼容 API 编译期占位类不应在运行期被执行");
    }

    public static void giveCoins(Player player, int amount) {
        throw new UnsupportedOperationException("OrderToCook 兼容 API 编译期占位类不应在运行期被执行");
    }

    private CoinUtils() {
    }
}
