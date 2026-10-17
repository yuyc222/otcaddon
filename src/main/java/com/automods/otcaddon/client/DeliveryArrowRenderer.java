package com.automods.otcaddon.client;

import com.automods.otcaddon.AddonConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/**
 * 悬浮导航箭头的绘制：把箭头当成一块有厚度的立体薄板，只画棱线——上表面一圈、下表面一圈、
 * 每个角一条竖棱，每根棱都摊成一条正对镜头的发光细带。
 *
 * <p>画法用的是游戏里末影龙死亡光柱那套：自己开一条 16 KB 的顶点缓冲
 * （{@link ByteBufferBuilder}），包一层 {@link MultiBufferSource}，材质取
 * {@link RenderType#dragonRays()}——纯顶点色 + 加法混合，颜色直接写在每个顶点上，
 * 所以笔画交叠的地方会自己变亮，看着就是发光的。全部线段共用这一条缓冲，最后一次性提交。</p>
 *
 * <p><b>朝向</b>：箭头是<b>躺平</b>的——薄板整个躺在水平面里，薄板法线（本地 +y）就是世界上方，
 * 本地 +z 指向目标的水平方向。所以它像一枚平放的指南针，而不是立在你面前的一块牌子。
 * 俯仰角每帧由两点的<b>真实高度差</b>算出来：同一层时是 0（箭头完全躺平），目标越高、
 * 尖端越往上抬，越低则越往下压。</p>
 *
 * <p>坐标：顶点给的是「相机相对坐标」（世界坐标减去相机位置），和同一时刻的方块、实体
 * 用的是同一套空间，因此会被地形正常遮挡。模型视图矩阵由渲染阶段现设，见 {@link #render}。</p>
 */
final class DeliveryArrowRenderer {

    /** 顶点缓冲大小：16 KB，一帧里所有线段都塞进这一条缓冲。 */
    private static final int BUFFER_BYTES = 16 * 1024;

    /** 整体缩放：轮廓原始单位里箭头全长 1.77，乘 0.31 后约 0.55 格（原来是 0.62，这轮缩了一半）。 */
    private static final float SCALE = 0.31F;

    /** 薄板厚度（方块）：箭头整体缩了一半，厚度跟着按比例缩，还剩约 8.5 厘米。 */
    private static final float THICKNESS = 0.085F;

    /** 笔画宽度（屏幕像素）：不管箭头离镜头多远，发光细带都保持 1 像素宽。 */
    private static final float LINE_WIDTH_PIXELS = 1.0F;

    /**
     * 箭头轮廓（本地坐标：x 向右、z 指向目标、y 向上），从尖端开始逆时针绕一圈。
     * 两个 {@code z=0.30} 的顶点就是「凹进去」的缺口，是导航箭头好认的关键。
     */
    private static final float[][] OUTLINE = {
            {0.00F, 1.15F},   // 尖端
            {0.70F, 0.02F},   // 右外角
            {0.24F, 0.30F},   // 右内凹
            {0.24F, -0.62F},  // 右尾
            {-0.24F, -0.62F}, // 左尾
            {-0.24F, 0.30F},  // 左内凹
            {-0.70F, 0.02F},  // 左外角
    };

    /** 上表面棱线的顶点色（R,G,B,A）；加法混合里 alpha 就是亮度。 */
    private static final float[] TOP_COLOR = {1.00F, 0.88F, 0.42F, 0.85F};
    /** 下表面棱线：压暗一点，和上表面拉开层次。 */
    private static final float[] BOTTOM_COLOR = {1.00F, 0.66F, 0.16F, 0.55F};
    /** 竖棱：介于两者之间，把上下两圈连起来。 */
    private static final float[] SIDE_COLOR = {1.00F, 0.78F, 0.30F, 0.70F};

    private DeliveryArrowRenderer() {
    }

    /**
     * 画一帧箭头。
     *
     * @param viewMatrix      这一帧的模型视图矩阵（只有相机朝向，没有平移）：渲染阶段给的就是它，
     *                        方块和实体用的也是同一个
     * @param cameraPos       相机位置，顶点要用「世界坐标减去它」
     * @param lookDirection   相机的视线方向（世界轴向上的单位向量），用来算每条棱离镜头多深
     * @param pixelWorldScale 「深度 1」处屏幕 1 像素对应多少世界尺寸（= 2 / (投影矩阵纵向缩放 × 帧缓冲高度)）
     * @param pitch           真实俯仰角（弧度）：atan2(高度差, 水平距离)，同一层为 0（箭头躺平）
     * @param anchorX         箭头中心的世界坐标
     * @param targetX         目标点的世界坐标（含高度），箭头就朝它指
     */
    static void render(Matrix4f viewMatrix, Vec3 cameraPos, Vec3 lookDirection,
                       float pixelWorldScale, double pitch,
                       double anchorX, double anchorY, double anchorZ,
                       double targetX, double targetY, double targetZ) {
        Frame frame = Frame.build(cameraPos, lookDirection, pixelWorldScale, pitch,
                anchorX, anchorY, anchorZ, targetX, targetY, targetZ);
        if (frame == null) {
            return;
        }

        // 这个阶段（方块层渲染中）全局的模型视图矩阵不一定是相机朝向，画之前显式设成这一帧的，
        // 画完再原样还回去——方块、实体用的就是这条矩阵。
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.set(viewMatrix);
        RenderSystem.applyModelViewMatrix();

        // 颜色全写在顶点里，把着色器的颜色调制器压成纯白，免得被别的渲染留下的颜色染偏。
        float[] previousColor = RenderSystem.getShaderColor().clone();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        try (ByteBufferBuilder buffer = new ByteBufferBuilder(BUFFER_BYTES)) {
            MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(buffer);
            VertexConsumer consumer = bufferSource.getBuffer(RenderType.dragonRays());

            float half = THICKNESS * 0.5F;
            int count = OUTLINE.length;

            // 上、下表面各绕一圈闭环。
            for (int i = 0; i < count; i++) {
                int j = (i + 1) % count;
                float ax = OUTLINE[i][0] * SCALE;
                float az = OUTLINE[i][1] * SCALE;
                float bx = OUTLINE[j][0] * SCALE;
                float bz = OUTLINE[j][1] * SCALE;
                emitEdge(consumer, TOP_COLOR, frame, ax, half, az, bx, half, bz);
                emitEdge(consumer, BOTTOM_COLOR, frame, ax, -half, az, bx, -half, bz);
            }

            // 每个角一条竖棱，把上下两层连起来，顺带在拐角处叠亮。
            for (float[] point : OUTLINE) {
                float x = point[0] * SCALE;
                float z = point[1] * SCALE;
                emitEdge(consumer, SIDE_COLOR, frame, x, half, z, x, -half, z);
            }

            // 所有线段一次提交。
            bufferSource.endBatch();
        } finally {
            RenderSystem.setShaderColor(previousColor[0], previousColor[1], previousColor[2], previousColor[3]);
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    /**
     * 把一条棱摊成朝向镜头的四边形（两个三角形）：沿「棱方向 × 看向镜头方向」把线加宽，
     * 加宽多少按这条棱到镜头的深度现算，保证屏幕上是 {@link #LINE_WIDTH_PIXELS} 像素。
     * 全部计算都在箭头本地坐标系里做，最后用 {@link Frame} 的三个轴换算成相机相对坐标。
     */
    private static void emitEdge(VertexConsumer consumer, float[] color, Frame frame,
                                 float ax, float ay, float az, float bx, float by, float bz) {
        float dx = bx - ax;
        float dy = by - ay;
        float dz = bz - az;

        float midX = (ax + bx) * 0.5F;
        float midY = (ay + by) * 0.5F;
        float midZ = (az + bz) * 0.5F;
        float viewX = frame.camLocalX - midX;
        float viewY = frame.camLocalY - midY;
        float viewZ = frame.camLocalZ - midZ;

        // 细带的横向 = 棱方向与视线方向的叉积。
        float sideX = dy * viewZ - dz * viewY;
        float sideY = dz * viewX - dx * viewZ;
        float sideZ = dx * viewY - dy * viewX;
        float length = (float) Math.sqrt(sideX * sideX + sideY * sideY + sideZ * sideZ);

        if (length < 1.0E-6F) {
            // 视线正好顺着这条棱看过去，叉积退化了：挑一根跟棱最不平行的轴再叉一次。
            float refX = 0.0F;
            float refY = 0.0F;
            float refZ = 0.0F;
            if (Math.abs(dx) <= Math.abs(dy) && Math.abs(dx) <= Math.abs(dz)) {
                refX = 1.0F;
            } else if (Math.abs(dy) <= Math.abs(dz)) {
                refY = 1.0F;
            } else {
                refZ = 1.0F;
            }
            sideX = dy * refZ - dz * refY;
            sideY = dz * refX - dx * refZ;
            sideZ = dx * refY - dy * refX;
            length = (float) Math.sqrt(sideX * sideX + sideY * sideY + sideZ * sideZ);
            if (length < 1.0E-6F) {
                return;
            }
        }

        // 本地坐标 -> 相机相对坐标：分别沿本地三个轴把分量摊开。
        float startX = frame.toWorldX(ax, ay, az);
        float startY = frame.toWorldY(ax, ay, az);
        float startZ = frame.toWorldZ(ax, ay, az);
        float endX = frame.toWorldX(bx, by, bz);
        float endY = frame.toWorldY(bx, by, bz);
        float endZ = frame.toWorldZ(bx, by, bz);

        // 这条棱离镜头多远（沿视线方向的深度）：同样 1 像素，远处对应更大的世界尺寸。
        // 按深度反推细带该有多宽，屏幕上量出来就恒是 LINE_WIDTH_PIXELS 像素。
        float midWorldX = (startX + endX) * 0.5F;
        float midWorldY = (startY + endY) * 0.5F;
        float midWorldZ = (startZ + endZ) * 0.5F;
        float depth = frame.depth(midWorldX, midWorldY, midWorldZ);
        if (depth < 1.0E-3F) {
            // 棱跑到镜头后面去了，这一帧看不见它。
            return;
        }
        float halfWidth = 0.5F * LINE_WIDTH_PIXELS * frame.pixelWorldScale * depth;

        float widen = halfWidth / length;
        sideX *= widen;
        sideY *= widen;
        sideZ *= widen;

        // 加宽方向也要从本地转到世界。
        float offsetX = frame.axisX(sideX, sideY, sideZ);
        float offsetY = frame.axisY(sideX, sideY, sideZ);
        float offsetZ = frame.axisZ(sideX, sideY, sideZ);

        float p0x = startX - offsetX;
        float p0y = startY - offsetY;
        float p0z = startZ - offsetZ;
        float p1x = startX + offsetX;
        float p1y = startY + offsetY;
        float p1z = startZ + offsetZ;
        float p2x = endX + offsetX;
        float p2y = endY + offsetY;
        float p2z = endZ + offsetZ;
        float p3x = endX - offsetX;
        float p3y = endY - offsetY;
        float p3z = endZ - offsetZ;

        quad(consumer, color, p0x, p0y, p0z, p1x, p1y, p1z, p2x, p2y, p2z, p3x, p3y, p3z);
    }

    /** 一个四边形拆成两个三角形，按 p0-p1-p2-p3 的顺序（p0 与 p2 对着）。 */
    private static void quad(VertexConsumer consumer, float[] color,
                             float p0x, float p0y, float p0z,
                             float p1x, float p1y, float p1z,
                             float p2x, float p2y, float p2z,
                             float p3x, float p3y, float p3z) {
        vertex(consumer, color, p0x, p0y, p0z);
        vertex(consumer, color, p1x, p1y, p1z);
        vertex(consumer, color, p2x, p2y, p2z);

        vertex(consumer, color, p0x, p0y, p0z);
        vertex(consumer, color, p2x, p2y, p2z);
        vertex(consumer, color, p3x, p3y, p3z);

        // 反绕序（背面朝镜头时用得上）。
        vertex(consumer, color, p2x, p2y, p2z);
        vertex(consumer, color, p1x, p1y, p1z);
        vertex(consumer, color, p0x, p0y, p0z);

        vertex(consumer, color, p3x, p3y, p3z);
        vertex(consumer, color, p2x, p2y, p2z);
        vertex(consumer, color, p0x, p0y, p0z);
    }

    private static void vertex(VertexConsumer consumer, float[] color, float x, float y, float z) {
        consumer.addVertex(x, y, z).setColor(color[0], color[1], color[2], color[3]);
    }

    /**
     * 箭头这一帧的本地坐标系：三个互相垂直的单位轴（用「相机相对坐标」表示）+ 箭头中心。
     *
     * <p>本地 x 向右、y 朝上、z 是箭头尖端指向。这是个<b>躺平</b>的坐标系，不是正对镜头的
     * 广告牌：</p>
     * <ol>
     *   <li>本地 y 恒等于世界上方，也就是薄板的厚度方向朝上——俯仰角为 0 时整块板躺在水平面里；</li>
     *   <li>本地 z 取「箭头位置 → 目标」在水平面里的方向（只算 x/z，不看高度差），箭头
     *       在水平方向上指着目标；</li>
     *   <li>最后绕本地 x 轴（水平的「右」轴）俯仰，尖端按真实高度差抬起或压下。</li>
     * </ol>
     */
    private static final class Frame {

        // 本地 x / y / z 三个轴在世界里的方向（单位向量）。
        private final float xxx;
        private final float xxy;
        private final float xxz;
        private final float yyx;
        private final float yyy;
        private final float yyz;
        private final float zzx;
        private final float zzy;
        private final float zzz;

        // 箭头中心（相机相对坐标）。
        private final float ox;
        private final float oy;
        private final float oz;

        // 相机在箭头本地坐标系里的位置，用来决定每根棱的细带朝哪边摊开。
        private final float camLocalX;
        private final float camLocalY;
        private final float camLocalZ;

        // 相机的视线方向（世界轴向上的单位向量），用来算每条棱离镜头多深。
        private final float lookX;
        private final float lookY;
        private final float lookZ;

        // 「深度 1」处屏幕 1 像素对应多少世界尺寸。
        private final float pixelWorldScale;

        private Frame(float xxx, float xxy, float xxz,
                      float yyx, float yyy, float yyz,
                      float zzx, float zzy, float zzz,
                      float ox, float oy, float oz,
                      float camLocalX, float camLocalY, float camLocalZ,
                      float lookX, float lookY, float lookZ,
                      float pixelWorldScale) {
            this.xxx = xxx;
            this.xxy = xxy;
            this.xxz = xxz;
            this.yyx = yyx;
            this.yyy = yyy;
            this.yyz = yyz;
            this.zzx = zzx;
            this.zzy = zzy;
            this.zzz = zzz;
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.camLocalX = camLocalX;
            this.camLocalY = camLocalY;
            this.camLocalZ = camLocalZ;
            this.lookX = lookX;
            this.lookY = lookY;
            this.lookZ = lookZ;
            this.pixelWorldScale = pixelWorldScale;
        }

        /** 算这一帧的坐标系；目标方向退化到算不出来时返回 {@code null}（这一帧就不画）。 */
        static Frame build(Vec3 cameraPos, Vec3 lookDirection, float pixelWorldScale, double pitch,
                           double anchorX, double anchorY, double anchorZ,
                           double targetX, double targetY, double targetZ) {
            // 水平朝向：箭头躺平，指向只取「箭头中心 -> 目标」在水平面里的方向，跟高度差无关。
            double fx = targetX - anchorX;
            double fz = targetZ - anchorZ;
            double hLen = Math.sqrt(fx * fx + fz * fz);
            if (hLen < 1.0E-4) {
                // 目标正在正上/正下方，水平方向没有意义：改用相机朝向的水平投影。
                fx = lookDirection.x;
                fz = lookDirection.z;
                hLen = Math.sqrt(fx * fx + fz * fz);
                if (hLen < 1.0E-4) {
                    // 相机也在看正上/正下方：随便挑个水平方向，箭头照样躺平不立起来。
                    fx = 1.0;
                    fz = 0.0;
                    hLen = 1.0;
                }
            }
            fx /= hLen;
            fz /= hLen;

            // 躺平的水平坐标系：本地 x = 右（水平面内、垂直于朝向，= 世界上方 × 朝向），
            // 本地 y = 世界上方（薄板厚度方向），本地 z = 朝向目标。俯仰角为 0 时薄板就躺在
            // 水平面里——这就是「平的，不是竖着的」。
            double axx = fz;
            double axy = 0.0;
            double axz = -fx;

            // 绕本地 x 轴（水平的「右」轴）俯仰：ŷ = up·cos − z·sin，ẑ = z·cos + up·sin。
            // 正角尖端抬起（目标在上方）、负角尖端压下，0 就是完全躺平。角度是外面按真实
            // 高度差算好的（见 DeliveryArrowOverlay），这里只负责转。
            // 翘起幅度来自游戏内配置（默认 1.0 = 按真实角度，不缩放）。
            double lean = pitch * AddonConfig.ARROW_LEAN_SCALE.get();
            double leanCos = Math.cos(lean);
            double leanSin = Math.sin(lean);

            double ayx = -fx * leanSin;
            double ayy = leanCos;
            double ayz = -fz * leanSin;
            double azx = fx * leanCos;
            double azy = leanSin;
            double azz = fz * leanCos;

            // 相机相对箭头中心的向量在本地坐标系里的三个分量。
            double relX = cameraPos.x - anchorX;
            double relY = cameraPos.y - anchorY;
            double relZ = cameraPos.z - anchorZ;
            double camLocalX = relX * axx + relY * axy + relZ * axz;
            double camLocalY = relX * ayx + relY * ayy + relZ * ayz;
            double camLocalZ = relX * azx + relY * azy + relZ * azz;

            return new Frame((float) axx, (float) axy, (float) axz,
                    (float) ayx, (float) ayy, (float) ayz,
                    (float) azx, (float) azy, (float) azz,
                    (float) (anchorX - cameraPos.x),
                    (float) (anchorY - cameraPos.y),
                    (float) (anchorZ - cameraPos.z),
                    (float) camLocalX, (float) camLocalY, (float) camLocalZ,
                    (float) lookDirection.x, (float) lookDirection.y, (float) lookDirection.z,
                    pixelWorldScale);
        }

        /** 相机相对坐标的点离镜头多远：沿视线方向量出来的深度（在镜头前方为正）。 */
        float depth(float x, float y, float z) {
            return x * lookX + y * lookY + z * lookZ;
        }

        /** 本地坐标 -> 相机相对坐标的世界 X 分量。 */
        float toWorldX(float lx, float ly, float lz) {
            return ox + xxx * lx + yyx * ly + zzx * lz;
        }

        /** 本地坐标 -> 相机相对坐标的世界 Y 分量。 */
        float toWorldY(float lx, float ly, float lz) {
            return oy + xxy * lx + yyy * ly + zzy * lz;
        }

        /** 本地坐标 -> 相机相对坐标的世界 Z 分量。 */
        float toWorldZ(float lx, float ly, float lz) {
            return oz + xxz * lx + yyz * ly + zzz * lz;
        }

        /** 本地方向向量 -> 世界 X 分量（不加平移）。 */
        float axisX(float lx, float ly, float lz) {
            return xxx * lx + yyx * ly + zzx * lz;
        }

        /** 本地方向向量 -> 世界 Y 分量（不加平移）。 */
        float axisY(float lx, float ly, float lz) {
            return xxy * lx + yyy * ly + zzy * lz;
        }

        /** 本地方向向量 -> 世界 Z 分量（不加平移）。 */
        float axisZ(float lx, float ly, float lz) {
            return xxz * lx + yyz * ly + zzz * lz;
        }
    }
}
