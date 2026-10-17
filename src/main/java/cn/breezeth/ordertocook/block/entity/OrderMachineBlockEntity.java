package cn.breezeth.ordertocook.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * OrderToCook 打单机方块实体的编译期占位（compile-only stub），不会进 jar。
 *
 * <p>本附属只用到两件事：餐厅当前等级（决定悦灵服务员上限），以及餐厅老板的名字
 * （决定奖励发给谁）。签名与 OrderToCook 1.3.7a 一致。</p>
 */
public class OrderMachineBlockEntity extends BlockEntity {
    public OrderMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** 餐厅等级，0～8。 */
    public int getRestaurantLevel() {
        throw new UnsupportedOperationException("OrderToCook 兼容 API 编译期占位类不应在运行期被执行");
    }

    /** 餐厅统计快照，含老板名字与等级。 */
    public RestaurantStats snapshotStats() {
        throw new UnsupportedOperationException("OrderToCook 兼容 API 编译期占位类不应在运行期被执行");
    }

    /** 与本体同名的统计记录，字段顺序按本体源码排列。 */
    public record RestaurantStats(String dimension, long posLong, String name, String owner, int level,
                                  int accepted, int delivery, int longDistance, int totalProfit, int deliveryProfit,
                                  int maxDeliveryDist, int walkIn) {
    }
}
