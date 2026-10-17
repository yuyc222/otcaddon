package com.automods.otcaddon.client;

import com.automods.otcaddon.OtcAddon;
import com.automods.otcaddon.registry.AddonEntities;
import com.automods.otcaddon.waiter.AllayWaiter;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.AllayRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * 悦灵服务员的渲染：直接借用原版悦灵渲染器，把模型和贴图整套拿来用。
 *
 * <p>服务员的实体不是生物，原版 {@link AllayRenderer} 只认 {@link Allay} 类型，所以这里养了
 * 一只"影子悦灵"：它只存在于客户端、永远不进世界，只用来给模型提供动画状态
 * （翅膀扇动用的是 tickCount，也就是它的年龄）。每帧把服务员实体的位置、朝向、
 * 年龄抄到影子上，再让原版渲染器照常画出来即可。这样贴图、翅膀动画、
 * 光照都和原版悦灵一模一样，将来原版改了模型也自动跟上。</p>
 */
@EventBusSubscriber(modid = OtcAddon.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AllayWaiterRenderer extends EntityRenderer<AllayWaiter> {
    /** 原版悦灵贴图，路径与 1.21.1 客户端资源一致。 */
    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/allay/allay.png");

    private final AllayRenderer delegate;
    /** 客户端专用影子悦灵：只提供动画状态，不加入世界。 */
    private RenderAllay shadow;

    /** 只借原版外观，不运行生物 AI；端物品进度由服务员自己的客户端 tick 维护。 */
    private static final class RenderAllay extends Allay {
        private AllayWaiter source;

        private RenderAllay(AllayWaiter source) {
            super(EntityType.ALLAY, source.level());
            this.source = source;
        }

        @Override
        public float getHoldingItemAnimationProgress(float partialTick) {
            return source.holdingProgress(partialTick);
        }
    }

    public AllayWaiterRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.delegate = new AllayRenderer(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(AllayWaiter entity) {
        return TEXTURE;
    }

    @Override
    public void render(AllayWaiter entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        Allay shadow = shadowOf(entity);
        syncShadow(shadow, entity);
        this.delegate.render(shadow, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private Allay shadowOf(AllayWaiter entity) {
        if (this.shadow == null || this.shadow.level() != entity.level()) {
            this.shadow = new RenderAllay(entity);
        }
        this.shadow.source = entity;
        return this.shadow;
    }

    /** 把服务员实体的位置、朝向和年龄抄给影子，让原版渲染器按同一套动画画出来。 */
    private static void syncShadow(Allay shadow, AllayWaiter source) {
        shadow.setPos(source.getX(), source.getY(), source.getZ());
        shadow.xOld = source.xOld;
        shadow.yOld = source.yOld;
        shadow.zOld = source.zOld;
        // 必须使用这只服务员上一 tick 的值，不能串用上一只被画出的悦灵的朝向。
        shadow.yRotO = source.yRotO;
        shadow.setYRot(source.getYRot());
        shadow.xRotO = source.xRotO;
        shadow.setXRot(source.getXRot());
        shadow.yBodyRotO = source.yRotO;
        shadow.yBodyRot = source.getYRot();
        shadow.yHeadRotO = source.yRotO;
        shadow.yHeadRot = source.getYRot();
        shadow.tickCount = source.tickCount + Math.floorMod(source.getId(), 20);
        shadow.setDeltaMovement(new Vec3(source.getX() - source.xOld,
                source.getY() - source.yOld, source.getZ() - source.zOld));
        shadow.setItemSlot(EquipmentSlot.MAINHAND, source.carriedItem());
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AddonEntities.ALLAY_WAITER.get(), AllayWaiterRenderer::new);
    }
}
