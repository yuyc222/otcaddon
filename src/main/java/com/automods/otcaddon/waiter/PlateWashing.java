package com.automods.otcaddon.waiter;

import cn.breezeth.ordertocook.block.entity.DishwasherBlockEntity;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** 复用本体放盘流程，但用独立的临时背包，绝不挪动老板背包中的东西。 */
final class PlateWashing {
    private static final ResourceLocation WASHING_TABLE =
            ResourceLocation.fromNamespaceAndPath("ordertocook", "washingtable");
    private static final GameProfile PROFILE = new GameProfile(
            UUID.fromString("3d0aa316-24d3-470a-81b4-cdb18f252a71"), "[OTC Waiter]");

    private static final ResourceLocation PLATE_SHELF =
            ResourceLocation.fromNamespaceAndPath("ordertocook", "plate_shelf");

    private PlateWashing() { }

    static boolean hasDirtySink(BlockEntity be) {
        if (be == null) return false;
        BlockState state = be.getBlockState();
        return WASHING_TABLE.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()))
                && state.getBlock().getStateDefinition().getProperty("plates") instanceof IntegerProperty plates
                && state.getValue(plates) > 0;
    }

    static boolean hasCleanOutput(BlockEntity be) {
        return be instanceof DishwasherBlockEntity dishwasher && dishwasher.canTakeCleanPlates();
    }

    static boolean canStoreClean(BlockState state) {
        return PLATE_SHELF.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()))
                && state.getBlock().getStateDefinition().getProperty("plates") instanceof IntegerProperty plates
                && state.getValue(plates) < 18;
    }

    static void finishSink(ServerLevel level, AllayWaiter waiter) {
        BlockEntity be = level.getBlockEntity(waiter.jobPos);
        ItemStack clean = OtcItems.cleanPlateStack();
        if (!hasDirtySink(be) || clean.isEmpty() || !waiter.carriedItem().isEmpty()) return;
        BlockState state = be.getBlockState();
        IntegerProperty plates = (IntegerProperty) state.getBlock().getStateDefinition().getProperty("plates");
        if (level.setBlock(waiter.jobPos, state.setValue(plates, state.getValue(plates) - 1), 3)) {
            waiter.setCarriedItem(clean);
        }
    }

    static void returnClean(ServerLevel level, AllayWaiter waiter) {
        BlockState state = level.getBlockState(waiter.jobPos);
        if (!canStoreClean(state) || !OtcItems.isCleanPlate(waiter.carriedItem())) return;
        IntegerProperty plates = (IntegerProperty) state.getBlock().getStateDefinition().getProperty("plates");
        int count = Math.min(18 - state.getValue(plates), waiter.carriedItem().getCount());
        if (level.setBlock(waiter.jobPos, state.setValue(plates, state.getValue(plates) + count), 3)) {
            ItemStack rest = waiter.carriedItem().copy();
            rest.shrink(count);
            waiter.setCarriedItem(rest);
        }
    }

    static void takeClean(ServerLevel level, AllayWaiter waiter) {
        if (!waiter.carriedItem().isEmpty()) return;
        if (!(level.getBlockEntity(waiter.jobPos) instanceof DishwasherBlockEntity washer)
                || !washer.canTakeCleanPlates()) return;
        FakePlayer carrier = FakePlayerFactory.get(level, PROFILE);
        carrier.getInventory().clearContent();
        carrier.setPos(Vec3.atCenterOf(waiter.jobPos));
        try {
            washer.takeCleanPlates(carrier);
        } finally {
            // 独立背包里只可能有这次取出的餐盘；先交给悦灵再清空，异常也不丢盘。
            for (int i = 0; i < carrier.getInventory().getContainerSize(); i++) {
                ItemStack stack = carrier.getInventory().getItem(i);
                if (stack.isEmpty()) continue;
                if (waiter.carriedItem().isEmpty()) waiter.setCarriedItem(stack);
                else net.minecraft.world.level.block.Block.popResource(level, waiter.jobPos, stack.copy());
            }
            carrier.getInventory().clearContent();
        }
    }

    /** 餐盘架不是方块实体，先筛含餐盘架的区块分段，避免每次逐格扫整家餐厅。 */
    static net.minecraft.core.BlockPos findShelf(ServerLevel level, net.minecraft.core.BlockPos home,
                                                net.minecraft.core.BlockPos center) {
        net.minecraft.core.BlockPos best = null;
        double distance = Double.MAX_VALUE;
        for (int cx = (home.getX() - 24) >> 4; cx <= (home.getX() + 24) >> 4; cx++) {
            for (int cz = (home.getZ() - 24) >> 4; cz <= (home.getZ() + 24) >> 4; cz++) {
                var chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (int sy = (home.getY() - 12) >> 4; sy <= (home.getY() + 12) >> 4; sy++) {
                    int index = chunk.getSectionIndex(sy << 4);
                    if (index < 0 || index >= chunk.getSections().length) continue;
                    var section = chunk.getSection(index);
                    if (!section.maybeHas(PlateWashing::canStoreClean)) continue;
                    for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                        if (!canStoreClean(section.getBlockState(x, y, z))) continue;
                        var pos = new net.minecraft.core.BlockPos((cx << 4) + x, (sy << 4) + y, (cz << 4) + z);
                        if (Math.abs(pos.getX() - home.getX()) > 24 || Math.abs(pos.getZ() - home.getZ()) > 24
                                || Math.abs(pos.getY() - home.getY()) > 12) continue;
                        double d = pos.distSqr(center);
                        if (d < distance) { distance = d; best = pos; }
                    }
                }
            }
        }
        return best;
    }

    static boolean canAccept(BlockEntity be) {
        if (be instanceof DishwasherBlockEntity dishwasher) return dishwasher.canInsertDirtyPlates();
        if (be == null) return false;
        BlockState state = be.getBlockState();
        return WASHING_TABLE.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()))
                && state.getBlock().getStateDefinition().getProperty("plates") instanceof IntegerProperty plates
                && state.getValue(plates) < 10;
    }

    static void insert(ServerLevel level, BlockEntity be, AllayWaiter waiter) {
        if (!canAccept(be) || waiter.plates() <= 0) return;
        FakePlayer carrier = FakePlayerFactory.get(level, PROFILE);
        carrier.getInventory().clearContent();
        carrier.getInventory().selected = 0;
        carrier.getAbilities().instabuild = false;
        carrier.setPos(Vec3.atCenterOf(be.getBlockPos()));
        ItemStack offered = waiter.carriedItem().copy();
        carrier.getInventory().setItem(0, offered);
        try {
            if (be instanceof DishwasherBlockEntity dishwasher) {
                dishwasher.insertDirtyPlates(carrier);
            } else {
                // 洗碗池每次只收一只；容量和放盘音效全部交给本体处理。
                be.getBlockState().useWithoutItem(level, carrier,
                        new BlockHitResult(Vec3.atCenterOf(be.getBlockPos()), Direction.UP, be.getBlockPos(), false));
            }
        } finally {
            // 只扣实际被设备收下的数量，设备满了或忙碌时剩余盘子仍在悦灵手里。
            waiter.setCarriedItem(offered);
            carrier.getInventory().clearContent();
        }
    }
}
