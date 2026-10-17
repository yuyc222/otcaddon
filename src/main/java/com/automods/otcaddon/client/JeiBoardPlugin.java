package com.automods.otcaddon.client;

import com.automods.otcaddon.OtcAddon;
import com.automods.otcaddon.network.AddonNetwork;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IBookmarkManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * JEI 插件：负责两类跟 JEI 打交道的事。
 *
 * <p>一是按住 Shift 点击 JEI 里的食物，把该食物加入当前打开的立牌菜单。</p>
 *
 * <p>二是操作台"缺料查询按钮"要用的 JEI 收藏（bookmark）读写：把缺少的菜品钉进收藏列表，
 * 打包／装盘成功后再撤掉。收藏 API 是 JEI 官方的 {@code IJeiRuntime#getBookmarkManager()}，
 * 不用反射、不碰内部实现。</p>
 *
 * <p>要点一：{@code IIngredientListOverlay#getIngredientUnderMouse()} 返回的是
 * {@code Optional<ITypedIngredient<?>>}，必须先用 {@code getItemStack()} 取出物品，
 * 不能直接当成 {@link ItemStack} 使用（那样判定永远为 false，功能会静默失效）。</p>
 *
 * <p>要点二（曾经的致命 bug）：JEI 是通过 {@code Class#getDeclaredConstructor().newInstance()}
 * 实例化 {@code @JeiPlugin} 类的，而且<strong>不会</strong>调用 {@code setAccessible(true)}。
 * 如果构造器是 private，这一步会抛 {@code IllegalAccessException}，JEI 把整个插件当成
 * “Failed to load”直接跳过——于是 {@link #onRuntimeAvailable} 永远不执行、runtime 永远是 null，
 * 点击时在第一道判断就静默 return，表现为“功能完全没有实现、连提示都没有”。
 * 所以这里的无参构造器<strong>必须是 public</strong>。</p>
 */
@JeiPlugin
@EventBusSubscriber(modid = OtcAddon.MOD_ID, value = Dist.CLIENT)
public final class JeiBoardPlugin implements IModPlugin {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(OtcAddon.MOD_ID, "jei");
    private static volatile IJeiRuntime runtime;

    public JeiBoardPlugin() {
    }

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        // 把收藏读写实现挂给 JEI 无关的记账类；它自己不引用 JEI 类型，没装 JEI 也能安全加载。
        MissingFoodBookmarks.onJeiRuntimeAvailable(new MissingFoodBookmarks.BookmarkAccess() {
            @Override
            public List<ResourceLocation> add(List<ResourceLocation> itemIds) {
                return addFoodBookmarks(itemIds);
            }

            @Override
            public void remove(List<ResourceLocation> itemIds) {
                removeFoodBookmarks(itemIds);
            }
        });
        OtcAddon.LOGGER.info("JEI 立牌食物插件已就绪：按住 Shift 点击 JEI 里的食物即可加入立牌菜单");
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
        MissingFoodBookmarks.onJeiRuntimeUnavailable();
    }

    /** 优先级设为最高，抢在 JEI 自己处理点击（打开配方界面）之前拦截这次 Shift+左键。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 0) return;
        if (!Screen.hasShiftDown()) return;
        if (!BoardScreens.isBoard(event.getScreen())) return;
        IJeiRuntime jei = runtime();
        if (jei == null) {
            OtcAddon.LOGGER.debug("JEI 运行时尚未就绪，忽略本次 Shift+点击");
            return;
        }
        ItemStack stack = jei.getIngredientListOverlay().getIngredientUnderMouse()
                .flatMap(ITypedIngredient::getItemStack)
                .orElse(ItemStack.EMPTY);
        if (stack.isEmpty() || !isFood(stack)) {
            OtcAddon.LOGGER.debug("Shift+点击的位置没有取到 JEI 里的食物，忽略");
            return;
        }
        AddonNetwork.sendBoardFood(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        // 先给一行即时反馈，服务端处理完还会推一条最终结果覆盖它。
        // 走 BoardHintOverlay 而不是原版动作栏，否则这行字会被立牌界面本身挡住。
        BoardHintOverlay.show(Component.translatable("message.otcaddon.board.sending", stack.getHoverName()));
        event.setCanceled(true);
    }

    /** 打开立牌界面时提示一次操作方法，免得“不知道怎么触发”被误当成“功能没做”。 */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!BoardScreens.isBoard(event.getScreen())) return;
        BoardHintOverlay.showHint(Component.translatable("message.otcaddon.board.hint"));
    }

    /**
     * 取 JEI 运行时：优先用插件回调里拿到的实例；万一没有，再用反射走 JEI 自己的
     * {@code Internal.getJeiRuntime()} 兜底（此时 JEI 若尚未启动完成会返回 null，属正常）。
     */
    private static IJeiRuntime runtime() {
        IJeiRuntime local = runtime;
        if (local != null) return local;
        try {
            Class<?> internal = Class.forName("mezz.jei.common.Internal");
            Object value = internal.getMethod("getJeiRuntime").invoke(null);
            return value instanceof IJeiRuntime found ? found : null;
        } catch (InvocationTargetException error) {
            return null;
        } catch (ReflectiveOperationException | LinkageError error) {
            OtcAddon.LOGGER.debug("反射获取 JEI 运行时失败", error);
            return null;
        }
    }

    private static boolean isFood(ItemStack stack) {
        return stack.get(DataComponents.FOOD) != null || stack.getFoodProperties(null) != null;
    }

    /**
     * 把给定的菜品钉进 JEI 收藏列表，返回"确实由这次操作加进去"的物品 ID。
     *
     * <p>已经在收藏里的（多半是玩家自己钉的）直接跳过、也不上账——后面撤除时不能动玩家的收藏。</p>
     */
    static List<ResourceLocation> addFoodBookmarks(List<ResourceLocation> itemIds) {
        IJeiRuntime jei = runtime();
        if (jei == null) return List.of();
        IBookmarkManager bookmarks = jei.getBookmarkManager();
        List<ResourceLocation> added = new ArrayList<>();
        for (ResourceLocation itemId : itemIds) {
            ITypedIngredient<ItemStack> typed = typedIngredient(jei, itemId);
            if (typed == null || bookmarks.contains(typed)) continue;
            if (bookmarks.add(typed)) added.add(itemId);
        }
        return added;
    }

    /** 从 JEI 收藏里撤掉指定的菜品；本来就不在收藏里的会被 JEI 忽略。 */
    static void removeFoodBookmarks(List<ResourceLocation> itemIds) {
        IJeiRuntime jei = runtime();
        if (jei == null) return;
        IBookmarkManager bookmarks = jei.getBookmarkManager();
        for (ResourceLocation itemId : itemIds) {
            ITypedIngredient<ItemStack> typed = typedIngredient(jei, itemId);
            if (typed != null) bookmarks.remove(typed);
        }
    }

    /** 把物品 ID 变成 JEI 认识的原料对象；JEI 没收录这件物品时返回 null。 */
    private static ITypedIngredient<ItemStack> typedIngredient(IJeiRuntime jei, ResourceLocation itemId) {
        if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) return null;
        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item == null || item == Items.AIR) return null;
        // 用三参重载：它才是 JEI 19.57 起保留的正式接口，两参那版已标记"将被移除"。
        // 最后一个参数传 false，与旧两参版本的默认行为一致（收藏由 JEI 自己在写入时归一化）。
        return jei.getIngredientManager()
                .createTypedIngredient(VanillaTypes.ITEM_STACK, new ItemStack(item), false)
                .orElse(null);
    }
}
