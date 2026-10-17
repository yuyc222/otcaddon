package com.automods.otcaddon.mixin;

import com.automods.otcaddon.money.RestaurantMoney;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "cn.breezeth.ordertocook.entity.CustomerEntity", remap = false)
public abstract class CustomerBonusMixin {
    @Shadow private int pendingBoomMachineId;
    @Redirect(method = "grantPendingBoomBonus", at = @At(value = "INVOKE",
            target = "Lcn/breezeth/ordertocook/util/CoinUtils;giveCoins(Lnet/minecraft/world/entity/player/Player;I)V"))
    private void otcaddon$bonus(Player player, int amount, ServerLevel world) {
        RestaurantMoney.rewardById(world, player, amount, pendingBoomMachineId);
    }
}
