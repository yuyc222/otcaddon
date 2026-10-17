package com.automods.otcaddon.money;

import com.automods.otcaddon.registry.AddonBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class MoneyJarBlockEntity extends BaseContainerBlockEntity {
    private NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);
    public MoneyJarBlockEntity(BlockPos pos, BlockState state) { super(AddonBlocks.MONEY_JAR_ENTITY.get(), pos, state); }
    @Override public int getContainerSize() { return 9; }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override protected Component getDefaultName() { return Component.translatable("block.otcaddon.money_jar"); }
    @Override protected AbstractContainerMenu createMenu(int id, Inventory inventory) { return new MoneyJarMenu(id, inventory, this); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return OtcCoins.isCoin(stack); }
    @Override public void setItem(int slot, ItemStack stack) {
        if (stack.isEmpty() || canPlaceItem(slot, stack)) super.setItem(slot, stack);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        ContainerHelper.saveAllItems(tag, items, provider);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        items = NonNullList.withSize(9, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, provider);
    }
    public int balance() {
        int total = 0;
        for (ItemStack stack : items) total += OtcCoins.value(stack) * stack.getCount();
        return total;
    }
    /** 正常堆叠，不自动压缩已有零钱；九格全满时确实会溢出。返回没存下的钱。 */
    public int deposit(int amount) {
        int rest = amount;
        for (int value : OtcCoins.VALUES) {
            int count = rest / value;
            if (count == 0) continue;
            ItemStack offered = OtcCoins.stack(value, count);
            if (offered.isEmpty()) continue;
            for (int pass = 0; pass < 2 && !offered.isEmpty(); pass++) {
                for (int i = 0; i < 9 && !offered.isEmpty(); i++) {
                    ItemStack current = items.get(i);
                    if (pass == 0 && !current.isEmpty() && ItemStack.isSameItemSameComponents(current, offered)) {
                        int moved = Math.min(offered.getCount(), current.getMaxStackSize() - current.getCount());
                        if (moved > 0) { current.grow(moved); offered.shrink(moved); rest -= moved * value; }
                    } else if (pass == 1 && current.isEmpty()) {
                        int moved = Math.min(offered.getCount(), offered.getMaxStackSize());
                        items.set(i, offered.copyWithCount(moved));
                        offered.shrink(moved);
                        rest -= moved * value;
                    }
                }
            }
        }
        if (rest != amount) setChanged();
        return rest;
    }
    /** 扣款时只取需要的硬币；大币找零仍优先进罐，满了再给玩家。 */
    public int withdraw(int amount) {
        int removed = 0;
        for (int i = 0; i < 9 && removed < amount; i++) {
            ItemStack stack = items.get(i);
            int value = OtcCoins.value(stack);
            if (value == 0) continue;
            int take = Math.min(stack.getCount(), (amount - removed + value - 1) / value);
            stack.shrink(take);
            removed += take * value;
        }
        if (removed > 0) setChanged();
        return removed;
    }
}
