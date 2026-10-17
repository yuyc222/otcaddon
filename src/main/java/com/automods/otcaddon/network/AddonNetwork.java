package com.automods.otcaddon.network;

import com.automods.otcaddon.OtcAddon;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class AddonNetwork {
    /** 服务员新增手持物品实体同步，拒绝旧客户端连接，避免同步字段不匹配。 */
    private static final String PROTOCOL_VERSION = "9";
    private static final ResourceLocation ADD_BOARD_FOOD_ID = ResourceLocation.fromNamespaceAndPath(OtcAddon.MOD_ID, "add_board_food");
    private static final ResourceLocation BOARD_FOOD_RESULT_ID = ResourceLocation.fromNamespaceAndPath(OtcAddon.MOD_ID, "board_food_result");
    private static final ResourceLocation CLEAR_PACKED_BOOKMARKS_ID = ResourceLocation.fromNamespaceAndPath(OtcAddon.MOD_ID, "clear_packed_bookmarks");
    private static final ResourceLocation WAITER_ACTION_ID = ResourceLocation.fromNamespaceAndPath(OtcAddon.MOD_ID, "waiter_action");
    private static final ResourceLocation WAITER_STATUS_ID = ResourceLocation.fromNamespaceAndPath(OtcAddon.MOD_ID, "waiter_status");

    /** OrderToCook 立牌菜单的菜单类与方块实体类（用类名比较，避免编译期硬依赖）。 */
    private static final String BOARD_HANDLER_CLASS = "cn.breezeth.ordertocook.screen.BoardScreenHandler";
    private static final String BOARD_ENTITY_CLASS = "cn.breezeth.ordertocook.block.entity.BoardBlockEntity";

    /** 添加结果，客户端据此在物品栏上方显示一行提示。 */
    public static final int RESULT_ADDED = 0;
    public static final int RESULT_EXISTS = 1;
    public static final int RESULT_NOT_FOOD = 2;
    public static final int RESULT_NOT_OPEN = 3;
    public static final int RESULT_FULL = 4;
    public static final int RESULT_UNAVAILABLE = 5;

    private AddonNetwork() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(AddonNetwork::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(BoardFoodResult.TYPE, BoardFoodResult.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> com.automods.otcaddon.client.BoardFoodFeedback.show(payload.status(), payload.itemId())));
        registrar.playToClient(ClearPackedBookmarks.TYPE, ClearPackedBookmarks.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> com.automods.otcaddon.client.MissingFoodBookmarks.clearPacked(payload.itemIds())));
        registrar.playToServer(AddBoardFood.TYPE, AddBoardFood.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (!(context.player() instanceof ServerPlayer player)) return;
                    int status = addFoodToOpenBoard(player, payload.itemId());
                    PacketDistributor.sendToPlayer(player, new BoardFoodResult(status, payload.itemId()));
                }));
        registrar.playToServer(WaiterAction.TYPE, WaiterAction.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (!(context.player() instanceof ServerPlayer player)) return;
                    com.automods.otcaddon.waiter.WaiterManager.handleAction(player, payload.action());
                }));
        registrar.playToClient(WaiterStatus.TYPE, WaiterStatus.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> com.automods.otcaddon.client.OrderMachineWaiterButton.accept(payload)));
    }

    /**
     * 把 JEI 里点到的那件食物加进玩家正打开的立牌菜单。
     *
     * <p>OrderToCook 的 BoardScreenHandler 暴露了 {@code getTemplates()}，立牌方块实体
     * 暴露了 {@code tryAddTemplate(ItemStack)}；这两个方法都是 public，但本附属没有对
     * OrderToCook 的编译期依赖，所以走反射调用。菜单没打开时一律拒绝，防止被伪造的
     * 网络包隔空改别人的立牌。</p>
     */
    private static int addFoodToOpenBoard(ServerPlayer player, ResourceLocation itemId) {
        if (player.containerMenu == null
                || !isTypeNamed(player.containerMenu.getClass(), BOARD_HANDLER_CLASS)) {
            OtcAddon.LOGGER.debug("玩家 {} 没有打开立牌菜单，拒绝添加 {}", player.getName().getString(), itemId);
            return RESULT_NOT_OPEN;
        }
        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item == null || item == Items.AIR) return RESULT_NOT_FOOD;
        ItemStack stack = new ItemStack(item);
        if (stack.get(DataComponents.FOOD) == null && stack.getFoodProperties(null) == null) {
            return RESULT_NOT_FOOD;
        }
        try {
            Object templates = invokeNoArg(player.containerMenu, "getTemplates");
            if (templates == null || !isTypeNamed(templates.getClass(), BOARD_ENTITY_CLASS)) {
                OtcAddon.LOGGER.debug("立牌菜单的容器类型不是 BoardBlockEntity，而是 {}",
                        templates == null ? "null" : templates.getClass().getName());
                return RESULT_UNAVAILABLE;
            }
            if (containsTemplate(templates, itemId)) return RESULT_EXISTS;
            boolean added = invokeItemMethod(templates, "tryAddTemplate", stack);
            OtcAddon.LOGGER.debug("立牌菜单添加 {} 的结果：{}", itemId, added ? "成功" : "失败（可能是菜单已满）");
            return added ? RESULT_ADDED : RESULT_FULL;
        } catch (ReflectiveOperationException error) {
            OtcAddon.LOGGER.warn("OrderToCook 立牌菜单接口不可用", error);
            return RESULT_UNAVAILABLE;
        }
    }

    /** 调用无参 public 方法；用 getMethod 而不是 getDeclaredMethod，继承来的实现也能找到。 */
    private static Object invokeNoArg(Object target, String name) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(name);
        return method.invoke(target);
    }

    private static boolean invokeItemMethod(Object target, String name, ItemStack stack)
            throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(name, ItemStack.class);
        return Boolean.TRUE.equals(method.invoke(target, stack));
    }

    /** 判断实际类型是否就是（或继承自）指定类名的类，避免 OrderToCook 用了子类就失配。 */
    private static boolean isTypeNamed(Class<?> type, String className) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (current.getName().equals(className)) return true;
        }
        return false;
    }

    /** 立牌菜单里已经有同 ID 的物品时，tryAddTemplate 会返回 false，这里提前区分开。 */
    private static boolean containsTemplate(Object templates, ResourceLocation itemId) {
        try {
            Method getItems = templates.getClass().getMethod("getItems");
            Object value = getItems.invoke(templates);
            if (!(value instanceof Iterable<?> iterable)) return false;
            for (Object element : iterable) {
                if (element instanceof ItemStack existing && !existing.isEmpty()
                        && itemId.equals(BuiltInRegistries.ITEM.getKey(existing.getItem()))) {
                    return true;
                }
            }
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
        return false;
    }

    public static void sendBoardFood(ResourceLocation itemId) {
        PacketDistributor.sendToServer(new AddBoardFood(itemId));
    }

    /** 打包／装盘成功后，服务端把这一单的菜品 ID 推给玩家，客户端据此撤掉本次添加的 JEI 收藏。 */
    public static void sendPackedBookmarks(ServerPlayer player, List<ResourceLocation> itemIds) {
        PacketDistributor.sendToPlayer(player, new ClearPackedBookmarks(List.copyOf(itemIds)));
    }

    /** 客户端点击「悦灵服务员」按钮：action 为雇佣或解雇。 */
    public static void sendWaiterAction(int action) {
        PacketDistributor.sendToServer(new WaiterAction(action));
    }

    /** 服务端把某台打单机的雇佣情况（以及本次操作结果）推给正在看界面的玩家。 */
    public static void sendWaiterStatus(ServerPlayer player, int containerId, int hired, int max, int nextCost, int result) {
        PacketDistributor.sendToPlayer(player, new WaiterStatus(containerId, hired, max, nextCost, result));
    }

    public record AddBoardFood(ResourceLocation itemId) implements CustomPacketPayload {
        public static final Type<AddBoardFood> TYPE = new Type<>(ADD_BOARD_FOOD_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, AddBoardFood> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, AddBoardFood::itemId, AddBoardFood::new);

        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 服务端把添加结果回传给点击的客户端，避免"点了没反应"时无从判断。 */
    public record BoardFoodResult(int status, ResourceLocation itemId) implements CustomPacketPayload {
        public static final Type<BoardFoodResult> TYPE = new Type<>(BOARD_FOOD_RESULT_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, BoardFoodResult> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, BoardFoodResult::status,
                ResourceLocation.STREAM_CODEC, BoardFoodResult::itemId,
                BoardFoodResult::new);

        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 打包／装盘成功的一单里都有哪些菜（物品 ID），客户端据此精确撤收藏。 */
    public record ClearPackedBookmarks(List<ResourceLocation> itemIds) implements CustomPacketPayload {
        public static final Type<ClearPackedBookmarks> TYPE = new Type<>(CLEAR_PACKED_BOOKMARKS_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, ClearPackedBookmarks> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.<io.netty.buffer.ByteBuf, ResourceLocation>list(64).apply(ResourceLocation.STREAM_CODEC),
                ClearPackedBookmarks::itemIds,
                ClearPackedBookmarks::new);

        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 客户端 → 服务端：雇佣或解雇一名悦灵服务员。 */
    public record WaiterAction(int action) implements CustomPacketPayload {
        public static final Type<WaiterAction> TYPE = new Type<>(WAITER_ACTION_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, WaiterAction> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, WaiterAction::action,
                WaiterAction::new);

        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * 服务端 → 客户端：某台打单机的悦灵服务员情况。
     *
     * @param containerId 玩家当时打开的容器编号，客户端靠它对上按钮
     * @param hired       已雇佣数量
     * @param max         上限（= 餐厅等级）
     * @param nextCost    再雇一名要多少 otc 币
     * @param result      本次操作结果码，见 {@code WaiterManager.RESULT_*}
     */
    public record WaiterStatus(int containerId, int hired, int max, int nextCost, int result)
            implements CustomPacketPayload {
        public static final Type<WaiterStatus> TYPE = new Type<>(WAITER_STATUS_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, WaiterStatus> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, WaiterStatus::containerId,
                ByteBufCodecs.VAR_INT, WaiterStatus::hired,
                ByteBufCodecs.VAR_INT, WaiterStatus::max,
                ByteBufCodecs.VAR_INT, WaiterStatus::nextCost,
                ByteBufCodecs.VAR_INT, WaiterStatus::result,
                WaiterStatus::new);

        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
