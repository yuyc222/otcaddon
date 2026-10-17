package com.automods.otcaddon.mixin;

import cn.breezeth.ordertocook.block.entity.OrderMachineBlockEntity;
import com.automods.otcaddon.money.RestaurantMoney;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "cn.breezeth.ordertocook.block.entity.OrderMachineBlockEntity", remap = false)
public abstract class MachinePaymentMixin {
    @Redirect(method = {"tryUpgrade", "tryRename"}, at = @At(value = "INVOKE",
            target = "Lcn/breezeth/ordertocook/util/CoinUtils;tryConsumeWithChange(Lnet/minecraft/world/entity/player/Player;I)Z"))
    private boolean otcaddon$pay(Player player, int amount) {
        return RestaurantMoney.consume((OrderMachineBlockEntity) (Object) this, player, amount);
    }
}
