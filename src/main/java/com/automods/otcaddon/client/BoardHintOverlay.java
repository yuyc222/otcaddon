package com.automods.otcaddon.client;

import com.automods.otcaddon.OtcAddon;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * OrderToCook 界面上方的提示条（立牌菜单的"按住 Shift 点击 JEI 里的食物"，以及操作台
 * "缺料查询按钮"的收藏结果等）。
 *
 * <p>原来这些提示走的是 {@code LocalPlayer#displayClientMessage(..., true)}，也就是原版
 * 的“物品栏上方那行字”。那行字属于游戏 HUD，而 HUD 是在界面之前画的
 * （{@code Gui.render} 先跑，{@code ClientHooks.drawScreen} 后跑），所以只要立牌界面一打开，
 * 提示就被压在界面底下看不见了。</p>
 *
 * <p>改成挂在 {@link ScreenEvent.Render.Post} 上：这个事件在 NeoForge 里是在
 * {@code Screen.renderWithTooltip} 之后触发，也就是整帧里最靠上的一层——界面本体和它自己的
 * 物品提示（原版用 z=400 绘制）都画完了才轮到我们。这里再往上抬到
 * {@value #ABOVE_TOOLTIPS_Z}，保证提示一定盖在界面之上。</p>
 */
@EventBusSubscriber(modid = OtcAddon.MOD_ID, value = Dist.CLIENT)
public final class BoardHintOverlay {
    /** 普通反馈显示 3 秒。 */
    private static final long DEFAULT_MILLIS = 3000L;
    /** 界面打开时那条操作说明显示久一点，免得还没看清就没了。 */
    private static final long HINT_MILLIS = 6000L;
    /** 原版物品提示的 z 是 400，比它更大就一定在它上面。 */
    private static final float ABOVE_TOOLTIPS_Z = 500.0F;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    /** 原版 GUI 背景色（{@code Options.getBackgroundColor} 的默认值）。 */
    private static final int BACKDROP_COLOR = 0xC0101010;
    private static final int TOP_MARGIN = 4;
    private static final int LINE_HEIGHT = 11;

    private static Component message;
    private static long expiresAt;

    private BoardHintOverlay() {
    }

    /** 在屏幕最上层显示一行提示，几秒后自动消失。 */
    public static void show(Component text) {
        show(text, DEFAULT_MILLIS);
    }

    public static void show(Component text, long millis) {
        if (text == null) return;
        message = text;
        expiresAt = System.currentTimeMillis() + millis;
    }

    /** 界面刚打开时的那条操作说明。 */
    public static void showHint(Component text) {
        show(text, HINT_MILLIS);
    }

    public static void clear() {
        message = null;
        expiresAt = 0L;
    }

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (message == null) return;
        if (System.currentTimeMillis() > expiresAt) {
            message = null;
            return;
        }
        if (!BoardScreens.showsOverlay(event.getScreen())) return;

        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        if (font == null) return;
        GuiGraphics graphics = event.getGuiGraphics();
        int maxWidth = Math.max(60, graphics.guiWidth() - 24);
        List<FormattedCharSequence> lines = font.split(message, maxWidth);

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, ABOVE_TOOLTIPS_Z);
        int y = TOP_MARGIN;
        for (FormattedCharSequence line : lines) {
            int lineWidth = font.width(line);
            int left = (graphics.guiWidth() - lineWidth) / 2;
            graphics.fill(left - 2, y - 2, left + lineWidth + 2, y + 9 + 2, BACKDROP_COLOR);
            graphics.drawString(font, line, left, y, TEXT_COLOR, true);
            y += LINE_HEIGHT;
        }
        graphics.pose().popPose();
    }
}
