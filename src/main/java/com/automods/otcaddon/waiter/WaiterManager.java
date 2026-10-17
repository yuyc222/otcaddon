package com.automods.otcaddon.waiter;

import cn.breezeth.ordertocook.block.entity.FoodPlateBlockEntity;
import cn.breezeth.ordertocook.block.entity.OrderMachineBlockEntity;
import cn.breezeth.ordertocook.block.entity.TakeoutBagBlockEntity;
import cn.breezeth.ordertocook.core.ModConstants;
import cn.breezeth.ordertocook.item.TakeoutBagItem;
import cn.breezeth.ordertocook.screen.OrderMachineScreenHandler;
import com.automods.otcaddon.money.RestaurantMoney;
import cn.breezeth.ordertocook.core.OrderNpcManager;
import com.automods.otcaddon.OtcAddon;
import com.automods.otcaddon.network.AddonNetwork;
import com.automods.otcaddon.registry.AddonEntities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * 悦灵服务员的服务器端大脑：雇佣、召唤、派活、干活。
 *
 * <p>每个已加载的维度每 tick 检查一次所有雇佣队伍，做三件事：</p>
 * <ol>
 *   <li>每台打单机只保留一名服务员，已购等级决定工作能力；</li>
 *   <li>给空闲的服务员找活：把操作台上做好的打包袋／餐盘送到点了单的到店顾客手里；</li>
 *   <li>回收吃完的脏餐盘，送进最近能收盘的洗碗机或洗碗池。</li>
 * </ol>
 *
 * <p>交付动作直接调用 OrderToCook 本体的 {@link TakeoutBagItem#trySubmitDineInFromEntityUse}，
 * 和玩家自己右键顾客是同一条代码路径：结算、发币、顾客吃饭动画、送客都由本体负责，
 * 附属只是替玩家把东西送过去。放盘也复用本体交互，但不再借用老板的背包。</p>
 */
@EventBusSubscriber(modid = OtcAddon.MOD_ID)
public final class WaiterManager {
    /** 找打包袋、餐盘、顾客、洗碗机的水平半径。 */
    static final int SEARCH_RADIUS = 24;
    /** 垂直搜索范围。 */
    private static final int SEARCH_HEIGHT = 12;
    /** 两次"找活"之间的最小间隔（tick）。 */
    private static final int DECISION_INTERVAL = 10;
    /** 飞到这个距离以内才算到位。 */
    private static final double ARRIVE_DISTANCE = 0.45D;
    /** 单只服务员最高 8 级，且不超过餐厅等级。 */
    private static final int HARD_MAX = 8;
    /** 首次雇佣 100 币，后续升级逐级翻倍。 */
    private static final int HIRE_BASE_COST = 100;

    /** 玩家点击雇佣／升级；旧解雇动作已拒绝。 */
    public static final int ACTION_HIRE = 0;

    // 回包里的结果码，和客户端提示一一对应。
    public static final int RESULT_SYNC = 0;
    public static final int RESULT_HIRED = 1;
    public static final int RESULT_MAX = 3;
    public static final int RESULT_NO_COIN = 4;
    public static final int RESULT_LOCKED = 5;
    public static final int RESULT_UNAVAILABLE = 6;

    /** OrderToCook 给顾客打的订单标记前缀。 */
    private static final String ORDER_TAG_PREFIX = "otc_order:";
    /** 服务员实体身上的队伍标记前缀，后面跟打单机坐标。 */
    private static final String TEAM_TAG_PREFIX = "otcaddon_waiter:";

    private WaiterManager() {
    }

    /** 每名悦灵服务员的服务上限 = 餐厅等级（本体等级范围 0～8）。 */
    public static int maxWaiters(OrderMachineBlockEntity machine) {
        return Math.max(0, Math.min(HARD_MAX, machine.getRestaurantLevel()));
    }

    /** 下一级费用：100 × 2^当前已购等级；0 表示首次雇佣。 */
    public static int hireCost(int hired) {
        return HIRE_BASE_COST << Math.max(0, Math.min(HARD_MAX, hired));
    }

    static String teamTag(BlockPos machinePos) {
        return TEAM_TAG_PREFIX + machinePos.asLong();
    }

    // ———————————————————————————— 雇佣 / 升级 ————————————————————————————

    /** 打开打单机界面时把当前雇佣情况推给客户端，按钮上就能显示 n/上限。 */
    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getContainer() instanceof OrderMachineScreenHandler menu)) return;
        OrderMachineBlockEntity machine = menu.getMachineIfServer();
        if (machine == null || !(machine.getLevel() instanceof ServerLevel level)) return;
        sendStatus(level, machine, player, event.getContainer().containerId, RESULT_SYNC);
    }

    /** 处理一次「雇佣／升级」请求。只认玩家当前打开的那台打单机，防伪造。 */
    public static void handleAction(ServerPlayer player, int action) {
        if (!(player.containerMenu instanceof OrderMachineScreenHandler menu)) return;
        OrderMachineBlockEntity machine = menu.getMachineIfServer();
        if (machine == null || !(machine.getLevel() instanceof ServerLevel level)) {
            sendStatusUnavailable(player);
            return;
        }
        BlockPos pos = machine.getBlockPos();
        WaiterTeams data = WaiterTeams.get(level);
        int max = maxWaiters(machine);
        if (!menu.stillValid(player) || action != ACTION_HIRE) return;
        String restaurantOwner = machine.snapshotStats().owner();
        if (restaurantOwner != null && !restaurantOwner.isBlank()
                && !restaurantOwner.equals(player.getGameProfile().getName())) {
            sendStatus(level, machine, player, menu.containerId, RESULT_UNAVAILABLE);
            return;
        }
        int hired = data.levelAt(pos);
        int result;
        if (max <= 0) {
            result = RESULT_LOCKED;
        } else if (hired >= max) {
            result = RESULT_MAX;
        } else {
            int cost = hireCost(hired);
            if (!RestaurantMoney.consume(machine, player, cost)) {
                result = RESULT_NO_COIN;
            } else {
                data.setLevel(pos, hired + 1, player.getUUID());
                result = RESULT_HIRED;
            }
        }
        sendStatus(level, machine, player, menu.containerId, result);
    }

    private static void sendStatus(ServerLevel level, OrderMachineBlockEntity machine, ServerPlayer player,
                                   int containerId, int result) {
        int max = maxWaiters(machine);
        int hired = Math.min(WaiterTeams.get(level).levelAt(machine.getBlockPos()), max);
        AddonNetwork.sendWaiterStatus(player, containerId, hired, max, hireCost(hired), result);
    }

    /** 打单机读不到（Order To Cook 版本对不上）时，也让客户端给一句提示。 */
    private static void sendStatusUnavailable(ServerPlayer player) {
        AddonNetwork.sendWaiterStatus(player, player.containerMenu.containerId, 0, 0, 0, RESULT_UNAVAILABLE);
    }

    // ———————————————————————————— 每帧维护 ————————————————————————————

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            tick(level);
            if (level.getGameTime() % 20 == 0) {
                for (ServerPlayer player : level.players()) {
                    if (player.containerMenu instanceof OrderMachineScreenHandler menu) {
                        OrderMachineBlockEntity machine = menu.getMachineIfServer();
                        if (machine != null) sendStatus(level, machine, player, menu.containerId, RESULT_SYNC);
                    }
                }
            }
        }
    }

    private static void tick(ServerLevel level) {
        WaiterTeams data = WaiterTeams.get(level);
        if (data.isEmpty()) return;
        for (WaiterTeams.Team team : data.snapshot()) {
            BlockPos machinePos = team.blockPos();
            if (!level.isLoaded(machinePos)) continue;
            if (!(level.getBlockEntity(machinePos) instanceof OrderMachineBlockEntity machine)) {
                // 打单机没了（被拆/换维度），服务员跟着回家。
                despawnAll(level, machinePos);
                data.remove(machinePos);
                continue;
            }
            int max = maxWaiters(machine);
            // 餐厅降级只限制当前能力，不抹掉已经付费的等级。
            int rank = Math.min(data.levelAt(machinePos), max);
            int want = data.levelAt(machinePos) > 0 ? 1 : 0;
            List<AllayWaiter> waiters = findWaiters(level, machinePos);
            UUID active = data.activeAt(machinePos);
            waiters.sort(java.util.Comparator.comparingInt(w -> w.getUUID().equals(active) ? 0 : 1));
            while (waiters.size() < want) {
                AllayWaiter spawned = spawn(level, machinePos);
                if (spawned == null) break;
                waiters.add(spawned);
            }
            while (waiters.size() > want) {
                waiters.remove(waiters.size() - 1).discard();
            }
            if (!waiters.isEmpty()) data.setActive(machinePos, waiters.getFirst().getUUID());
            Player owner = resolveOwner(level, data, machine, machinePos);
            for (AllayWaiter waiter : waiters) {
                waiter.setServiceLevel(rank);
                tickWaiter(level, waiter, rank > 0 ? owner : null, machinePos);
            }
        }
    }

    private static List<AllayWaiter> findWaiters(ServerLevel level, BlockPos machinePos) {
        String tag = teamTag(machinePos);
        AABB box = new AABB(machinePos).inflate(SEARCH_RADIUS + 16, SEARCH_HEIGHT + 8, SEARCH_RADIUS + 16);
        return new ArrayList<>(level.getEntitiesOfClass(AllayWaiter.class, box, waiter -> waiter.belongsTo(tag)));
    }

    private static AllayWaiter spawn(ServerLevel level, BlockPos machinePos) {
        AllayWaiter waiter = AddonEntities.ALLAY_WAITER.get().create(level);
        if (waiter == null) return null;
        waiter.setHome(machinePos);
        waiter.addTag(teamTag(machinePos));
        waiter.moveTo(machinePos.getX() + 0.5D, machinePos.getY() + AllayWaiter.HOVER_HEIGHT, machinePos.getZ() + 0.5D, 0.0F, 0.0F);
        level.addFreshEntity(waiter);
        return waiter;
    }

    private static void despawnAll(ServerLevel level, BlockPos machinePos) {
        for (AllayWaiter waiter : findWaiters(level, machinePos)) {
            waiter.discard();
        }
    }

    /** 奖励发给餐厅老板；老板不在线就先歇着（不然没人收钱）。 */
    private static Player resolveOwner(ServerLevel level, WaiterTeams data, OrderMachineBlockEntity machine,
                                       BlockPos machinePos) {
        MinecraftServer server = level.getServer();
        if (server == null) return null;
        UUID ownerId = data.ownerAt(machinePos);
        if (ownerId != null) {
            ServerPlayer online = server.getPlayerList().getPlayer(ownerId);
            if (online != null) return online;
        }
        String ownerName;
        try {
            ownerName = machine.snapshotStats().owner();
        } catch (Throwable error) {
            ownerName = null;
        }
        if (ownerName != null && !ownerName.isBlank()) {
            return server.getPlayerList().getPlayerByName(ownerName);
        }
        return null;
    }

    // ———————————————————————————— 单个服务员的任务 ————————————————————————————

    private static void tickWaiter(ServerLevel level, AllayWaiter waiter, Player owner, BlockPos machinePos) {
        if (waiter.jobCooldownTicks > 0) {
            waiter.jobCooldownTicks--;
        }
        if (owner == null || owner.level() != level) {
            // 老板离线/换维度时停工，但保留手上的物品和任务。
            waiter.setMoveTarget(null);
            return;
        }
        if ((waiter.serviceLevel() < 2 && (waiter.job == AllayWaiter.JOB_COLLECT || waiter.job == AllayWaiter.JOB_WASH))
                || (waiter.serviceLevel() < 3 && waiter.job >= AllayWaiter.JOB_SCRUB)) {
            waiter.clearJob(DECISION_INTERVAL);
        }
        switch (waiter.job) {
            case AllayWaiter.JOB_PICKUP -> tickPickup(level, waiter);
            case AllayWaiter.JOB_DELIVER -> tickDeliver(level, waiter, owner);
            case AllayWaiter.JOB_COLLECT -> tickCollect(level, waiter);
            case AllayWaiter.JOB_WASH -> tickWash(level, waiter);
            case AllayWaiter.JOB_SCRUB -> tickScrub(level, waiter);
            case AllayWaiter.JOB_RETURN -> tickReturn(level, waiter);
            case AllayWaiter.JOB_TAKE_CLEAN -> tickTakeClean(level, waiter);
            default -> {
                if (owner != null && waiter.jobCooldownTicks <= 0
                        && level.getGameTime() % DECISION_INTERVAL == Math.floorMod(waiter.getId(), DECISION_INTERVAL)) {
                    assignJob(level, waiter, machinePos);
                }
            }
        }
    }

    /** 找活：先把手里的脏盘子洗掉，再送热菜，最后收脏盘子。 */
    private static void assignJob(ServerLevel level, AllayWaiter waiter, BlockPos machinePos) {
        waiter.jobCooldownTicks = waiter.responseTicks();
        List<BlockEntity> nearby = blockEntities(level, machinePos);
        if (OtcItems.isCleanPlate(waiter.carriedItem())) {
            if (waiter.serviceLevel() >= 3) {
                BlockPos shelf = PlateWashing.findShelf(level, machinePos, waiter.blockPosition());
                if (shelf != null) waiter.startJob(AllayWaiter.JOB_RETURN, shelf, null);
            }
            return;
        }
        if (waiter.plates() > 0) {
            if (waiter.serviceLevel() < 2) return;
            BlockPos washer = findDishwasher(nearby, waiter.blockPosition());
            if (washer != null) {
                waiter.startJob(AllayWaiter.JOB_WASH, washer, null);
            }
            return;
        }
        if (!waiter.carriedItem().isEmpty()) {
            waiter.dropCarriedItem();
            waiter.clearJob(100);
            return;
        }
        List<AllayWaiter> teammates = findWaiters(level, machinePos);
        ReadyItem ready = findReadyItem(level, machinePos, nearby, teammates);
        if (ready != null) {
            waiter.startJob(AllayWaiter.JOB_PICKUP, ready.pos(), ready.customer());
            return;
        }
        if (waiter.serviceLevel() >= 2 && findDishwasher(nearby, machinePos) != null) {
            BlockPos dirty = findDirtyPlate(nearby, waiter.blockPosition(), teammates);
            if (dirty != null) {
                waiter.startJob(AllayWaiter.JOB_COLLECT, dirty, null);
                return;
            }
        }
        if (waiter.serviceLevel() >= 3 && PlateWashing.findShelf(level, machinePos, waiter.blockPosition()) != null) {
            BlockEntity task = nearby.stream().filter(be -> PlateWashing.hasDirtySink(be) || PlateWashing.hasCleanOutput(be))
                    .min(java.util.Comparator.comparingDouble(be -> be.getBlockPos().distSqr(waiter.blockPosition())))
                    .orElse(null);
            if (task != null) {
                waiter.startJob(PlateWashing.hasDirtySink(task) ? AllayWaiter.JOB_SCRUB : AllayWaiter.JOB_TAKE_CLEAN,
                        task.getBlockPos(), null);
                return;
            }
        }
        waiter.setMoveTarget(null);
        waiter.jobCooldownTicks = waiter.responseTicks();
    }

    private static LivingEntity customer(ServerLevel level, AllayWaiter waiter) {
        return waiter.customerId != null && level.getEntity(waiter.customerId) instanceof LivingEntity npc
                && npc.isAlive() ? npc : null;
    }

    private static boolean matchesOrder(ServerLevel level, ItemStack stack, LivingEntity npc) {
        CompoundTag nbt = customTag(stack);
        return npc != null && nbt != null && !nbt.contains(ModConstants.NBT_DELIVERY_POS)
                && !isExpired(level, nbt) && !nbt.getString(ModConstants.NBT_ORDER_ID).isBlank()
                && nbt.getString(ModConstants.NBT_ORDER_ID).equals(orderIdOf(npc));
    }

    private static boolean approach(AllayWaiter waiter, Vec3 target) {
        waiter.setMoveTarget(target);
        return waiter.position().distanceToSqr(target) <= ARRIVE_DISTANCE * ARRIVE_DISTANCE;
    }

    private static Vec3 pickupPosition(BlockPos pos) {
        return Vec3.atBottomCenterOf(pos).add(0.0D, 0.35D, 0.0D);
    }

    private static void tickPickup(ServerLevel level, AllayWaiter waiter) {
        if (!level.isLoaded(waiter.jobPos)) {
            waiter.clearJob(DECISION_INTERVAL);
            return;
        }
        ItemStack ready = readyStack(level.getBlockEntity(waiter.jobPos));
        if (ready.isEmpty() || !matchesOrder(level, ready, customer(level, waiter))
                || dirtyAtCustomer(level, customer(level, waiter))) {
            waiter.clearJob(DECISION_INTERVAL);
            return;
        }
        if (!approach(waiter, pickupPosition(waiter.jobPos))) return;
        ItemStack stack = takeReadyItem(level, waiter.jobPos);
        if (stack.isEmpty()) {
            waiter.clearJob(DECISION_INTERVAL);
            return;
        }
        waiter.setCarriedItem(stack);
        waiter.startJob(AllayWaiter.JOB_DELIVER, waiter.jobPos, waiter.customerId);
        level.playSound(null, waiter.blockPosition(), SoundEvents.ALLAY_ITEM_TAKEN, SoundSource.NEUTRAL, 0.8F, 1.1F);
    }

    private static void tickDeliver(ServerLevel level, AllayWaiter waiter, Player owner) {
        LivingEntity npc = customer(level, waiter);
        ItemStack carried = waiter.carriedItem();
        if (carried.isEmpty() || !matchesOrder(level, carried, npc)) {
            waiter.dropCarriedItem();
            waiter.clearJob(100);
            return;
        }
        // 停在顾客前方、手边高度，而不是悬在顾客头顶。
        Vec3 target = npc.position().add(npc.getLookAngle().x * 0.65D,
                Math.min(0.8D, npc.getBbHeight() * 0.5D), npc.getLookAngle().z * 0.65D);
        if (!approach(waiter, target)) return;
        if (dirtyAtCustomer(level, npc)) {
            // 取餐后有人放了脏盘：将原餐放回取餐处掉落，不覆盖脏盘，也不结算。
            net.minecraft.world.level.block.Block.popResource(level, waiter.jobPos, carried.copy());
            waiter.setCarriedItem(ItemStack.EMPTY);
            waiter.clearJob(100);
            return;
        }
        ItemStack stack = carried.copy();
        InteractionResult result;
        try {
            result = TakeoutBagItem.trySubmitDineInFromEntityUse(level, owner, stack, npc, null);
        } catch (RuntimeException | LinkageError error) {
            OtcAddon.LOGGER.warn("悦灵服务员送餐失败（Order To Cook 版本可能不匹配）", error);
            result = InteractionResult.FAIL;
        }
        // 本体会消耗交付的物品，按实际剩余量更新，避免凭空删除或重复掉落。
        waiter.setCarriedItem(stack);
        if (result.consumesAction()) {
            level.playSound(null, npc.blockPosition(), SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.NEUTRAL, 0.9F, 1.0F);
            waiter.dropCarriedItem();
            waiter.clearJob(DECISION_INTERVAL);
        } else {
            waiter.dropCarriedItem();
            waiter.clearJob(100);
        }
    }

    private static void tickCollect(ServerLevel level, AllayWaiter waiter) {
        if (!level.isLoaded(waiter.jobPos)) {
            waiter.clearJob(DECISION_INTERVAL);
            return;
        }
        BlockEntity be = level.getBlockEntity(waiter.jobPos);
        if (!(be instanceof FoodPlateBlockEntity plate) || plate.isEatingSequenceActive()
                || !OtcItems.isDirtyPlate(plate.getPlateStack())) {
            waiter.clearJob(DECISION_INTERVAL);
            return;
        }
        if (!approach(waiter, pickupPosition(waiter.jobPos))) return;
        // 先把真实盘子转移到手上再拆方块，避免拆方块时再掉一份。
        ItemStack dirty = plate.getPlateStack().copy();
        plate.setPlateStack(ItemStack.EMPTY);
        level.removeBlock(waiter.jobPos, false);
        waiter.setCarriedItem(dirty);
        level.playSound(null, waiter.jobPos, SoundEvents.ALLAY_ITEM_TAKEN, SoundSource.NEUTRAL, 0.8F, 1.1F);
        waiter.clearJob(5);
    }

    private static void tickWash(ServerLevel level, AllayWaiter waiter) {
        if (waiter.plates() <= 0 || !level.isLoaded(waiter.jobPos)) {
            waiter.clearJob(DECISION_INTERVAL);
            return;
        }
        BlockEntity washer = level.getBlockEntity(waiter.jobPos);
        if (!PlateWashing.canAccept(washer)) {
            waiter.clearJob(DECISION_INTERVAL * 4);
            return;
        }
        Vec3 target = Vec3.atBottomCenterOf(waiter.jobPos).add(0.0D, 1.05D, 0.0D);
        if (!approach(waiter, target)) return;
        int before = waiter.plates();
        try {
            PlateWashing.insert(level, washer, waiter);
        } catch (RuntimeException | LinkageError error) {
            OtcAddon.LOGGER.warn("悦灵服务员放盘失败，保留尚未放入的脏餐盘", error);
            waiter.clearJob(100);
            return;
        }
        if (waiter.plates() < before) {
            level.playSound(null, waiter.jobPos, SoundEvents.ALLAY_THROW, SoundSource.NEUTRAL, 0.8F, 1.0F);
        }
        waiter.clearJob(DECISION_INTERVAL);
    }

    private static boolean dirtyAtCustomer(ServerLevel level, LivingEntity npc) {
        if (npc == null) return false;
        BlockPos pos = OrderNpcManager.getCustomerPlateDisplayPos(npc);
        return pos != null && level.getBlockEntity(pos) instanceof FoodPlateBlockEntity plate
                && OtcItems.isDirtyPlate(plate.getPlateStack());
    }

    private static void tickScrub(ServerLevel level, AllayWaiter waiter) {
        if (!level.isLoaded(waiter.jobPos) || !PlateWashing.hasDirtySink(level.getBlockEntity(waiter.jobPos))
                || !waiter.carriedItem().isEmpty()) {
            waiter.clearJob(DECISION_INTERVAL);
            return;
        }
        if (!approach(waiter, Vec3.atBottomCenterOf(waiter.jobPos).add(0, 1.05, 0))) return;
        if (++waiter.workTicks % 20 == 0) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.BUBBLE_POP,
                    waiter.jobPos.getX() + 0.5, waiter.jobPos.getY() + 1.0, waiter.jobPos.getZ() + 0.5,
                    8, 0.2, 0.05, 0.2, 0.01);
            level.playSound(null, waiter.jobPos, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.BLOCKS, 0.22F, 1.0F);
        }
        if (waiter.workTicks < 80) return;
        PlateWashing.finishSink(level, waiter);
        waiter.clearJob(DECISION_INTERVAL);
    }

    private static void tickReturn(ServerLevel level, AllayWaiter waiter) {
        if (!level.isLoaded(waiter.jobPos) || !PlateWashing.canStoreClean(level.getBlockState(waiter.jobPos))) {
            waiter.clearJob(DECISION_INTERVAL);
            return;
        }
        if (!approach(waiter, pickupPosition(waiter.jobPos))) return;
        PlateWashing.returnClean(level, waiter);
        waiter.clearJob(DECISION_INTERVAL);
    }

    private static void tickTakeClean(ServerLevel level, AllayWaiter waiter) {
        if (!level.isLoaded(waiter.jobPos) || !PlateWashing.hasCleanOutput(level.getBlockEntity(waiter.jobPos))) {
            waiter.clearJob(DECISION_INTERVAL);
            return;
        }
        if (!approach(waiter, pickupPosition(waiter.jobPos))) return;
        PlateWashing.takeClean(level, waiter);
        waiter.clearJob(DECISION_INTERVAL);
    }

    // ———————————————————————————— 找目标 ————————————————————————————

    /** 一份做好的成品，以及该交给哪位顾客。 */
    private record ReadyItem(BlockPos pos, UUID customer) {
    }

    private static boolean reserved(List<AllayWaiter> teammates, BlockPos pos, UUID customer) {
        for (AllayWaiter other : teammates) {
            if ((other.job == AllayWaiter.JOB_PICKUP || other.job == AllayWaiter.JOB_COLLECT)
                    && pos.equals(other.jobPos)) return true;
            if (customer != null && customer.equals(other.customerId)) return true;
        }
        return false;
    }

    private static ReadyItem findReadyItem(ServerLevel level, BlockPos machinePos, List<BlockEntity> nearby,
                                           List<AllayWaiter> teammates) {
        Map<String, LivingEntity> waiting = waitingCustomers(level, machinePos);
        if (waiting.isEmpty()) return null;
        ReadyItem best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockEntity be : nearby) {
            ItemStack stack = readyStack(be);
            if (stack.isEmpty()) continue;
            CompoundTag nbt = customTag(stack);
            if (nbt == null) continue;
            String orderId = nbt.getString(ModConstants.NBT_ORDER_ID);
            if (orderId.isBlank()) continue;
            // 外卖单（带 delivery_pos）不接：那要老板自己骑摩托送。
            if (nbt.contains(ModConstants.NBT_DELIVERY_POS)) continue;
            if (isExpired(level, nbt)) continue;
            LivingEntity npc = waiting.get(orderId);
            if (npc == null || dirtyAtCustomer(level, npc) || reserved(teammates, be.getBlockPos(), npc.getUUID())) continue;
            double distance = be.getBlockPos().distSqr(machinePos);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = new ReadyItem(be.getBlockPos(), npc.getUUID());
            }
        }
        return best;
    }

    /** 已经点了单、正等着上菜的到店顾客：订单号 → 顾客实体。 */
    private static Map<String, LivingEntity> waitingCustomers(ServerLevel level, BlockPos machinePos) {
        Map<String, LivingEntity> waiting = new HashMap<>();
        AABB box = new AABB(machinePos).inflate(SEARCH_RADIUS, SEARCH_HEIGHT, SEARCH_RADIUS);
        for (LivingEntity npc : level.getEntitiesOfClass(LivingEntity.class, box, entity -> orderIdOf(entity) != null)) {
            String orderId = orderIdOf(npc);
            if (orderId != null) {
                waiting.putIfAbsent(orderId, npc);
            }
        }
        return waiting;
    }

    /** 最近的、现在能收盘子的洗碗机或洗碗池。 */
    private static BlockPos findDishwasher(List<BlockEntity> nearby, BlockPos center) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockEntity be : nearby) {
            if (!PlateWashing.canAccept(be)) continue;
            double distance = be.getBlockPos().distSqr(center);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = be.getBlockPos().immutable();
            }
        }
        return best;
    }

    /** 最近的、已经吃完的脏餐盘。 */
    private static BlockPos findDirtyPlate(List<BlockEntity> nearby, BlockPos center, List<AllayWaiter> teammates) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockEntity be : nearby) {
            if (!(be instanceof FoodPlateBlockEntity plate) || plate.isEatingSequenceActive()
                    || !OtcItems.isDirtyPlate(plate.getPlateStack()) || reserved(teammates, be.getBlockPos(), null)) continue;
            double distance = be.getBlockPos().distSqr(center);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = be.getBlockPos().immutable();
            }
        }
        return best;
    }

    /** 操作台正上方的成品：打包袋或还没动过的餐盘（正在吃的、脏的都不算）。 */
    private static ItemStack readyStack(BlockEntity be) {
        if (be instanceof TakeoutBagBlockEntity bag) {
            return bag.getBagStack();
        }
        if (be instanceof FoodPlateBlockEntity plate) {
            if (plate.isEatingSequenceActive()) return ItemStack.EMPTY;
            ItemStack stack = plate.getPlateStack();
            return OtcItems.isDirtyPlate(stack) ? ItemStack.EMPTY : stack;
        }
        return ItemStack.EMPTY;
    }

    /**
     * 拿走一份成品，并把装着它的方块一起拆掉（和玩家右键取走是同一种表现）。
     * 拆之前先清空方块实体，避免方块自己把东西再掉一份出来。
     */
    private static ItemStack takeReadyItem(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        ItemStack stack = ItemStack.EMPTY;
        if (be instanceof TakeoutBagBlockEntity bag && !bag.getBagStack().isEmpty()) {
            stack = bag.getBagStack().copy();
            bag.setBagStack(ItemStack.EMPTY);
        } else if (be instanceof FoodPlateBlockEntity plate) {
            ItemStack inPlate = plate.getPlateStack();
            if (!inPlate.isEmpty() && !plate.isEatingSequenceActive() && !OtcItems.isDirtyPlate(inPlate)) {
                stack = inPlate.copy();
                plate.setPlateStack(ItemStack.EMPTY);
            }
        }
        if (!stack.isEmpty()) {
            level.removeBlock(pos, false);
        }
        return stack;
    }

    /** 扫描打单机附近的方块实体（只读已加载区块，不触发加载）。 */
    private static List<BlockEntity> blockEntities(ServerLevel level, BlockPos center) {
        List<BlockEntity> found = new ArrayList<>();
        int minChunkX = (center.getX() - SEARCH_RADIUS) >> 4;
        int maxChunkX = (center.getX() + SEARCH_RADIUS) >> 4;
        int minChunkZ = (center.getZ() - SEARCH_RADIUS) >> 4;
        int maxChunkZ = (center.getZ() + SEARCH_RADIUS) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) continue;
                for (Object value : chunk.getBlockEntities().values()) {
                    if (!(value instanceof BlockEntity be)) continue;
                    BlockPos pos = be.getBlockPos();
                    if (Math.abs(pos.getX() - center.getX()) > SEARCH_RADIUS) continue;
                    if (Math.abs(pos.getZ() - center.getZ()) > SEARCH_RADIUS) continue;
                    if (Math.abs(pos.getY() - center.getY()) > SEARCH_HEIGHT) continue;
                    found.add(be);
                }
            }
        }
        return found;
    }

    // ———————————————————————————— 小工具 ————————————————————————————

    private static CompoundTag customTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.copyTag();
    }

    private static boolean isExpired(ServerLevel level, CompoundTag nbt) {
        if (nbt.contains(ModConstants.NBT_EXPIRY_TICK)) {
            return level.getGameTime() >= nbt.getLong(ModConstants.NBT_EXPIRY_TICK);
        }
        if (nbt.contains(ModConstants.NBT_EXPIRY_TIME)) {
            return System.currentTimeMillis() >= nbt.getLong(ModConstants.NBT_EXPIRY_TIME);
        }
        return true;
    }

    /** 顾客身上的订单号：看自己、看他坐的椅子、看坐在他身上的（女仆兼容）。 */
    private static String orderIdOf(Entity entity) {
        String own = orderTagOf(entity);
        if (own != null) return own;
        Entity vehicle = entity.getVehicle();
        if (vehicle != null) {
            String byVehicle = orderTagOf(vehicle);
            if (byVehicle != null) return byVehicle;
        }
        for (Entity passenger : entity.getPassengers()) {
            String byPassenger = orderTagOf(passenger);
            if (byPassenger != null) return byPassenger;
        }
        return null;
    }

    private static String orderTagOf(Entity entity) {
        for (String tag : entity.getTags()) {
            if (tag.startsWith(ORDER_TAG_PREFIX)) {
                return tag.substring(ORDER_TAG_PREFIX.length());
            }
        }
        return null;
    }
}
