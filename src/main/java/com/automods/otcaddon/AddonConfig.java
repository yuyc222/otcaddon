package com.automods.otcaddon;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 本附属的游戏内配置（NeoForge 配置系统）。
 *
 * <p>用的是 <b>client</b> 类型，所以改完会生成/生效于 {@code config/otcaddon-client.toml}，
 * 在单人游戏里改这个文件就行，不影响服务端。两个旋钮都只跟「指路箭头长什么样」有关：</p>
 *
 * <ul>
 *   <li>{@link #ARROW_DROP_BELOW_EYE}：箭头中心比玩家眼睛低多少格，也就是它的默认上下位置；</li>
 *   <li>{@link #ARROW_LEAN_SCALE}：箭头翘起（俯仰）的幅度系数。</li>
 * </ul>
 *
 * <p>注册见 {@link OtcAddon} 的构造器。取值在每帧渲染时现读，所以不用重启游戏也能生效，
 * 改完 toml 后重新加载一次配置即可。</p>
 */
public final class AddonConfig {

    /** 构建配置项用的 builder，所有项都挂在它下面。 */
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /**
     * 箭头中心比玩家眼睛低多少格。默认 0.5——正好在人物眼睛下方半格的位置。
     *
     * <p>调大它就往下沉，调小（比如 0.3）就往上抬。不能为负：为负会跑到眼睛上方去。</p>
     */
    public static final ModConfigSpec.DoubleValue ARROW_DROP_BELOW_EYE = BUILDER
            .comment("指路箭头中心比玩家眼睛低多少格（默认 0.5，即眼睛下方半格）。",
                    "调大箭头往下沉，调小往上抬。0 就是和眼睛齐平。")
            .defineInRange("arrowDropBelowEye", 0.5, 0.0, 3.0);

    /**
     * 箭头翘起幅度系数：尖端俯仰角 = 真实俯仰角 × 这个系数。
     *
     * <p>真实俯仰角只看「目标与玩家的高度差 ÷ 水平距离」，同一层时是 0；这个系数负责把翘的
     * 幅度整体放大或收窄。默认 1.0（不缩放，按真实角度来）。设成 0 箭头就永远躺平不翘。</p>
     */
    public static final ModConfigSpec.DoubleValue ARROW_LEAN_SCALE = BUILDER
            .comment("箭头翘起幅度系数：尖端俯仰角 = 真实俯仰角 × 这个系数（默认 1.0）。",
                    "默认 1.0 就是按真实高度差算出的角度，不做缩放；设成 0 箭头永远躺平不翘。")
            .defineInRange("arrowLeanScale", 1.0, 0.0, 2.0);

    /** 这份配置规格，构造模组时交给 NeoForge 注册。 */
    public static final ModConfigSpec SPEC = BUILDER.build();

    private AddonConfig() {
    }
}
