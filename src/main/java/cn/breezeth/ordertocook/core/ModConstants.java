package cn.breezeth.ordertocook.core;

/**
 * OrderToCook 内部常量表的编译期占位（compile-only stub），不会进 jar。
 *
 * <p>本附属要读订单物品 NBT 里的订单号和外卖坐标，这里只声明用到的两个键，
 * 取值与 OrderToCook 1.3.7a 的源码一致。运行期由本体提供真实类。</p>
 */
public final class ModConstants {
    public static final String NBT_ORDER_ID = "OrderId";
    public static final String NBT_DELIVERY_POS = "delivery_pos";
    public static final String NBT_EXPIRY_TICK = "ExpiryTick";
    public static final String NBT_EXPIRY_TIME = "ExpiryTime";

    private ModConstants() {
    }
}
