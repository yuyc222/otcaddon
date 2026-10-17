package cn.breezeth.ordertocook.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * OrderToCook 打包袋／餐盘的编译期占位（compile-only stub），不会进 jar。
 *
 * <p>只声明「把成品交给某位顾客」这一个静态入口：本体自己的
 * {@code FoodPlateItem.interactLivingEntity} 与打包袋右键走的都是它。
 * 传进来的 {@code stack} 是打包袋或餐盘，方法内部会自动区分（餐盘会顺手摆到顾客面前的餐盘位上），
 * 结算、发币、播放动画、送走顾客都在本体里完成。</p>
 */
public class TakeoutBagItem extends Item {
    public TakeoutBagItem(Item.Properties properties) {
        super(properties);
    }

    /**
     * 把一份成品交给指定顾客。
     *
     * @param world         服务端世界
     * @param player        结算奖励的归属玩家（餐厅老板）
     * @param stack         打包袋或餐盘
     * @param interacted    目标顾客实体
     * @param submitSound   交付音效，可为 null
     * @return SUCCESS 表示已成功交付；PASS 表示这份成品不属于该顾客（或已过期）
     */
    public static InteractionResult trySubmitDineInFromEntityUse(ServerLevel world, Player player, ItemStack stack,
                                                                 Entity interacted, SoundEvent submitSound) {
        throw new UnsupportedOperationException("OrderToCook 兼容 API 编译期占位类不应在运行期被执行");
    }
}
