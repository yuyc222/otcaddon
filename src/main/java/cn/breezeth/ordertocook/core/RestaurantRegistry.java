package cn.breezeth.ordertocook.core;
import cn.breezeth.ordertocook.block.entity.OrderMachineBlockEntity;
import net.minecraft.server.level.ServerLevel;
/** 本体编译期占位，不进入 jar。 */
public final class RestaurantRegistry {
    public static OrderMachineBlockEntity.RestaurantStats getStatsById(ServerLevel world, int id) {
        throw new UnsupportedOperationException();
    }
}
