package cn.breezeth.ordertocook.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * OrderToCook 餐盘方块实体的编译期占位（compile-only stub），不会进 jar。
 *
 * <p>操作台装盘后餐盘会以方块形式出现在操作台正上方；顾客进餐时餐盘同样以这个方块实体
 * 摆在顾客面前。吃完以后里面的物品会变成「脏餐盘」。签名与 OrderToCook 1.3.7a 一致。</p>
 */
public class FoodPlateBlockEntity extends BlockEntity {
    public FoodPlateBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** 餐盘里当前装着的东西（成品餐盘或脏餐盘），空盘时是空物品。 */
    public ItemStack getPlateStack() {
        throw new UnsupportedOperationException("OrderToCook 兼容 API 编译期占位类不应在运行期被执行");
    }

    public void setPlateStack(ItemStack stack) {
        throw new UnsupportedOperationException("OrderToCook 兼容 API 编译期占位类不应在运行期被执行");
    }

    /** 顾客是否正在吃这盘菜（正在吃的时候不能端走，否则顾客会给差评）。 */
    public boolean isEatingSequenceActive() {
        throw new UnsupportedOperationException("OrderToCook 兼容 API 编译期占位类不应在运行期被执行");
    }
}
