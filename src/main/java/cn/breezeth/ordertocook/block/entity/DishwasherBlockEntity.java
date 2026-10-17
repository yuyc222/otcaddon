package cn.breezeth.ordertocook.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * OrderToCook 洗碗机方块实体的编译期占位（compile-only stub），不会进 jar。
 *
 * <p>本体洗碗机只认「玩家手上/背包里的脏餐盘」：{@link #insertDirtyPlates(Player)} 会把玩家背包里
 * 最多 16 个脏餐盘收进机器开始清洗。悦灵服务员使用独立临时背包调用此接口，
 * 不会挪动老板背包里的物品。签名与 OrderToCook 1.3.7a 一致。</p>
 */
public class DishwasherBlockEntity extends BlockEntity {
    public DishwasherBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** 机器现在能不能收脏餐盘（正在洗或正在出盘时不能）。 */
    public boolean canInsertDirtyPlates() {
        throw new UnsupportedOperationException("OrderToCook 兼容 API 编译期占位类不应在运行期被执行");
    }

    /** 把玩家背包里的脏餐盘收进机器开始清洗。 */
    public void insertDirtyPlates(Player player) {
        throw new UnsupportedOperationException("OrderToCook 兼容 API 编译期占位类不应在运行期被执行");
    }

    public boolean canTakeCleanPlates() { throw new UnsupportedOperationException(); }
    public void takeCleanPlates(Player player) { throw new UnsupportedOperationException(); }

    /** 机器里现在存着几个盘子。 */
    public int getStoredPlates() {
        throw new UnsupportedOperationException("OrderToCook 兼容 API 编译期占位类不应在运行期被执行");
    }
}
