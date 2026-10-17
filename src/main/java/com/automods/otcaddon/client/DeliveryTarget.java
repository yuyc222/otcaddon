package com.automods.otcaddon.client;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * 玩家主手上那个物品给出的「指路目标」。
 *
 * <p>目前只有一种来源：Order To Cook 打包好的外卖袋 {@code ordertocook:takeout_bag}：
 * 目标写在物品自带的 NBT 里（键名 {@code delivery_pos}；原模组当前只写 {@code x}/{@code z}，
 * 所以缺少 {@code y} 时按当前维度的海平面高度定位；若未来版本写入 y，则优先使用它）。
 * 点单票据等其它物品即使带着坐标也不认。</p>
 *
 * <p>只读主手，副手拿着的不算；订单过期或读不到坐标就当作没有目标。</p>
 */
public final class DeliveryTarget {
    /** Order To Cook 的命名空间；只有它的物品才可能有配送坐标。 */
    private static final String OTC_NAMESPACE = "ordertocook";
    /** 打包好的外卖袋的物品名（命名空间 {@code ordertocook} 下）。 */
    private static final String TAKEOUT_BAG_PATH = "takeout_bag";
    /** 配送坐标所在的 NBT 键（对应 OrderToCook 的 {@code ModConstants.NBT_DELIVERY_POS}）。 */
    private static final String DELIVERY_POS = "delivery_pos";
    /** 订单到期时间（游戏刻），对应 {@code ModConstants.NBT_EXPIRY_TICK}。 */
    private static final String EXPIRY_TICK = "ExpiryTick";
    /** 坐标分量在 NBT 里的键名。 */
    private static final String KEY_X = "x";
    private static final String KEY_Y = "y";
    private static final String KEY_Z = "z";

    private final int x;
    private final int y;
    private final int z;

    private DeliveryTarget(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /** 目标方块的 X 坐标。 */
    public int x() {
        return x;
    }

    /** 目标方块的 Y 坐标（高度）：箭头靠它决定往上还是往下指。 */
    public int y() {
        return y;
    }

    /** 目标方块的 Z 坐标。 */
    public int z() {
        return z;
    }

    /**
     * 读取玩家<b>主手</b>物品给出的指路目标；主手物品不指路（哪怕副手拿着指路物品）就返回 {@code null}。
     */
    public static DeliveryTarget heldBy(Player player) {
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            return null;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());

        // 必须是 Order To Cook 的 takeout_bag：点单票据等其它物品即使带着坐标也不指路。
        if (!OTC_NAMESPACE.equals(itemId.getNamespace())
                || !TAKEOUT_BAG_PATH.equals(itemId.getPath())) {
            return null;
        }

        return fromTakeoutBag(stack, player);
    }

    /** 从打包好的外卖袋 NBT 里读配送坐标；读不到或订单已过期返回 {@code null}。 */
    private static DeliveryTarget fromTakeoutBag(ItemStack stack, Player player) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || !customData.contains(DELIVERY_POS)) {
            return null;
        }

        CompoundTag tag = customData.copyTag();
        if (!tag.contains(DELIVERY_POS, Tag.TAG_COMPOUND)) {
            return null;
        }

        // 订单过期后目标已经失效，再指路只会误导玩家。
        if (tag.contains(EXPIRY_TICK, Tag.TAG_LONG)
                && player.level().getGameTime() >= tag.getLong(EXPIRY_TICK)) {
            return null;
        }

        CompoundTag pos = tag.getCompound(DELIVERY_POS);
        if (!pos.contains(KEY_X, Tag.TAG_INT) || !pos.contains(KEY_Z, Tag.TAG_INT)) {
            return null;
        }

        int x = pos.getInt(KEY_X);
        int z = pos.getInt(KEY_Z);
        // 原模组当前只写 x/z；按需求用当前维度海平面补 y。未来若本体写入 y，则直接使用。
        int y = pos.contains(KEY_Y, Tag.TAG_INT)
                ? pos.getInt(KEY_Y)
                : player.level().getSeaLevel();

        return new DeliveryTarget(x, y, z);
    }
}
