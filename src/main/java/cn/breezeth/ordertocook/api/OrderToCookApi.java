package cn.breezeth.ordertocook.api;

/**
 * OrderToCook 兼容 API 的编译期签名占位（compile-only stub）。
 *
 * <p>本文件位于附属源码里，只用于让附属通过编译；{@code build.gradle} 已把
 * {@code cn/breezeth/ordertocook/**} 排除出 jar，运行期这些类由 OrderToCook 本体提供。
 * 签名必须与被接入的 OrderToCook 版本保持一致，改动前先核对对方源码。</p>
 */
public final class OrderToCookApi {
    public static final int API_VERSION = 1;

    private OrderToCookApi() {
    }
}
