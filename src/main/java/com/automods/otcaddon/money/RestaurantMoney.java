package com.automods.otcaddon.money;

import cn.breezeth.ordertocook.block.entity.OrderMachineBlockEntity;
import cn.breezeth.ordertocook.config.ConfigManager;
import cn.breezeth.ordertocook.core.RestaurantRegistry;
import cn.breezeth.ordertocook.util.CoinUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/** 金额在本体结算入口直接分流，不先塞进玩家背包再搬走。 */
public final class RestaurantMoney {
    private RestaurantMoney() { }
    public static boolean usesOtcCoins() {
        var config = ConfigManager.get();
        return !config.sdmShopCurrencyCompat && (config.customCurrencyItem == null || config.customCurrencyItem.isBlank());
    }
    private static List<MoneyJarBlockEntity> jars(ServerLevel level, BlockPos machine) {
        List<MoneyJarBlockEntity> result = new ArrayList<>();
        if (!level.isLoaded(machine) || !(level.getBlockEntity(machine) instanceof OrderMachineBlockEntity)) return result;
        // 以打单机为中心的 3×3×3 范围，包含斜角和上下方，不跨未加载区块。
        for (BlockPos pos : BlockPos.betweenClosed(machine.offset(-1, -1, -1), machine.offset(1, 1, 1))) {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof MoneyJarBlockEntity jar) result.add(jar);
        }
        result.sort(Comparator.comparingDouble((MoneyJarBlockEntity jar) -> jar.getBlockPos().distSqr(machine))
                .thenComparingLong(jar -> jar.getBlockPos().asLong()));
        return result;
    }
    public static void reward(ServerLevel world, Player player, int amount, CompoundTag order) {
        if (!usesOtcCoins()) { CoinUtils.giveCoins(player, amount); return; }
        ServerLevel source = null;
        BlockPos pos = null;
        if (order.contains("order_machine.pos_long") && order.contains("order_machine.dimension")) {
            ResourceLocation dimension = ResourceLocation.tryParse(order.getString("order_machine.dimension"));
            if (dimension != null) source = world.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
            pos = BlockPos.of(order.getLong("order_machine.pos_long"));
        }
        if (source != null && pos != null) give(source, pos, player, amount);
        else rewardById(world, player, amount, order.getInt("order_machine.id"));
    }
    public static void rewardById(ServerLevel world, Player player, int amount, int machineId) {
        var stats = machineId > 0 ? RestaurantRegistry.getStatsById(world, machineId) : null;
        ResourceLocation dimension = stats == null ? null : ResourceLocation.tryParse(stats.dimension());
        ServerLevel source = dimension == null ? null : world.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (source == null) CoinUtils.giveCoins(player, amount);
        else give(source, BlockPos.of(stats.posLong()), player, amount);
    }
    private static void give(ServerLevel level, BlockPos pos, Player player, int amount) {
        int rest = amount;
        if (usesOtcCoins()) for (MoneyJarBlockEntity jar : jars(level, pos)) {
            rest = jar.deposit(rest);
            if (rest <= 0) break;
        }
        if (rest > 0) CoinUtils.giveCoins(player, rest);
    }
    public static boolean consume(OrderMachineBlockEntity machine, Player player, int amount) {
        if (amount <= 0) return true;
        if (!usesOtcCoins() || !(machine.getLevel() instanceof ServerLevel level)) {
            return CoinUtils.tryConsumeWithChange(player, amount);
        }
        List<MoneyJarBlockEntity> jars = jars(level, machine.getBlockPos());
        long stored = jars.stream().mapToLong(MoneyJarBlockEntity::balance).sum();
        int fromJar = (int) Math.min(stored, amount);
        int fromPlayer = amount - fromJar;
        // 玩家余额不足时不修改罐子；扣款失败不会吞币。
        if (CoinUtils.countCoins(player) < fromPlayer) return false;
        if (fromPlayer > 0 && !CoinUtils.tryConsumeWithChange(player, fromPlayer)) return false;
        int remaining = fromJar;
        for (MoneyJarBlockEntity jar : jars) {
            if (remaining <= 0) break;
            int removed = jar.withdraw(remaining);
            remaining -= removed;
        }
        if (remaining < 0) give(level, machine.getBlockPos(), player, -remaining);
        return true;
    }
}
