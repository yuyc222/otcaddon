package com.automods.otcaddon.money;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** 只接受本体的五种 otc 实体币，不接受普通金粒或自定义货币。 */
public final class OtcCoins {
    public static final int[] VALUES = {10000, 100, 20, 5, 1};
    private OtcCoins() { }
    public static ItemStack stack(int value, int count) {
        return new ItemStack(BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("ordertocook", "chef_coin_" + value)), count);
    }
    public static int value(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!"ordertocook".equals(id.getNamespace())) return 0;
        return switch (id.getPath()) {
            case "chef_coin_1" -> 1;
            case "chef_coin_5" -> 5;
            case "chef_coin_20" -> 20;
            case "chef_coin_100" -> 100;
            case "chef_coin_10000" -> 10000;
            default -> 0;
        };
    }
    public static boolean isCoin(ItemStack stack) { return value(stack) > 0; }
}
