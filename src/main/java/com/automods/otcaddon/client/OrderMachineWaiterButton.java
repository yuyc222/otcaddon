package com.automods.otcaddon.client;

import com.automods.otcaddon.OtcAddon;
import com.automods.otcaddon.network.AddonNetwork;
import com.automods.otcaddon.waiter.WaiterManager;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * 在打单机界面右侧、「餐厅命名」按钮正下方加一个「悦灵服务员」按钮。
 *
 * <p>位置和本体那四个按钮对齐：本体用 {@code guiLeft + 176 + 8} 当左边、按钮宽 60 高 20，
 * 从上往下每格 24～30 像素。红框框的就是「餐厅命名」下面这块空地，这里放同一个 x、
 * 高 20 的按钮，间距也沿用本体的手感。</p>
 *
 * <p>点击后只发一个动作包给服务端，雇佣/解雇/扣币全在服务端判定
 * （见 {@code WaiterManager#handleAction}），客户端只负责显示结果。</p>
 */
@EventBusSubscriber(modid = OtcAddon.MOD_ID, value = Dist.CLIENT)
public final class OrderMachineWaiterButton {
    /** 界面底图宽度（{@code AbstractContainerScreen} 默认值，打单机用的就是这个）。 */
    private static final int IMAGE_WIDTH = 176;
    /** 按钮离界面右边缘的距离，和本体那四个按钮一致。 */
    private static final int SIDE_GAP = 8;
    /** 按钮纵向位置（相对界面左上角）：「餐厅命名」在 98，这里放它正下方。 */
    private static final int BUTTON_TOP = 126;
    /** 本体点「餐厅命名」后会弹出一个输入框（正好盖在这块位置），此时按钮临时下移这么多。 */
    private static final int RENAME_SHIFT = 26;
    private static final int BUTTON_WIDTH = 60;
    private static final int BUTTON_HEIGHT = 20;

    /** 每台打开的界面（按容器编号区分）当前的雇佣情况。 */
    private static final Map<Integer, Status> STATUSES = new ConcurrentHashMap<>();

    private OrderMachineWaiterButton() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!BoardScreens.isOrderMachine(event.getScreen())) return;
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        int x = screen.getGuiLeft() + IMAGE_WIDTH + SIDE_GAP;
        int y = screen.getGuiTop() + BUTTON_TOP;
        event.addListener(new WaiterButton(screen, x, y, screen.getMenu().containerId));
    }

    /** 服务端推来的雇佣情况：更新按钮文字、气泡，并弹一句操作结果。 */
    public static void accept(AddonNetwork.WaiterStatus payload) {
        STATUSES.put(payload.containerId(), new Status(payload.hired(), payload.max(), payload.nextCost()));
        Component message = messageFor(payload);
        if (message != null) {
            BoardHintOverlay.show(message);
        }
    }

    /** 结果码转提示语；纯粹的同步（打开界面时的第一次回包）不弹提示。 */
    private static Component messageFor(AddonNetwork.WaiterStatus payload) {
        return switch (payload.result()) {
            case WaiterManager.RESULT_HIRED -> Component.translatable("message.otcaddon.waiter.hired",
                    payload.hired(), payload.max());
            case WaiterManager.RESULT_MAX -> Component.translatable("message.otcaddon.waiter.max", payload.max());
            case WaiterManager.RESULT_NO_COIN -> Component.translatable("message.otcaddon.waiter.no_coin",
                    payload.nextCost());
            case WaiterManager.RESULT_LOCKED -> Component.translatable("message.otcaddon.waiter.locked");
            case WaiterManager.RESULT_UNAVAILABLE -> Component.translatable("message.otcaddon.waiter.unavailable");
            default -> null;
        };
    }

    /** 按钮上显示的「已雇 x / 上限 y」。 */
    private record Status(int hired, int max, int nextCost) {
    }

    /** 会随雇佣数量刷新文字和气泡的按钮。 */
    private static final class WaiterButton extends Button {
        private final Screen screen;
        private final int homeY;
        private final int containerId;
        private Status shown;
        private boolean applied;
        private boolean shifted;

        private WaiterButton(Screen screen, int x, int y, int containerId) {
            super(x, y, BUTTON_WIDTH, BUTTON_HEIGHT,
                    Component.translatable("screen.otcaddon.waiter.button"),
                    button -> AddonNetwork.sendWaiterAction(WaiterManager.ACTION_HIRE),
                    DEFAULT_NARRATION);
            this.screen = screen;
            this.homeY = y;
            this.containerId = containerId;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            Status status = STATUSES.get(this.containerId);
            if (!this.applied || status != this.shown) {
                this.shown = status;
                this.applied = true;
                apply(status);
            }
            moveForRenameField();
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
        }

        /** 本体点「餐厅命名」后会弹出一个盖住这块位置的输入框，此时把按钮临时挪到下面。 */
        private void moveForRenameField() {
            boolean renaming = false;
            for (GuiEventListener child : this.screen.children()) {
                if (child instanceof EditBox box && box.visible) {
                    renaming = true;
                    break;
                }
            }
            if (renaming == this.shifted) return;
            this.shifted = renaming;
            setY(renaming ? this.homeY + RENAME_SHIFT : this.homeY);
        }

        private void apply(Status status) {
            if (status == null) {
                setMessage(Component.translatable("screen.otcaddon.waiter.button"));
                setTooltip(Tooltip.create(Component.translatable("screen.otcaddon.waiter.tooltip.unknown")));
                this.active = true;
                return;
            }
            setMessage(Component.translatable("screen.otcaddon.waiter.button.count", status.hired(), status.max()));
            this.active = status.max() > status.hired();
            MutableComponent text = Component.empty()
                    .append(Component.translatable("screen.otcaddon.waiter.tooltip.limit", status.max(), status.hired()))
                    .append(Component.literal("\n"))
                    .append(status.hired() >= status.max()
                            ? Component.translatable("message.otcaddon.waiter.max", status.max())
                            : Component.translatable("screen.otcaddon.waiter.tooltip.cost", status.nextCost()))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("screen.otcaddon.waiter.tooltip.action"))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("screen.otcaddon.waiter.tooltip.duty"));
            setTooltip(Tooltip.create(text));
        }
    }
}
