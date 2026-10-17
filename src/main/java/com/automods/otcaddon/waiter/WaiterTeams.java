package com.automods.otcaddon.waiter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** 每台打单机只保存一名悦灵的已购等级；旧雇佣数量原值迁移为等级。 */
public final class WaiterTeams extends SavedData {
    private final Map<Long, Team> teams = new HashMap<>();
    private final Map<Long, UUID> activeWaiters = new HashMap<>();

    UUID activeAt(BlockPos pos) { return activeWaiters.get(pos.asLong()); }
    void setActive(BlockPos pos, UUID uuid) {
        if (!uuid.equals(activeWaiters.put(pos.asLong(), uuid))) setDirty();
    }

    public static WaiterTeams get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(WaiterTeams::new, WaiterTeams::load), "otcaddon_waiters");
    }

    private static WaiterTeams load(CompoundTag tag, HolderLookup.Provider registries) {
        WaiterTeams data = new WaiterTeams();
        ListTag list = tag.getList("Teams", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            long pos = entry.getLong("Pos");
            int rank = Math.max(0, Math.min(8, entry.getInt(entry.contains("Level") ? "Level" : "Hired")));
            int[] saved = entry.getIntArray("PaidCosts");
            int[] costs = new int[rank];
            for (int n = 0; n < rank; n++) costs[n] = n < saved.length ? Math.max(0, saved[n]) : 20 + 10 * n;
            UUID owner = null;
            try { owner = UUID.fromString(entry.getString("Owner")); } catch (IllegalArgumentException ignored) { }
            data.teams.put(pos, new Team(pos, rank, owner, costs));
            if (entry.hasUUID("ActiveWaiter")) data.activeWaiters.put(pos, entry.getUUID("ActiveWaiter"));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Team team : teams.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Pos", team.pos());
            entry.putInt("Level", team.rank());
            UUID active = activeWaiters.get(team.pos());
            if (active != null) entry.putUUID("ActiveWaiter", active);
            entry.putIntArray("PaidCosts", team.paidCosts());
            entry.putString("Owner", team.owner() == null ? "" : team.owner().toString());
            list.add(entry);
        }
        tag.put("Teams", list);
        return tag;
    }

    public boolean isEmpty() { return teams.isEmpty(); }
    public List<Team> snapshot() { return new ArrayList<>(teams.values()); }
    public int levelAt(BlockPos pos) {
        Team team = teams.get(pos.asLong());
        return team == null ? 0 : team.rank();
    }
    public UUID ownerAt(BlockPos pos) {
        Team team = teams.get(pos.asLong());
        return team == null ? null : team.owner();
    }
    public void setLevel(BlockPos pos, int rank, UUID owner) {
        int clamped = Math.max(0, Math.min(8, rank));
        Team old = teams.get(pos.asLong());
        int[] previous = old == null ? new int[0] : old.paidCosts();
        int[] costs = Arrays.copyOf(previous, clamped);
        for (int n = previous.length; n < clamped; n++) costs[n] = WaiterManager.hireCost(n);
        teams.put(pos.asLong(), new Team(pos.asLong(), clamped, owner, costs));
        setDirty();
    }
    public void remove(BlockPos pos) {
        activeWaiters.remove(pos.asLong());
        if (teams.remove(pos.asLong()) != null) setDirty();
    }
    public record Team(long pos, int rank, UUID owner, int[] paidCosts) {
        public BlockPos blockPos() { return BlockPos.of(pos); }
    }
}
