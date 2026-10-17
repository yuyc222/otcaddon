package cn.breezeth.ordertocook.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;

/** 仅编译期签名，运行时使用本体实现，不进入附属 jar。 */
public final class OrderNpcManager {
    public static BlockPos getCustomerPlateDisplayPos(LivingEntity npc) {
        throw new UnsupportedOperationException();
    }
    public record PendingBoomBonusData(String playerUuid, int machineId, boolean delivery,
            boolean longDistance, int deliveryDistance, boolean walkIn, String restaurantName,
            String customerName, int bonusCoin) { }
}
