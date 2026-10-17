package com.automods.otcaddon.compat;

import cn.breezeth.ordertocook.api.IngredientSourceCompatApi;
import cn.breezeth.ordertocook.api.MenuFoodCompatApi;
import cn.breezeth.ordertocook.api.RestaurantSupplyCompatApi;
import com.automods.otcaddon.OtcAddon;
import com.automods.otcaddon.api.AddonApi;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.NeoForge;

/** 把整合包食物目录和相连操作台库存接入 OrderToCook 的公开兼容接口。 */
public final class OrderToCookCompat {
    private static final ResourceLocation COUNTERTOP_ID =
            ResourceLocation.fromNamespaceAndPath("ordertocook", "countertop");
    private static final ResourceLocation CLEAN_PLATE_ID =
            ResourceLocation.fromNamespaceAndPath("ordertocook", "clean_plate");
    private static final String BOARD_MENU_CLASS = "cn.breezeth.ordertocook.screen.BoardScreenHandler";
    private static final int MAX_CONNECTED_COUNTERTOPS = 256;
    private static final String[] CONNECTOR_KEYWORDS = {"sink", "dishwasher", "stove", "furnace", "fridge", "refrigerator"};

    private OrderToCookCompat() {
    }

    public static void register() {
        AddonApi.registerMenuRule("registered_foods", 100, OrderToCookCompat::evaluateFood);
        AddonApi.registerIngredientSource("connected_countertops", 100, new ConnectedIngredientSource());
        AddonApi.registerSupplySource("connected_countertops", 100, new ConnectedSupplySource());
    }

    private static MenuFoodCompatApi.Result evaluateFood(ItemStack stack, MenuFoodCompatApi.Context context) {
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food == null) {
            food = stack.getFoodProperties(null);
        }
        return food != null && food.nutrition() > 0
                ? MenuFoodCompatApi.Result.allow(food.nutrition())
                : MenuFoodCompatApi.Result.pass();
    }

    private static boolean isCountertop(Level level, BlockPos pos) {
        return COUNTERTOP_ID.equals(BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()));
    }

    private static boolean isConnector(Level level, BlockPos pos) {
        String path = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath().toLowerCase();
        for (String keyword : CONNECTOR_KEYWORDS) if (path.contains(keyword)) return true;
        return false;
    }

    private static Container countertopInventory(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof Container container && isCountertop(level, pos) ? container : null;
    }

    private static Set<BlockPos> connectedCountertops(Level level, BlockPos start) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        Set<BlockPos> countertops = new HashSet<>();
        queue.add(start.immutable());
        visited.add(start.immutable());
        while (!queue.isEmpty() && visited.size() <= MAX_CONNECTED_COUNTERTOPS) {
            BlockPos pos = queue.removeFirst();
            if (isCountertop(level, pos)) countertops.add(pos);
            if (!isCountertop(level, pos) && !isConnector(level, pos)) continue;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos next = pos.relative(direction);
                if (visited.add(next) && (isCountertop(level, next) || isConnector(level, next))) queue.addLast(next);
            }
        }
        return countertops;
    }

    /** 操作台和四类设备共同组成连通区域，但库存只从操作台方块实体读取。 */
    private static boolean isConnectedIdleCountertop(Level level, BlockPos start) {
        Container own = countertopInventory(level, start);
        if (own == null || own.getContainerSize() < 13 || !own.getItem(0).isEmpty()) return false;
        for (BlockPos pos : connectedCountertops(level, start)) {
            Container inventory = countertopInventory(level, pos);
            if (inventory != null && inventory.getContainerSize() >= 13 && !inventory.getItem(0).isEmpty()) return true;
        }
        return false;
    }

    private static int countConnected(Level level, BlockPos start, ResourceLocation wanted) {
        int total = 0;
        for (BlockPos pos : connectedCountertops(level, start)) {
            Container inventory = countertopInventory(level, pos);
            if (inventory != null) total += countItem(inventory, wanted);
        }
        return total;
    }

    private static int extractConnected(Level level, BlockPos start, ResourceLocation wanted, int amount, boolean simulate) {
        int moved = 0;
        for (BlockPos pos : connectedCountertops(level, start)) {
            Container inventory = countertopInventory(level, pos);
            if (inventory != null) moved += extractItem(inventory, wanted, amount - moved, simulate);
            if (moved >= amount) break;
        }
        return moved;
    }

    private static int countItem(Container inventory, ResourceLocation wanted) {
        int total = 0;
        for (int slot = 1; slot < Math.min(13, inventory.getContainerSize()); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && wanted.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static int extractItem(Container inventory, ResourceLocation wanted, int amount, boolean simulate) {
        int moved = 0;
        for (int slot = 1; slot < Math.min(13, inventory.getContainerSize()) && moved < amount; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !wanted.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) continue;
            int take = Math.min(stack.getCount(), amount - moved);
            moved += take;
            if (!simulate) stack.shrink(take);
        }
        if (moved > 0 && !simulate) inventory.setChanged();
        return moved;
    }

    private static final class ConnectedIngredientSource implements IngredientSourceCompatApi.Handler {
        @Override
        public IngredientSourceCompatApi.Probe probe(Level level, BlockPos pos) {
            return isConnectedIdleCountertop(level, pos)
                    ? IngredientSourceCompatApi.Probe.SOURCE
                    : IngredientSourceCompatApi.Probe.NOT_SOURCE;
        }

        @Override
        public int count(Level level, BlockPos pos, String itemId) {
            ResourceLocation wanted = ResourceLocation.tryParse(itemId);
            return wanted == null ? 0 : countConnected(level, pos, wanted);
        }

        @Override
        public int extract(Level level, BlockPos pos, String itemId, int amount, boolean simulate) {
            ResourceLocation wanted = ResourceLocation.tryParse(itemId);
            return wanted == null || amount <= 0
                    ? 0 : extractConnected(level, pos, wanted, amount, simulate);
        }
    }

    private static final class ConnectedSupplySource implements RestaurantSupplyCompatApi.Handler {
        @Override
        public RestaurantSupplyCompatApi.Probe probe(Level level, BlockPos pos,
                                                      RestaurantSupplyCompatApi.SupplyType type) {
            if (!isConnectedIdleCountertop(level, pos)) return RestaurantSupplyCompatApi.Probe.NOT_SOURCE;
            return type == RestaurantSupplyCompatApi.SupplyType.PACKAGING
                    || type == RestaurantSupplyCompatApi.SupplyType.CLEAN_PLATE
                    ? RestaurantSupplyCompatApi.Probe.SOURCE
                    : RestaurantSupplyCompatApi.Probe.NOT_SOURCE;
        }

        @Override
        public int count(Level level, BlockPos pos, RestaurantSupplyCompatApi.SupplyType type) {
            ResourceLocation wanted = type == RestaurantSupplyCompatApi.SupplyType.PACKAGING
                    ? BuiltInRegistries.ITEM.getKey(Items.LEATHER) : CLEAN_PLATE_ID;
            return countConnected(level, pos, wanted);
        }

        @Override
        public int extract(Level level, BlockPos pos, RestaurantSupplyCompatApi.SupplyType type,
                           int amount, boolean simulate) {
            if (amount <= 0) return 0;
            ResourceLocation wanted = type == RestaurantSupplyCompatApi.SupplyType.PACKAGING
                    ? BuiltInRegistries.ITEM.getKey(Items.LEATHER) : CLEAN_PLATE_ID;
            return extractConnected(level, pos, wanted, amount, simulate);
        }
    }
}
