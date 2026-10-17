# 从源码构建 Order To Cook Addon 1.1.0

## 环境要求

- **JDK 21**（NeoForge 1.21.1 强制要求，Java 17 或 8 都编不过）。
- **Gradle 8.8 或更高**。本项目用 NeoForge 的 ModDevGradle 插件（`net.neoforged.moddev` 2.0.143），不需要 NeoGradle，也不需要预装 ForgeGradle。
- 首次构建要联网：Gradle 会从 `maven.neoforged.net` 下载 NeoForge 21.1.244，从 `maven.blamejared.com` 下载 JEI 的编译期 API。

## 构建

```bash
export JAVA_HOME=/path/to/jdk-21
gradle build
```

产物：`build/libs/otcaddon-1.1.0.jar`

（本包没有附带 Gradle Wrapper 的 `gradlew`，用本机 Gradle 即可；也可以直接在 IntelliJ IDEA 里打开本目录，按 Gradle 项目导入。）

## 在开发环境里跑起来

```bash
gradle runClient
gradle runServer
```

`runClient` / `runServer` 的启动目录是 `run/`。**请把 Order To Cook 本体 1.3.6+ 的 jar 放进 `run/mods/`**，否则运行时会缺前置：本工程里的 `cn/breezeth/ordertocook/**` 只是编译期签名占位，不含任何实现。

## 目录结构

```
src/main/java/com/automods/otcaddon/   附属本体
    waiter/      悦灵服务员（实体、管理器、洗碗、队伍）
    money/       储钱罐方块、容器、otc 币读写
    client/      渲染、指路箭头、JEI 收藏与菜单快捷添加、动画修复
    compat/      前置兼容、动画报错过滤、旧数据清理
    mixin/       对前置的少量注入
    network/     网络包（协议版本 9）
    registry/    方块、物品、实体注册
src/main/java/cn/breezeth/ordertocook/ 前置 API 的编译期占位（不进 jar，见下）
src/main/resources/                    资源：模型、贴图、语言、配方、战利品表、mixins 配置
tools/render_pixel_art.py              贴图生成脚本（可选，不影响构建）
```

## 重要注意事项

1. **`cn.breezeth.ordertocook.**` 只用于编译。**`build.gradle` 的 `jar` 任务已经把它们整体排除**（`exclude 'cn/**'`）。千万不要为了“完整”把它们打进 jar：运行时会和 Order To Cook 本体里的同名类冲突，注册会写进影子副本，表现为“注册成功但游戏里没反应”。
2. 版本号由 `gradle.properties` 的 `release_version` 决定，jar 文件名和游戏内模组列表都显示 `1.1.0`；`mod_version` 只是构建标记。
3. 运行期依赖 Order To Cook **1.3.6+**；JEI **19.57.0.449+** 是可选的，缺失时收藏和菜单快捷添加自动不生效，其余功能正常。
4. 改完资源后如果游戏内模型不更新，先确认 `build/resources/main` 已刷新，或执行一次 `gradle clean build`。

## License

MIT，见 [LICENSE](LICENSE)。

---

## English quick start

Requires **JDK 21** and **Gradle 8.8+** (NeoForge ModDevGradle 2.0.143). Run `gradle build`; the jar lands at `build/libs/otcaddon-1.1.0.jar`. For in-game testing, build the dev runs with `gradle runClient` and put the Order To Cook 1.3.6+ jar into `run/mods/`.

The `cn.breezeth.ordertocook.**` sources are compile-time API stubs only and are excluded from the jar by `build.gradle`; shipping them would clash with the real Order To Cook classes at runtime.

Runtime target: Minecraft 1.21.1, NeoForge 21.1.244+, Order To Cook 1.3.6+, optional JEI 19.57.0.449+.
