package cn.breezeth.ordertocook.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * OrderToCook 打包袋方块实体的编译期占位（compile-only stub），不会进 jar。
 *
 * <p>操作台打包成功后，打包袋会以方块形式出现在操作台正上方，成品就存在这个方块实体里。
 * 签名与 OrderToCook 1.3.7a 一致。</p>
 */
public class TakeoutBagBlockEntity extends BlockEntity {
    public TakeoutBagBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public ItemStack getBagStack() {
        throw new UnsupportedOperationException("OrderToCook 兼容 API 编译期占位类不应在运行期被执行");
    }

    public void setBagStack(ItemStack stack) {
        throw new UnsupportedOperationException("OrderToCook 兼容 API 编译期占位类不应在运行期被执行");
    }
}
