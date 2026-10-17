package com.automods.otcaddon.mixin;

import com.automods.otcaddon.money.RestaurantMoney;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 精确改订单奖励出口，不改声望、统计、找零和其他发币。 */
@Mixin(targets = "cn.breezeth.ordertocook.item.TakeoutBagItem", remap = false)
public abstract class TakeoutRewardMixin {
    @Redirect(method = {"trySubmitDelivery", "settleDirectly"}, at = @At(value = "INVOKE",
            target = "Lcn/breezeth/ordertocook/util/CoinUtils;giveCoins(Lnet/minecraft/world/entity/player/Player;I)V"))
    private static void otcaddon$reward(Player payee, int amount, ServerLevel world, Player player,
                                       ItemStack stack, CompoundTag nbt) {
        RestaurantMoney.reward(world, payee, amount, nbt);
    }
    @Redirect(method = "completeDelivery", at = @At(value = "INVOKE",
            target = "Lcn/breezeth/ordertocook/util/CoinUtils;giveCoins(Lnet/minecraft/world/entity/player/Player;I)V"))
    private static void otcaddon$complete(Player payee, int amount, ServerLevel world, Player player,
                                         ItemStack stack, CompoundTag nbt, Entity npc) {
        RestaurantMoney.reward(world, payee, amount, nbt);
    }
}
