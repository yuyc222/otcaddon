# Eden Power 与 GeckoLib 动画加载异常 · 分析报告（供 AI 自检）

> 用途：把本轮对 `eden_power` 的分析交给另一个 AI 独立复核。报告内所有断言都标注了
> 证据出处与置信度，并在第 6、7 节给出可执行验证清单与反证条件。
> 标注约定：`[已证实]` = 有日志/字节码直接证据；`[高置信推断]` = 由已证实事实推出；
> `[待验证]` = 需要动手做实验才能定论。

---

## 元信息

| 项 | 值 |
|---|---|
| 分析对象 | `eden_power` 模组是否 / 如何导致 GeckoLib 动画缓存缺失 |
| 证据日志 | `/workspace/uploads/latest.log`（443,122 行，一次完整启动） |
| 游戏环境 | Minecraft 1.21.1 / NeoForge 21.1.250 |
| GeckoLib | 4.9.3（`geckolib-neoforge-1.21.1-4.9.3.jar`，日志 L27/L75） |
| Eden Power | 1.0.107（日志 L70；本轮由 1.0.99 更新而来，L383） |
| Order To Cook | 1.3.6（日志 L36） |
| otcaddon | 1.1.0-r43（日志 L30）——**这是修复前的版本**，不是 r47 修复版 |
| 源码取证 | `javap` 反编译 `geckolib-neoforge-1.21.1-4.9.3.jar`，并用 `geckolib-forge-1.21.1-4.9.2.jar` 交叉比对，二者逻辑一致 |

> 注意：日志来自 **r43（修复前）**。因此这份日志是"故障现场"，可以分析成因，
> 但不能用来证明 r47 的修复效果。

---

## 0. 结论摘要（TL;DR）

1. `[已证实]` Eden Power 在 `eden_power:animations/` 下放了 3 个文件名不以
   `.animation.json` 结尾的 JSON：
   - `earth_shock_pose.json`
   - `frost_dragon_mist_chant.json`
   - `instant_cast_pose.json`

   GeckoLib 会扫到它们并打印 WARN，但**命名不合规本身不会导致加载失败**（L289/291/293）。

2. `[已证实]` GeckoLib 4.9.3 对动画文件采用"**逐文件异步烘焙 + 汇总写入**"。
   失败分两条完全不同的路径：
   - 文件内部键帧数据坏 → `CompoundException` → **只丢该文件**，其余文件不受影响（会打印堆栈）。
   - 文件缺少顶层 `animations` 对象、JSON 非法、或把 `.geo.json` 误放进 `animations/`
     → 普通异常 → 该文件 future 失败 → 汇总循环的 `join()` 中断 →
     **本次 reload 的动画缓存整体不写入（整批丢失）**。

3. `[高置信推断]` Eden Power 有自己独立的渲染/动画系统（日志证明它自带模型加载器），
   这 3 个文件很可能是它自研的技能/姿势数据，**不保证**含 GeckoLib 要求的顶层
   `animations` 对象 → 因此它是"整批中断"路径的**高嫌疑来源**。

4. `[待验证]` 仅凭日志**无法 100% 定罪**。必须打开 Eden Power 的 jar 读这 3 个文件的
   顶层结构，并做"移出 Eden Power 后复现"实验。清单见第 6 节。

5. `[修正上一轮表述]` 上一轮说"命名不合规 + 内容解不开 → 静默丢整批"，需要精确化为：
   - 命名不合规 → 无害，仅 WARN；
   - **顶层缺 `animations` 键（`JsonSyntaxException`）** → 才是连累整批的那条路；
   - "内容解不开"（`CompoundException`）→ 是**逐文件隔离**的，不连累别人。
   这个区别决定了 Eden Power 是否真的有罪，是本报告的核心。

---

## 1. 证据清单（事实）

### 1.1 加载名单与版本

- L288 `Reloading ResourceManager: ... mod/geckolib ... mod/eden_power,mobends ... mod/ordertocook ... mod/otcaddon ...`
- L27 `Found mod file "geckolib-neoforge-1.21.1-4.9.3.jar"`
- L75 `GeckoLib 4 4.9.3 (geckolib)`
- L70 `Eden Power 1.0.107 (eden_power)`
- L36 `ordertocook-1.3.6-neoforge1.21.1.jar`
- L30 `otcaddon-1.1.0-r43.jar`
- L383 `eden_power (version 1.0.99 -> 1.0.107)`

### 1.2 GeckoLib 对 Eden Power 的告警（原文）

```
L289 [Worker-Main-9/WARN] [GeckoLib/]: Found animation file with improper file name format;
     animation files should end in .animation.json: 'eden_power:animations/earth_shock_pose.json'
L291 [Worker-Main-19/WARN] [GeckoLib/]: ... 'eden_power:animations/frost_dragon_mist_chant.json'
L293 [Worker-Main-19/WARN] [GeckoLib/]: ... 'eden_power:animations/instant_cast_pose.json'
```

三条都是 `WARN`（不是 `ERROR`），且 GeckoLib 在打印后**继续解析内容**（见第 2.2 节字节码）。

### 1.3 同批告警的其它模组（对照组）

```
L290 irons_spellbooks:animations/casting_animations.json
L292 irons_spellbooks:animations/ice_block_animations.json
L298 irons_spellbooks:animations/root_animations.json
L299 irons_spellbooks:animations/spectral_hammer.json
L300 irons_spellbooks:animations/summoned_weapon_animations.json
L301 irons_spellbooks:animations/void_tentacle_animations.json
L302 irons_lib:animations/wizard_armor_animation.json
L303 irons_spellbooks:animations/wizard_armor_animation.json
```

共 11 条 improper filename：Eden Power 3 条、Iron's Spells 7 条、Iron's Lib 1 条。
`iron` 系同样命名不合规，但日志里**没有**它们的动画缺失报错。这至少说明：
"命名不合规"在这个整合包里是普遍现象，**不是故障的充分条件**。

### 1.4 Eden Power 有独立渲染/动画系统的证据

- L378 `[Edenpower/]: 遗念水晶原模型已加载：71 个网格，754 个面，12.0 秒循环动画。`
- L6083 `Class com.yuyc.edenpower.impl.client.render.MyriadSwordsRenderer$AlphaConsumer ...`
- L127-131 Eden Power 自带 `eden_power.mobends.armourers.mixins.json`（三个 mixin 目标
  armourers_workshop 类未找到，仅 WARN）

以上都发生在 GeckoLib 体系之外，说明 Eden Power 的模型动画不依赖 GeckoLib。
这提高了"那 3 个文件不是 GeckoLib 动画、而是自研数据"的可能性。

### 1.5 故障表现

- 全日志 `Unable to find animation` 共 **442,383** 条。
- 按动画名统计：

  | 动画名 | 次数 |
  |---|---|
  | customer_sit_1 | 183,318 |
  | customer_sit_3 | 118,541 |
  | customer_sit_4 | 116,655 |
  | customer_sit_2 | 18,729 |
  | customer_wait_1 | 2,377 |
  | neworder | 1,213 |
  | npc_eat | 980 |
  | npc_sitspawn | 552 |
  | customer_wait_4 | 16 |
  | customer_wait_2 | 2 |

- 按实体统计：`CustomerEntity` 440,190 条、`OrderMachineBlockEntity` 1,213 条。
- **全部是 ordertocook 的动画**；GeckoLib 系其它模组的动画没有出现在报错里。
- L473 第一条：`Unable to find animation: customer_sit_3 for CustomerEntity`（11:35:39.473）。

### 1.6 两个必须解释的"沉默"证据

1. 全日志 **0 条** `Error loading animation file`、**0 条** `CompoundException` 堆栈、
   **0 条** `Geo model file found in animations folder`。
2. 全日志没有 GeckoLib reload 失败的堆栈。

第 1 点说明：**没有**任何动画文件走"CompoundException → 打印堆栈"这条路径
（因为那条路一定会 `printStackTrace()`）。也就是说 ordertocook 动画丢失**不是**
"它自己的动画文件内部键帧坏"造成的。

### 1.7 其它相关告警

```
L294 [GeckoLib/]: Unsupported geometry json version for model ordertocook:geo/block/order_machine.geo.json. Supported versions: 1.12.0
L295 ... takeout_bag_placed.geo.json
L296 ... food_plate_placed.geo.json
```

这是 ordertocook 的 **geo 模型**版本告警（不影响动画文件），只作背景。

---

## 2. GeckoLib 4.9.3 动画加载机制（字节码取证）

以下结论由 `javap -c -p` 反编译 `software.bernie.geckolib.cache.GeckoLibCache` 与
`software.bernie.geckolib.loading.FileLoader` 得到（4.9.3 与 4.9.2 一致）。

### 2.1 文件发现：只按 `.json` 结尾过滤

- `GeckoLibCache.lambda$loadResources$7` 调用
  `ResourceManager.listResources("animations", predicate)`。
- 谓词 `lambda$loadResources$6` 只做：`ResourceLocation.toString().endsWith(".json")`。

**结论**：任何命名空间下 `assets/<ns>/animations/**.json` 都会被收进来，
GeckoLib **不要求**文件名以 `.animation.json` 结尾。Eden Power 的 3 个文件
因此必然进入烘焙列表。

### 2.2 逐文件命名检查与解析：`FileLoader.loadAnimationsFile`

字节码里的顺序：

1. `if (path.endsWith(".geo.json")) throw new IllegalArgumentException("Geo model file found in animations folder!");`
2. `if (!path.endsWith(".animation.json")) LOGGER.warn("Found animation file with improper file name format...")`
3. **继续执行**：
   `KeyFramesAdapter.GEO_GSON` → `loadFile()` → `GsonHelper.getAsJsonObject(json, "animations")`
   → `gson.fromJson(..., BakedAnimations.class)`

其中 `loadFile` → `getFileContents` 在读取失败时抛
`RuntimeException(new FileNotFoundException(...))`。

**关键点**：命名不合规只是第 2 步的 WARN，第 3 步照跑。所以
`eden_power:animations/*.json` 会不会出事，取决于第 3 步——
**文件里有没有顶层对象 `"animations"`**。

### 2.3 逐文件异常处理：`GeckoLibCache.lambda$loadAnimations$2`

该方法的异常表（`javap` 原文）：

```
Exception table:
   from  to  target  type
     0    5     6    Class software/bernie/geckolib/util/CompoundException
     0    5    38    Class java/lang/Exception
```

- `target 6`（CompoundException）：`CompoundException.withMessage(...).printStackTrace()`
  → 然后 `return new BakedAnimations(空 map)`。**该文件变空，不向上抛。**
- `target 38`（其它 Exception）：`GeckoLibConstants.exception(loc, "Error loading animation file", e)`
  → **`throw` 出去**，该文件 future 失败。

而 `BakedAnimationsAdapter.bakeAnimation(...)` 的签名就是
`throws CompoundException`，说明："有 `animations` 键、但某个动画的键帧写坏"这类错误
才会走 CompoundException（并被隔离）；**顶层没有 `animations` 键**是
`GsonHelper.getAsJsonObject` 抛的 `JsonSyntaxException`，属于第二条路（会连累整批）。

### 2.4 汇总写入：`lambda$loadResources$10`

逻辑（伪代码）：

```java
for (Map.Entry<ResourceLocation, CompletableFuture<T>> e : map.entrySet()) {
    if (!EXCLUDED_NAMESPACES.contains(e.getKey().getNamespace().toLowerCase(Locale.ROOT))) {
        biConsumer.accept(e.getKey(), e.getValue().join());   // ← 没有 try/catch
    }
}
```

**关键点**：`join()` 没有逐项容错。任意一个 future 因普通异常失败，
`join()` 抛 `CompletionException` → **整个循环中断**，排在后面的文件不再写入；
该阶段 future 异常完成。

### 2.5 最终提交：`GeckoLibCache.reload`

```java
CompletableFuture.allOf(loadAnimations(...), loadModels(...))
    .thenCompose(barrier::wait)
    .thenAcceptAsync(lambda$reload$0, executor);
```

`lambda$reload$0` 才把局部 map 赋给静态 `ANIMATIONS` / `MODELS`。
任一路 future 异常 → `allOf` 异常 → `lambda$reload$0` **不执行** →
静态 `ANIMATIONS` 保持旧值；首次加载时它是 `Collections.emptyMap()`。

### 2.6 三种结局对照表

| 触发条件 | 抛出类型 | 是否连累别的文件 | 日志表现 |
|---|---|---|---|
| 命名不合规、内容合法（有顶层 `animations`） | 无 | 否 | 仅 1 条 WARN |
| 有 `animations`、内部键帧坏 | `CompoundException` | 否（该文件变空） | 会打印 CompoundException 堆栈 |
| 顶层缺 `animations` / 非法 JSON / 读文件失败 / `.geo.json` 放错目录 | 普通 `Exception` | **是，整批不写入** | `Error loading animation file` + 上层 reload 失败 |

---

## 3. Eden Power 的定位

| 项 | 内容 |
|---|---|
| 目录 | `eden_power:animations/` |
| 文件 | `earth_shock_pose.json`、`frost_dragon_mist_chant.json`、`instant_cast_pose.json` |
| 语义 | "地震姿势 / 冰龙迷雾吟唱 / 瞬发施法姿势" —— 像技能姿态数据，不像标准 GeckoLib 动画 |
| 模组自身动画系统 | 自研（L378 遗念水晶、L6083 MyriadSwordsRenderer），不依赖 GeckoLib |
| GeckoLib 告警 | 3 条 WARN，无 ERROR |
| 日志中的解析结果 | 无堆栈、无 `Error loading animation file` |

**综合判断**：Eden Power 属于"把非 GeckoLib 数据放进 GeckoLib 扫描目录"的高嫌疑来源。
但是否真的触发整批中断，取决于这 3 个文件顶层有没有 `animations` 键——
日志本身给不出答案，必须打开 jar（见第 6 节 V1）。

---

## 4. 两种因果链

### 假设 A：Eden Power 是元凶（或元凶之一）

```
eden_power 3 个文件缺顶层 "animations" 键
  → FileLoader 第 3 步 GsonHelper.getAsJsonObject 抛 JsonSyntaxException（普通 Exception）
  → lambda$loadAnimations$2 catch(38) 包成 RuntimeException 抛出
  → 该文件 future 异常
  → lambda$loadResources$10 汇总循环 join() 中断，后续文件不写入
  → allOf 异常 → lambda$reload$0 不执行
  → 静态 ANIMATIONS 保持空（首次加载）
  → 所有 GeckoLib 实体的动画都找不到
  → 一直渲染的 ordertocook 顾客每帧刷 Unable to find animation
    （其它 GeckoLib 实体此刻没在渲染，所以没刷）
```

该假设能解释 1.5 的故障现象，但需要解释 1.6 的疑问（见第 5 节）。

### 假设 B：Eden Power 无辜

```
eden_power 3 个文件含合法顶层 "animations"（只是命名怪）
  → 仅 3 条 WARN，烘焙正常，不影响任何其它模组
  → ordertocook 动画丢失另有原因
    （例如它的动画文件被资源包覆盖 / reload 在别处失败 / 文件确实没被 listResources 返回）
```

### 判别点

**唯一决定性判别点**：打开 `eden_power-r107.jar`，看那 3 个文件的顶层键。
有 `animations` → 假设 B；没有 → 假设 A。

---

## 5. 当前证据的证明力与遗留疑点

### 已证实（可直接采信）
- Eden Power 的 3 个文件被 GeckoLib 扫到并告警（L289/291/293）。
- Eden Power 有自研渲染系统，不依赖 GeckoLib（L378/L6083/L127-131）。
- GeckoLib 4.9.3 上述加载/异常/汇总代码路径（字节码）。
- 442,383 条缺失动画全部来自 ordertocook（1.5）。

### 未证实（需要实验）
- 那 3 个文件的顶层结构（决定假设 A/B）。
- "整批中断"是否真的在这次启动中发生。

### 必须解释的疑点

**疑点 1**：如果 `loadResources` 真的因 Eden Power 的文件抛异常，
通常 vanilla 的资源重载会把失败记录到日志（reload 失败堆栈 / `Error loading animation file`）。
但这份日志里一条都没有。可能的解释：
- (a) 异常被 `CompletableFuture` 链吞掉，只体现在"ANIMATIONS 没写入"这个沉默结果上；
- (b) 用户提供的日志被截断或过滤过（但文件有 44 万行，看起来完整）；
- (c) 假设 B 成立，根本没有异常。
→ 这条必须由自检方用 V3 实验判定。

**疑点 2**：为什么只有 ordertocook 报错？两种解释：
- (a) 只有它的实体一直在渲染，别的 GeckoLib 模组此刻没有动画实体在场景里
  （Eden Power 本身不用 GeckoLib，Iron's Spells 法术没在施放）；
- (b) 全包只有它的动画被丢。
→ 可在游戏里召唤/触发其它 GeckoLib 动画实体来区分。

---

## 6. 给自检 AI 的验证清单（按优先级）

**V1 · 决定性强（5 分钟）**
解压 `eden_power-r107.jar`，读取：
```
assets/eden_power/animations/earth_shock_pose.json
assets/eden_power/animations/frost_dragon_mist_chant.json
assets/eden_power/animations/instant_cast_pose.json
```
输出每个文件的：① 是否合法 JSON；② 顶层键集合；③ 是否含顶层对象 `animations`。
判定：任一文件无 `animations` → 假设 A 成立（Eden Power 高概率是元凶）。

**V2 · 决定性强（10 分钟）**
测试实例里移出 / 改名 `eden_power-r107.jar`，重启进入同一场景：
- 观察 ordertocook 顾客动画是否恢复；
- 观察 `Unable to find animation` 是否归零。
恢复 → 假设 A 强证据；不恢复 → Eden Power 不是（唯一）原因。

**V3 · 机制复核（15 分钟）**
用同一版本 GeckoLib 4.9.3 做最小复现：在任意模组资源包放
`assets/<ns>/animations/bad.json`，内容 `{"foo":1}`（无 `animations` 键），启动观察：
1. 是否只有 1 条 improper filename WARN；
2. 其它模组的 GeckoLib 动画是否整体失效（验证"整批中断"）；
3. 是否出现 `Error loading animation file` / reload 失败堆栈。
把 3 个观察点与第 2.3–2.5 节逐条对照。

**V4 · 内容归属（5 分钟）**
在 Eden Power jar 里 grep 字符串 `earth_shock_pose`、`instant_cast_pose`、
`frost_dragon_mist_chant`，确认是 Eden Power 代码读取的自研格式，
还是 GeckoLib 动画（若无人引用，进一步说明是"迷路文件"）。

**V5 · 日志完整性（5 分钟）**
在 `latest.log` 中搜 `Resource reload`、`reload failed`、`SimpleReloadInstance`、
`ReloadableResourceManager`，确认 GeckoLib 这次 reload 是否被标记失败，
以及 1.6 的"沉默"是否真实。

**V6 · 对照组（可选）**
Iron's Spells 有 7 个同样命名不合规的文件却没报故障：
- 若 V3 证明命名不合规无害 → 与结论一致；
- 若发现 Iron's Spells 也丢了动画但日志没记 → 需重新评估"整批中断"假设。

---

## 7. 反证条件（任一成立即推翻 / 修正本报告）

| 编号 | 条件 | 后果 |
|---|---|---|
| F1 | 3 个文件都有合法顶层 `animations` 对象 | Eden Power 不是元凶，只是命名不规范 |
| F2 | 移出 eden_power 后故障依旧 | Eden Power 不是原因，或不是唯一原因 |
| F3 | V3 显示"缺 animations 键"只影响该文件自己 | "整批中断"机制错误，第 2 / 4 节需推翻 |
| F4 | 日志能找到 GeckoLib reload 成功记录（ANIMATIONS 正常写入） | 故障另有来源，假设 A 不成立 |

---

## 8. 附：关键日志原文（可核对）

```
L288 [11:35:29.804] Reloading ResourceManager: vanilla, mod_resources, ... mod/geckolib, ... mod/ordertocook, ... mod/eden_power,mobends, ...
L289 [11:35:29.817] [GeckoLib/]: Found animation file with improper file name format; animation files should end in .animation.json: 'eden_power:animations/earth_shock_pose.json'
L291 [11:35:29.818] [GeckoLib/]: ... 'eden_power:animations/frost_dragon_mist_chant.json'
L293 [11:35:29.824] [GeckoLib/]: ... 'eden_power:animations/instant_cast_pose.json'
L378 [11:35:31.225] [com.yuyc.edenpower.Edenpower/]: 遗念水晶原模型已加载：71 个网格，754 个面，12.0 秒循环动画。
L383 [11:35:31.xxx] eden_power (version 1.0.99 -> 1.0.107)
L473 [11:35:39.473] [Render thread/ERROR] [GeckoLib/]: Unable to find animation: customer_sit_3 for CustomerEntity
L6083 [11:37:11.581] [Sodium-VertexConsumerTracker/]: Class com.yuyc.edenpower.impl.client.render.MyriadSwordsRenderer$AlphaConsumer ...
```

---

## 9. 可直接复制给自检 AI 的提示词

```
你收到一份关于 Minecraft 1.21.1 / NeoForge / GeckoLib 4.9.3 整合包里
"GeckoLib 动画缓存整体缺失"的调查报告（见上文）。
请独立复核，不要默认结论正确。重点：
1. 按第 6 节 V1 打开 eden_power-r107.jar，读那 3 个动画 JSON 的顶层结构，
   明确回答"是否含顶层 animations 对象"。
2. 判断本报告第 2 节的 GeckoLib 字节码结论是否成立：
   普通 Exception 会让汇总循环 join() 中断导致整批丢失，
   而 CompoundException 是逐文件隔离的。
3. 解释第 5 节疑点 1：为什么发生了"整批丢失"却没有任何 reload 异常堆栈？
4. 给出结论：Eden Power 是元凶 / 是嫌疑之一 / 无辜，并列出你的证据。
5. 如果发现报告有错，请指出具体是第几节的哪条断言错了。
```

---

*报告结束。核对了日志行号与 GeckoLib 4.9.3 字节码；Eden Power 3 个文件的顶层结构未打开 jar 验证。*
