package com.automods.otcaddon.client;

import com.automods.otcaddon.AddonConfig;
import com.automods.otcaddon.OtcAddon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 指路箭头「在哪画」：在水和玻璃都画完之后（{@link RenderLevelStageEvent.Stage#AFTER_TRANSLUCENT_BLOCKS}）
 * 往世界坐标里画一个悬浮在空中的发光线框箭头。
 *
 * <p>位置规则：箭头永远飘在玩家眼前约 2 格处——朝目标的方向、比眼睛低
 * {@link AddonConfig#ARROW_DROP_BELOW_EYE} 格（游戏内配置，默认 0.5）。压低一点是必须的：
 * 箭头是<b>躺平</b>的，要是正好和眼睛齐平，视线就和薄板在一个平面里，只会看到一条侧棱。
 * 因为画在世界坐标里，它会被方块正常遮挡，也不会和水、玻璃抢深度。</p>
 *
 * <p>朝向规则：箭头躺平，在水平方向上指着目标的真实 x/z（像一枚平放的指南针）；翘起多少不是
 * 写死的，而是每帧按<b>真实高度差</b>算出来的俯仰角：玩家脚底和目标方块在同一高度时俯仰角
 * 就是 0，箭头完全躺平；目标在楼上就往上抬、在脚下就往下压，高度差得越多、水平离得越近，翘得
 * 越明显。主手拿着 Order To Cook 打包好的外卖袋时指向订单配送点，判定见 {@link DeliveryTarget}。
 * 具体怎么画见 {@link DeliveryArrowRenderer}。</p>
 */
@EventBusSubscriber(modid = OtcAddon.MOD_ID, value = Dist.CLIENT)
public final class DeliveryArrowOverlay {

    /** 离目标这么近（约 1.5 格，含高度）就不再画箭头，免得贴脸挡视线。 */
    private static final double ARRIVED_DISTANCE_SQ = 1.5 * 1.5;

    /** 箭头悬在离玩家眼睛多远的位置（格），这正是「始终距离 2 格」。 */
    private static final double FLOAT_DISTANCE = 2.0;

    /** 目标几乎就在正上/正下方时，水平方向没有意义，改用玩家面朝的方向摆箭头。 */
    private static final double MIN_HORIZONTAL = 0.05;

    private DeliveryArrowOverlay() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.screen != null) {
            return;
        }

        DeliveryTarget target = DeliveryTarget.heldBy(player);
        if (target == null) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 eye = player.getEyePosition(partialTick);

        // 目标方块的中心。y 也参与，箭头才能真的往上或往下指。
        double targetX = target.x() + 0.5;
        double targetY = target.y() + 0.5;
        double targetZ = target.z() + 0.5;

        double dx = targetX - eye.x;
        double dy = targetY - eye.y;
        double dz = targetZ - eye.z;
        if (dx * dx + dy * dy + dz * dz < ARRIVED_DISTANCE_SQ) {
            return;
        }

        // 悬停位置：沿「玩家 -> 目标」的水平方向往前 2 格，高度比眼睛低一点。
        // 只用水平方向是为了不管目标在高处还是低处，箭头都稳稳地飘在眼前，
        // 而不是一头扎进地里或者飞到头顶上——高低差交给箭头的俯仰去表达。
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        // 真实俯仰角：只看高度差和水平距离，跟相机朝哪、目标偏左偏右都无关。
        // 高度差必须用连续坐标算，而且要和上面的 eye 一样带帧间插值：眼睛高度减回去就是
        // 玩家脚底那一层连续的 y。玩家脚底和目标方块在同一高度时差就是 0，俯仰角自然为 0，
        // 箭头完全躺平；目标在上方为正、下方为负。
        // 以前拿脚下方块的整数 y（blockPosition().getY()）去减，脚一跨格 y 就整跳 1，
        // 角度跟着「啪」地跳一格；连续坐标是走多少抬多少，上台阶也一路平滑。
        double playerFeetY = eye.y - player.getEyeHeight();
        double heightDifference = target.y() - playerFeetY;
        double pitch = Math.atan2(heightDifference, horizontal);

        double placeDirX;
        double placeDirZ;
        if (horizontal < MIN_HORIZONTAL) {
            Vec3 look = player.getLookAngle();
            placeDirX = look.x;
            placeDirZ = look.z;
            double lookLength = Math.sqrt(placeDirX * placeDirX + placeDirZ * placeDirZ);
            if (lookLength < MIN_HORIZONTAL) {
                return;
            }
            placeDirX /= lookLength;
            placeDirZ /= lookLength;
        } else {
            placeDirX = dx / horizontal;
            placeDirZ = dz / horizontal;
        }

        double anchorX = eye.x + placeDirX * FLOAT_DISTANCE;
        double anchorY = eye.y - AddonConfig.ARROW_DROP_BELOW_EYE.get();
        double anchorZ = eye.z + placeDirZ * FLOAT_DISTANCE;

        // 屏幕上的 1 像素对应多少世界尺寸：由当前投影矩阵的纵向缩放和画布高度决定。
        // 「深度 1」处 1 像素 = 2 / (纵向缩放 × 高度)，再乘上棱到镜头的深度就是它的实际像素宽度。
        // 这样不管窗口多大、视野设成多少，箭头的笔画都稳定是 1 像素。
        Matrix4f projection = event.getProjectionMatrix();
        int framebufferHeight = Minecraft.getInstance().getMainRenderTarget().height;
        float pixelWorldScale = framebufferHeight > 0
                ? 2.0F / (projection.m11() * framebufferHeight)
                : 0.0F;

        Vector3f look = event.getCamera().getLookVector();
        DeliveryArrowRenderer.render(event.getModelViewMatrix(), event.getCamera().getPosition(),
                new Vec3(look.x, look.y, look.z), pixelWorldScale, pitch,
                anchorX, anchorY, anchorZ, targetX, targetY, targetZ);
    }
}
