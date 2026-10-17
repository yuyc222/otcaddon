package com.automods.otcaddon.money;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** 沿用原版九格容器画面，所有放入路径在服务端槽位统一校验。 */
public final class MoneyJarMenu extends DispenserMenu {
    public MoneyJarMenu(int id, Inventory inventory, Container jar) {
        super(id, inventory, jar);
        for (int i = 0; i < 9; i++) {
            Slot old = slots.get(i);
            Slot restricted = new Slot(jar, i, old.x, old.y) {
                @Override public boolean mayPlace(ItemStack stack) { return OtcCoins.isCoin(stack); }
            };
            restricted.index = i;
            slots.set(i, restricted);
        }
    }
}
