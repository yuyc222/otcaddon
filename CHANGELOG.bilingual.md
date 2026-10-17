# 更新日志 · 1.1.0 / Changelog · 1.1.0

**中文 English 双语 / Bilingual（相对 1.0.1 / Changes since 1.0.1）**

适用版本 / Target：Minecraft 1.21.1 · NeoForge 21.1.244+ · 前置 Order To Cook 1.3.6+
发布文件 / Release file：`otcaddon-1.1.0.jar`

---

## 简体中文

### 新增：悦灵服务员（打单机右侧按钮）

- 打单机界面右侧新增**悦灵服务员按钮**。第一次点击雇佣 1 级，之后每次点击升一级；
  **每台打单机最多一只**，等级不得超过餐厅等级，最高 8 级。
- **费用**：100、200、400、800、1600、3200、6400、12800 otc 币（雇佣 + 逐级升级），
  优先从储钱罐扣款，不足再扣玩家身上的币。旧存档的雇佣数量会折算成已购等级。
- **1 级**送打包袋 / 餐盘给到店顾客，顾客摆盘位置有脏盘时不送；
  **2 级**回收脏餐盘送入洗碗机或洗碗池；**3 级**在洗碗池洗碗、取回洗碗机净盘、把空盘放回餐盘架；
  **4~8 级**逐级提速，8 级恢复到旧版最高速度。
- 完整搬运流程：先飞到餐点旁取走真实物品、拿在手上飞过去，到顾客手边才交付；放盘不再借用老板背包。
- 移动和转身平滑过渡，修复多名悦灵朝向互相干扰；待命高度降到打单机上方 1.25 格。
- 老板离线 / 换维度时只待命并保留物品；打单机被拆悦灵自动离开；手持物品和目标随存档保存。

### 新增：储钱罐（粉色小猪）

- **合成**：工作台中心放一个箱子，上下左右各一块红砖；创造模式「功能方块」里也有。
- 9 格容器，**只收 otc 币**（面额 1、5、20、100、10000），右键打开。
- 放在打单机周围一格内（3×3×3，含上下和斜角）自动连接，不需要绑定。
- 订单收入、小费、顾客特殊奖励**直接优先入罐**，不经过背包；多个罐子按距离依次使用，
  放不下的部分按本体规则交给玩家。
- 打单机升级、收费改名、悦灵雇佣和升级都能优先用罐内货币，不足再补背包，余额不够不扣罐内的钱；
  支持大面额找零，找零优先放回罐内。
- 罐内货币随世界保存，拆罐掉币；本体用自定义货币或 SDM 账户时保持本体规则。

### 修复：前置顾客动画报错刷屏、顾客僵住不动

- 真正原因：不是前置缺动画名，而是 GeckoLib 用一个不带逐文件容错的循环烘焙所有模组的动画文件；
  任意一个文件解不开，后面的动画文件就整批被静默丢弃，Order To Cook 的顾客动画和打单机动画正在其中。
- **治本**：资源加载完成后自动检查 GeckoLib 动画缓存，把缺失的动画文件重新烘焙补回；
  坏掉的文件单独跳过，全部走反射 + 异常保护，最坏只是「不生效」，不会崩溃。
- **兜底**：日志过滤器拦掉「Unable to find animation:」条目，其他 GeckoLib 报错照常显示。
- 删除了两个建立在错误前提上、从来不会生效的旧类；不需要改前置或别的模组的文件，不影响存档。

### 改进

- 发布版本号整理为 **1.1.0**：jar 文件名与游戏内模组信息完全一致，不再带内部构建号。
- 网络协议版本从 `6` 升到 `9`（1.1.0 开发期间的按钮、储钱罐和送餐逻辑改动都要求新旧端一致），
  避免新客户端配旧服务端时按钮没反应。
- 前置最低版本明确为 **Order To Cook 1.3.6**。

### 环境要求

| 项目 | 版本 |
| --- | --- |
| Minecraft | 1.21.1 |
| 加载器 | NeoForge 21.1.244+ |
| 前置模组 | Order To Cook（下单了）1.3.6+（必需） |
| 可选模组 | JEI 19.57.0.449+（一键加菜、缺料一键收藏） |

> 升级时请删掉 `.minecraft/mods` 里的旧 jar，只保留 `otcaddon-1.1.0.jar`；多人游戏客户端和服务端一起更新；
> 进旧存档前先备份。类型检查和打包通过不代表游戏内一定正常，实际流程仍需在存档副本里验证。

---

## English

### New: Allay Waiter (button on the right of the Order Machine screen)

- A new **Allay Waiter** button on the right of the Order Machine screen. First click hires level 1, each
  later click upgrades one level; **one waiter per order machine**, capped by the restaurant level, max 8.
- **Cost**: 100, 200, 400, 800, 1600, 3200, 6400 and 12800 oTc coins (hire plus each upgrade), taken from
  the Money Jar first and then from your balance. Old saves convert their waiter count into purchased levels.
- **Level 1** delivers bags/plates to walk-in customers and refuses to deliver while a dirty plate sits on
  the customer's plate position; **level 2** collects dirty plates into a dishwasher or washing table;
  **level 3** washes at a washing table, retrieves dishwasher output and returns empty plates to a plate
  shelf; **levels 4-8** get progressively faster, with level 8 restoring the old maximum speed.
- Full carry flow: the waiter flies to the counter, physically picks up the real item, holds it while flying
  and only delivers on arrival. Plates no longer travel through the owner's inventory.
- Smooth movement/rotation interpolation and a fix for rotation leaking between waiters; idle flight height
  lowered to 1.25 blocks above the order machine.
- While the owner is offline or in another dimension the waiter only idles and keeps its items; it leaves
  when the order machine is removed; carried items and targets are persisted.

### New: Money Jar (pink piggy bank)

- **Crafting**: a chest in the centre of a crafting table with one brick above, below, left and right; also
  available in the creative "Functional Blocks" tab.
- Nine-slot container that accepts **only oTc coins** (denominations 1, 5, 20, 100, 10000), opened by right-click.
- Place it within one block of an order machine (the 3×3×3 box including diagonal and vertical neighbours)
  and it connects automatically, with no binding step.
- Order income, tips and special customer bonuses go **straight into the jar first**, without passing through
  the inventory; multiple jars are used in distance order and anything that does not fit follows the base
  mod's normal payout rules.
- Restaurant upgrades, paid renaming and waiter hiring/upgrading can spend jar coins first, then the player's
  balance; if there is not enough money the jar is left untouched. Large-denomination change is returned to
  the jar first.
- Coins are saved with the world and drop when the jar is removed. A custom currency or SDM account currency
  keeps the base mod's own rules.

### Fixed: Order To Cook animation error flood and frozen customers

- The real cause is not a missing animation name: GeckoLib bakes every mod's animation files in one loop with
  no per-file error handling, so a single unreadable file makes it discard every animation after it. Order To
  Cook's customer and order-machine animations were among the discarded ones.
- **Root-cause fix**: after each resource reload the mod re-checks GeckoLib's animation cache and re-bakes
  every missing file. Broken files are skipped individually; reflection-only and fully guarded, so the worst
  case is "does nothing" rather than a crash.
- **Backstop**: the log filter drops `Unable to find animation:` entries while other GeckoLib errors are still shown.
- Two old classes built on the wrong premise (and which could never fire) were deleted. No changes to the
  dependency or other mods are required, and saves are unaffected.

### Improvements

- The public version is now **1.1.0**: the jar file name and the in-game mod list match exactly, with no
  internal build suffix.
- The network protocol version went from `6` to `9` (the button, money jar and delivery changes in 1.1.0
  all require matching client and server), so an old server cannot silently ignore the new button.
- The minimum dependency is now explicit: **Order To Cook 1.3.6**.

### Requirements

| Item | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| Loader | NeoForge 21.1.244+ |
| Required dependency | Order To Cook 1.3.6+ |
| Optional | JEI 19.57.0.449+ (shift-click adding, missing-ingredient bookmarks) |

> When upgrading, delete the old jar from `.minecraft/mods` and keep only `otcaddon-1.1.0.jar`; on
> multiplayer update client and server together, and back up existing worlds. Passing code checks and
> packaging does not replace in-game testing; the real gameplay flow still needs a test run on a save copy.
