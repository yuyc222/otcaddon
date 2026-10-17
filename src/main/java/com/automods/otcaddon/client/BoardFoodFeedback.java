package com.automods.otcaddon.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** 把服务端返回的“添加成功/失败”结果，显示在立牌界面最上层（界面开着时）或物品栏上方（界面已关时）。 */
public final class BoardFoodFeedback {
    private BoardFoodFeedback() {
    }

    public static void show(int status, ResourceLocation itemId) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        Component name = itemName(itemId);
        Component message = switch (status) {
            case 0 -> Component.translatable("message.otcaddon.board.added", name);
            case 1 -> Component.translatable("message.otcaddon.board.exists", name);
            case 2 -> Component.translatable("message.otcaddon.board.not_food", name);
            case 3 -> Component.translatable("message.otcaddon.board.not_open");
            case 4 -> Component.translatable("message.otcaddon.board.full");
            default -> Component.translatable("message.otcaddon.board.unavailable");
        };
        showOverlayOrActionBar(message);
    }

    /**
     * 立牌／操作台界面开着的时候走 {@link BoardHintOverlay}：原版动作栏那行字在 HUD 层，
     * 会被界面整个盖住；界面上层绘制才能看见。界面已经关掉（比如服务端回"请先打开立牌菜单"、
     * 或者打包成功自动关界面）时，退回原版动作栏。
     */
    public static void showOverlayOrActionBar(Component message) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || message == null) return;
        if (BoardScreens.showsOverlay(Minecraft.getInstance().screen)) {
            BoardHintOverlay.show(message);
        } else {
            player.displayClientMessage(message, true);
        }
    }

    private static Component itemName(ResourceLocation itemId) {
        if (itemId != null && BuiltInRegistries.ITEM.containsKey(itemId)) {
            Item item = BuiltInRegistries.ITEM.get(itemId);
            if (item != null) return new ItemStack(item).getHoverName();
        }
        return Component.literal(String.valueOf(itemId));
    }
}
