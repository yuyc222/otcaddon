package com.automods.otcaddon.compat;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;

/**
 * 把 GeckoLib 刷屏的「找不到动画」报错挡在控制台和日志文件之外。
 *
 * <p>刷屏长这样：</p>
 *
 * <pre>[Render thread/ERROR] [GeckoLib/]: Unable to find animation: customer_sit_1 for CustomerEntity</pre>
 *
 * <p>真正的病根见 {@code GeckoLibAnimationRepair}：GeckoLib 载入动画时是「一个循环没做容错」，
 * 只要有任意一个模组的动画文件解不开，循环就静默中断，排在它后面的动画文件<b>整批</b>进不了缓存。
 * 前置 Order To Cook 的顾客动画和打单机动画正好被挤掉，于是每帧都报一次找不到。那边的做法是把
 * 丢掉的动画重新烘焙补回缓存，补上之后这类报错就不再产生了。</p>
 *
 * <p>本过滤器只负责「补缓存生效前那两三秒」的兜底，以及万一哪天 GeckoLib 换了实现、补缓存失效时
 * 不让日志被刷爆。拦截条件只有一条：正文含 {@code Unable to find animation:} —— GeckoLib 只会
 * 在动画确实解析不出来时打印这句话，其余报错（例如动画文件整个丢失）照常显示，不会被一起吞掉。</p>
 */
public final class OtcAnimationSpamFilter {

    /** GeckoLib 用的日志器名字，控制台里的 {@code [GeckoLib/]} 就是它。 */
    private static final String GECKOLIB_LOGGER = "GeckoLib";

    /** 报错正文的固定开头，GeckoLib 只在动画解析失败时打印它。 */
    private static final String MESSAGE_HEAD = "Unable to find animation:";

    /** 只装一次，重复调用直接返回。 */
    private static boolean installed;

    private OtcAnimationSpamFilter() {
    }

    /**
     * 把过滤器挂到日志系统上。模组构造时调用一次即可。
     *
     * <p>挂了两个地方：GeckoLib 这个日志器本身，以及根日志器——这样不管日志配置把 GeckoLib 的
     * 输出挂在哪一级，都能拦得住。整个安装过程包了 try/catch：万一哪天日志后端换了实现，最坏
     * 结果只是拦不住噪音，绝不会因为装过滤器把游戏搞崩。</p>
     */
    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        try {
            Filter filter = new MissingAnimationFilter();
            attachToLogger(GECKOLIB_LOGGER, filter);
            attachToRoot(filter);
        } catch (Throwable failure) {
            // 过滤网装不上只是噪音照旧，不影响任何功能，所以这里只提醒一句。
            com.automods.otcaddon.OtcAddon.LOGGER.warn(
                    "未能安装 GeckoLib 缺失动画报错的过滤器，该类报错可能仍会刷屏", failure);
        }
    }

    /** 往指定名字的日志器上挂过滤器。 */
    private static void attachToLogger(String name, Filter filter) {
        if (LogManager.getLogger(name) instanceof org.apache.logging.log4j.core.Logger coreLogger) {
            coreLogger.addFilter(filter);
        }
    }

    /** 往根日志器上再挂一份，作为兜底。 */
    private static void attachToRoot(Filter filter) {
        if (LogManager.getContext(false) instanceof LoggerContext context) {
            LoggerConfig root = context.getConfiguration().getRootLogger();
            root.addFilter(filter);
        }
    }

    /**
     * 真正干活的过滤器：命中「GeckoLib 找不到动画」就丢弃，其余一律放行。
     *
     * <p>父类 {@link AbstractFilter} 默认对不认识的调用方式返回 {@code NEUTRAL}（不表态），
     * 所以这里只要覆盖实际会用到的两种入口就够了。</p>
     */
    private static final class MissingAnimationFilter extends AbstractFilter {

        /** 按「已经包装好的日志事件」过滤，日志器的配置级过滤走的是这一条。 */
        @Override
        public Result filter(LogEvent event) {
            return isMissingAnimationNoise(event == null ? null : event.getMessage())
                    ? Result.DENY : Result.NEUTRAL;
        }

        /** 按「原始参数」过滤，另一种调用路径。 */
        @Override
        public Result filter(org.apache.logging.log4j.core.Logger logger, Level level, Marker marker,
                             Message message, Throwable throwable) {
            return isMissingAnimationNoise(message) ? Result.DENY : Result.NEUTRAL;
        }

        /** 正文里出现固定开头才算噪音。 */
        private static boolean isMissingAnimationNoise(Message message) {
            if (message == null) {
                return false;
            }
            String text = message.getFormattedMessage();
            return text != null && text.contains(MESSAGE_HEAD);
        }
    }
}
