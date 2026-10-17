package cn.breezeth.ordertocook.screen;

import cn.breezeth.ordertocook.block.entity.OrderMachineBlockEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

/**
 * OrderToCook 打单机界面容器的编译期占位（compile-only stub），不会进 jar。
 *
 * <p>雇佣悦灵服务员时，服务端就是拿玩家「当前打开的容器」对号入座找到对应打单机的，
 * 这样客户端不必（也无法）上报坐标，伪造的包也改不了别人家的餐厅。</p>
 */
public abstract class OrderMachineScreenHandler extends AbstractContainerMenu {
    protected OrderMachineScreenHandler(MenuType<?> menuType, int containerId) {
        super(menuType, containerId);
    }

    /** 服务端才有值：返回这台打单机的方块实体。 */
    public OrderMachineBlockEntity getMachineIfServer() {
        throw new UnsupportedOperationException("OrderToCook 兼容 API 编译期占位类不应在运行期被执行");
    }
}
