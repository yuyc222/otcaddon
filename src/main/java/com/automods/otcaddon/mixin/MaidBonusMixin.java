package com.automods.otcaddon.mixin;

import cn.breezeth.ordertocook.core.OrderNpcManager.PendingBoomBonusData;
import com.automods.otcaddon.money.RestaurantMoney;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "cn.breezeth.ordertocook.core.OrderNpcManager", remap = false)
public abstract class MaidBonusMixin {
    @Shadow @Final private static ConcurrentHashMap<UUID, PendingBoomBonusData> pendingTouhouBoomBonuses;
    @Unique private static final ThreadLocal<Integer> otcaddon$machine = ThreadLocal.withInitial(() -> 0);
    @Inject(method = "grantPendingTouhouBoomBonus", at = @At("HEAD"))
    private static void otcaddon$begin(ServerLevel world, UUID uuid, CallbackInfo ci) {
        var data = pendingTouhouBoomBonuses.get(uuid);
        otcaddon$machine.set(data == null ? 0 : data.machineId());
    }
    @Redirect(method = "grantPendingTouhouBoomBonus", at = @At(value = "INVOKE",
            target = "Lcn/breezeth/ordertocook/util/CoinUtils;giveCoins(Lnet/minecraft/world/entity/player/Player;I)V"))
    private static void otcaddon$bonus(Player player, int amount, ServerLevel world, UUID uuid) {
        RestaurantMoney.rewardById(world, player, amount, otcaddon$machine.get());
    }
    @Inject(method = "grantPendingTouhouBoomBonus", at = @At("RETURN"))
    private static void otcaddon$end(ServerLevel world, UUID uuid, CallbackInfo ci) { otcaddon$machine.remove(); }
}
