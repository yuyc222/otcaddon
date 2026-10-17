# Changelog

## 1.1.0

> Public release for Minecraft 1.21.1 / NeoForge. Everything below is new since **1.0.1**.

**New: Allay Waiter (button on the right of the Order Machine screen)**

- A new **Allay Waiter** button sits below "Rename restaurant" on the right of the Order Machine screen.
  The first click hires level 1, every later click upgrades one level. **One waiter per order machine**,
  the level can never exceed the restaurant level, up to level 8. Shift-dismiss is gone.
- **Cost**: hiring and every later level cost 100, 200, 400, 800, 1600, 3200, 6400 and 12800 oTc coins.
  Coins are taken from the Money Jar first, then from your own balance. Old saves convert their waiter
  count into purchased levels; extra waiters drop what they carry and are removed without extra refunds.
- **Level 1**: carries takeout bags / plates to **walk-in customers** (delivery, payment and coin rewards
  go through Order To Cook's own code path). It refuses to deliver while a dirty plate sits on the
  customer's current plate position, so nothing gets overwritten or paid out twice.
- **Level 2**: collects dirty plates and loads them into a dishwasher or washing table, and only goes
  for plates while the target can actually accept them.
- **Level 3**: washes plates at a washing table (4 seconds each, with water splashes), retrieves clean
  plates from dishwashers and returns empty plates to a plate shelf.
- **Levels 4-8**: progressively faster flight and quicker task picking; level 8 restores the old maximum.
  Levels 1-3 are deliberately slower, which looks much calmer.
- **Full carry flow**: the waiter flies to the counter, physically picks up the real bag/plate, holds it
  while flying and only delivers on arrival. Plates no longer travel through the owner's inventory.
- **Work range**: 24 blocks horizontally and 12 blocks vertically around the order machine, loaded chunks
  only. It finishes whatever it is already holding before taking new delivery, plate or washing jobs.
- Smooth position/rotation interpolation, plus a fix for rotation leaking between waiters that share
  animation state.
- Idle flight height lowered from 2.4 to 1.25 blocks above the order-machine coordinate, with bobbing
  reduced from 0.3 to 0.12 blocks.
- While the owner is offline or in another dimension the waiter only idles and keeps its items; if the
  order machine is removed the waiter leaves. Carried items and delivery targets are persisted.
- Plate shelves hold up to 18 plates; when there is no free slot the waiter keeps the empty plates in
  hand and waits instead of deleting or duplicating them.

**New: Money Jar (pink piggy bank)**

- **Crafting**: a chest in the centre of a crafting table with one brick above, below, left and right;
  it can also be found in the creative "Functional Blocks" tab.
- Item id `otcaddon:money_jar`, drawn with a proper pink piggy-bank OBJ model instead of a placeholder block.
- Right-click opens a nine-slot container that accepts **only oTc coins** (denominations 1, 5, 20, 100,
  10000). Ordinary items cannot be inserted.
- Place it within one block of an order machine (the 3×3×3 box around it, including diagonal and vertical
  neighbours) and it connects automatically, with **no extra binding step**.
- Order income, tips and special customer bonuses go **straight into the jar first**, without passing
  through the inventory, and are settled against the order machine that owns the order. Multiple jars are
  used in distance/coordinate order. Anything that does not fit follows Order To Cook's normal payout
  rules, and a full inventory is handled by the base mod.
- Restaurant upgrades, paid renaming, and waiter hiring/upgrading can all spend jar coins first, then the
  player's balance. If there is not enough money the jar is left untouched. Large-denomination change is
  supported and is returned to the jar first.
- Stored coins are saved with the world and drop when the jar is removed. Income is only written to loaded
  jars; otherwise the base mod's payout path is used.
- If the base mod uses a custom currency or SDM account currency, its own payment rules are kept and nothing
  is force-converted into oTc coins.

**Fixed: Order To Cook animation error flood and frozen customers**

- The actual cause is now understood: **the dependency was not missing an animation name, GeckoLib's loading
  loop was at fault.** GeckoLib bakes every mod's `assets/<namespace>/animations/*.json` in one loop with no
  per-file error handling, so a single unreadable file (for example a `.geo.json` model mistakenly placed in
  an `animations` folder, or malformed JSON) aborts that loop and silently discards every animation file
  after it. Order To Cook's customer and order-machine animations were among the discarded ones, so the log
  printed `Unable to find animation: ...` every frame and customers stood frozen.
- **Root-cause fix**: after each resource reload the mod re-checks GeckoLib's animation cache and re-bakes
  every missing animation file straight from the resource packs. Files that genuinely cannot be parsed are
  skipped individually and never affect the rest. It is reflection-only and fully guarded, so the worst case
  is "does nothing" rather than a crash.
- **Backstop**: the log filter drops entries containing `Unable to find animation:`, covering the gap before
  the cache is repaired. Other GeckoLib errors are still shown and never swallowed.
- Two old classes built on the wrong premise (they could never actually fire) were deleted, and their
  optional mixin config was removed.
- **No** changes to the dependency or to any other mod's files are required, and saves are unaffected.

**Improvements**

- The public version is now **1.1.0**: the jar file name (`otcaddon-1.1.0.jar`) and the in-game mod list
  show exactly the same number, with **no internal build suffix**.
- The network protocol version went from `6` to `9`, so an old server cannot silently ignore the new button.
- The minimum Order To Cook version is now explicit: **1.3.6**, verified against that release jar's
  interfaces and payment hooks.

**Requirements**

| Item | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| Loader | NeoForge 21.1.244+ |
| Required dependency | Order To Cook 1.3.6+ |
| Optional | JEI 19.57.0.449+ (needed for shift-click adding and missing-ingredient bookmarks) |

> When upgrading, delete the old jar from `.minecraft/mods` and keep only this one. On multiplayer, update
> the client and the server together, and back up existing worlds first. Passing code checks and packaging
> does not replace in-game testing; the real gameplay flow still needs a test run on a save copy.

---

## 1.0.1

**New: One-click bookmarks for missing ingredients (requires JEI)**

- In the Order To Cook **Takeout Counter** screen, clicking the **missing-ingredient query** button in the
  top-right corner now pins every missing dish of the current order into JEI's bookmark list (the pinned
  column on the left side of the JEI overlay), so you can gather ingredients from a checklist.
- Dishes you have already bookmarked are not added twice, and nothing is added when the order is not missing anything.
- Without JEI installed, clicking the button does not crash; it only shows a message at the top of the screen
  saying JEI is required.

**New: Automatic cleanup after a successful order**

- Once an order is **packed** (put into a takeout bag) or **plated successfully**, the mod removes the JEI
  bookmarks it added for that order, so you no longer have to unpin them one by one.
- **Only its own bookmarks are touched**: items you pinned yourself, or bookmarks created for another order,
  are left alone.
- **Failures never delete anything**: if packing/plating fails (missing ingredients, no plate, blocked output),
  the bookmarks stay so you can keep preparing.

**Improvements**

- Hint messages on the Takeout Counter screen are now drawn **above the GUI** instead of behind it
  (the Order Board hint was already on top).
- The public release version is now **1.0.1**: the jar file name (`otcaddon-1.0.1.jar`) and the version shown
  in the in-game mod list match exactly, with no internal build suffix.

**Requirements**

| Item | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| Loader | NeoForge 21.1.244+ |
| Required dependency | Order To Cook 1.3.x+ |
| Optional | JEI 19.57.0.449+ (needed for shift-click adding and missing-ingredient bookmarks) |

> When upgrading, delete the old jar from `.minecraft/mods` and keep only this one.

---

## 1.0.0

- First public release, for Minecraft 1.21.1 / NeoForge.
- Shift + left click a food item in JEI to add it directly to an Order Board menu.
- Connected counters share ingredients, packaging (leather) and clean plates.
- Added a glowing delivery arrow shown while holding a packed takeout bag, configurable via
  `config/otcaddon-client.toml`.
- Open source under the MIT license, by yuyc; credits note that development was AI-assisted.
