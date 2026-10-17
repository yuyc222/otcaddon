package com.automods.otcaddon.client;

import com.automods.otcaddon.OtcAddon;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * 认出"点击操作台界面右上角那个缺料查询按钮"这一下，把当前订单缺少的菜品加进 JEI 收藏。
 *
 * <p>那个按钮是 OrderToCook 本体自带的，本附属没有它的编译期依赖，改不了也拦不到它的回调，
 * 所以做法是"在旁边听"：在鼠标按下的最早时机，判断当前界面是不是操作台界面、点有没有落在
 * 按钮的 16×16 方框里。坐标取自本体 {@code TakeoutBoxScreen} 的源码常量
 * （相对界面左上角 X+156、Y+4），这样不依赖本体暴露任何接口。</p>
 *
 * <p>缺哪些菜不用另开通信包：本体的 {@code TakeoutBoxScreenHandler} 已经把缺料清单同步进了
 * 客户端数据槽（{@code getMissingIngredientCount / Id / Required / Available}），
 * 这里按方法名反射读出来即可。</p>
 *
 * <p>点击事件<strong>不取消</strong>：本体按钮照常把查询请求发给服务端，聊天栏仍会显示查询结果，
 * 我们只是顺手多做一件事。</p>
 */
@EventBusSubscriber(modid = OtcAddon.MOD_ID, value = Dist.CLIENT)
public final class TakeoutQueryBookmarks {
    /** 与本体源码一致：按钮位置和大小（相对界面左上角）。 */
    private static final int QUERY_BUTTON_X = 156;
    private static final int QUERY_BUTTON_Y = 4;
    private static final int QUERY_BUTTON_SIZE = 16;
    /** 读不到界面偏移时的兜底：本体操作台背景图是 176×166（AbstractContainerScreen 默认值）。 */
    private static final int FALLBACK_IMAGE_WIDTH = 176;
    private static final int FALLBACK_IMAGE_HEIGHT = 166;
    /** 本体最多同步 6 条缺料记录（propertyDelegate 一共 4 + 6×3 格）。 */
    private static final int MAX_MISSING = 6;

    private TakeoutQueryBookmarks() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 0) return;
        Screen screen = event.getScreen();
        if (!BoardScreens.isTakeoutBox(screen)) return;

        int left = guiLeft(screen);
        int top = guiTop(screen);
        double x = event.getMouseX();
        double y = event.getMouseY();
        if (x < left + QUERY_BUTTON_X || x >= left + QUERY_BUTTON_X + QUERY_BUTTON_SIZE) return;
        if (y < top + QUERY_BUTTON_Y || y >= top + QUERY_BUTTON_Y + QUERY_BUTTON_SIZE) return;

        List<ResourceLocation> missing = readMissing(screen);
        if (missing.isEmpty()) return;
        MissingFoodBookmarks.addMissing(missing);
    }

    /** 打开操作台界面时提示一次这个功能，免得"根本不知道按钮还能这么用"。 */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!BoardScreens.isTakeoutBox(event.getScreen())) return;
        if (!MissingFoodBookmarks.jeiReady()) return;
        BoardHintOverlay.showHint(Component.translatable("message.otcaddon.query.hint"));
    }

    /** 退出世界时清空"本次添加"名单，见 {@link MissingFoodBookmarks#reset()}。 */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        MissingFoodBookmarks.reset();
    }

    /** 从菜单同步过来的数据槽里读出缺料清单（物品 ID）。 */
    private static List<ResourceLocation> readMissing(Screen screen) {
        try {
            Object menu = screen.getClass().getMethod("getMenu").invoke(screen);
            if (menu == null) return List.of();
            Class<?> menuType = menu.getClass();
            int count = (Integer) menuType.getMethod("getMissingIngredientCount").invoke(menu);
            int limit = Math.max(0, Math.min(MAX_MISSING, count));
            if (limit == 0) return List.of();
            List<ResourceLocation> ids = new ArrayList<>(limit);
            for (int index = 0; index < limit; index++) {
                int rawId = (Integer) menuType.getMethod("getMissingIngredientId", int.class).invoke(menu, index);
                // byId 对无效编号会回退到默认值（空气），这里统一按空气过滤掉。
                Item item = BuiltInRegistries.ITEM.byId(rawId);
                if (item == null || item == Items.AIR) continue;
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                if (id != null) ids.add(id);
            }
            return ids;
        } catch (ReflectiveOperationException | LinkageError error) {
            OtcAddon.LOGGER.debug("读取操作台缺料数据失败（Order To Cook 版本可能不匹配）", error);
            return List.of();
        }
    }

    /** 界面左上角坐标；本体和原版都提供 public 的 getGuiLeft/getGuiTop，取不到才退回估算。 */
    private static int guiLeft(Screen screen) {
        try {
            return (Integer) screen.getClass().getMethod("getGuiLeft").invoke(screen);
        } catch (ReflectiveOperationException | LinkageError error) {
            return (screen.width - FALLBACK_IMAGE_WIDTH) / 2;
        }
    }

    private static int guiTop(Screen screen) {
        try {
            return (Integer) screen.getClass().getMethod("getGuiTop").invoke(screen);
        } catch (ReflectiveOperationException | LinkageError error) {
            return (screen.height - FALLBACK_IMAGE_HEIGHT) / 2;
        }
    }
}
