package com.automods.otcaddon.waiter;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * OrderToCook 物品的小工具。
 *
 * <p>这里刻意不引用 {@code ModItems}：只需按注册名认出「脏餐盘」这一件东西，
 * 用注册表 ID 比对既不用多加一份占位类，也不会因为本体的注册器写法变化而失配。</p>
 */
final class OtcItems {
    /** 脏餐盘的注册名（OrderToCook 本体）。 */
    private static final ResourceLocation DIRTY_PLATE_ID =
            ResourceLocation.fromNamespaceAndPath("ordertocook", "dirty_plate");

    private static final ResourceLocation CLEAN_PLATE_ID =
            ResourceLocation.fromNamespaceAndPath("ordertocook", "clean_plate");

    static boolean isCleanPlate(ItemStack stack) {
        return !stack.isEmpty() && CLEAN_PLATE_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    static ItemStack cleanPlateStack() {
        Item item = BuiltInRegistries.ITEM.get(CLEAN_PLATE_ID);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private OtcItems() {
    }

    static boolean isDirtyPlate(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return DIRTY_PLATE_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /** 拿脏餐盘物品本体；本体没装或版本不对时返回 null。 */
    static Item dirtyPlateItem() {
        Item item = BuiltInRegistries.ITEM.get(DIRTY_PLATE_ID);
        return item == null || item == Items.AIR ? null : item;
    }

    static ItemStack dirtyPlateStack(int count) {
        Item item = dirtyPlateItem();
        return item == null || count <= 0 ? ItemStack.EMPTY : new ItemStack(item, count);
    }
}
