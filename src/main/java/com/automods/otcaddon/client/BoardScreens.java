package com.automods.otcaddon.client;

import net.minecraft.client.gui.screens.Screen;

/**
 * 判断某个界面是不是 OrderToCook 的界面（立牌 BoardScreen / 操作台 TakeoutBoxScreen）。
 *
 * <p>本附属对 OrderToCook 没有编译期依赖，所以只能按类名逐层往上比对父类，
 * 这样 OrderToCook 换了子类也不会失配。</p>
 */
final class BoardScreens {
    private static final String BOARD_SCREEN_CLASS = "cn.breezeth.ordertocook.screen.BoardScreen";
    private static final String TAKEOUT_BOX_SCREEN_CLASS = "cn.breezeth.ordertocook.screen.TakeoutBoxScreen";
    private static final String ORDER_MACHINE_SCREEN_CLASS = "cn.breezeth.ordertocook.screen.OrderMachineScreen";

    private BoardScreens() {
    }

    static boolean isBoard(Screen screen) {
        return isTypeNamed(screen, BOARD_SCREEN_CLASS);
    }

    /** 操作台界面：打包、装盘和"缺料查询"按钮都在这里。 */
    static boolean isTakeoutBox(Screen screen) {
        return isTypeNamed(screen, TAKEOUT_BOX_SCREEN_CLASS);
    }

    /** 打单机界面：升级、营业、排行、餐厅命名和新加的"悦灵服务员"按钮都在这里。 */
    static boolean isOrderMachine(Screen screen) {
        return isTypeNamed(screen, ORDER_MACHINE_SCREEN_CLASS);
    }

    /** 这三类界面上都要用最上层的提示条，否则提示会被界面本体盖住。 */
    static boolean showsOverlay(Screen screen) {
        return isBoard(screen) || isTakeoutBox(screen) || isOrderMachine(screen);
    }

    private static boolean isTypeNamed(Screen screen, String className) {
        if (screen == null) return false;
        for (Class<?> type = screen.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals(className)) return true;
        }
        return false;
    }
}
