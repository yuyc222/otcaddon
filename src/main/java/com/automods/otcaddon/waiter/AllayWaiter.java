package com.automods.otcaddon.waiter;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** 非生物服务员：服务端持有真实物品，客户端仅平滑展示原版悦灵。 */
public class AllayWaiter extends Entity {
    static final int JOB_NONE = 0;
    static final int JOB_PICKUP = 1;
    static final int JOB_COLLECT = 2;
    static final int JOB_WASH = 3;
    static final int JOB_DELIVER = 4;
    static final int JOB_SCRUB = 5;
    static final int JOB_RETURN = 6;
    static final int JOB_TAKE_CLEAN = 7;
    private int serviceLevel = 1;
    int workTicks;

    void setServiceLevel(int rank) { serviceLevel = Math.max(1, Math.min(8, rank)); }
    int serviceLevel() { return serviceLevel; }
    int responseTicks() { return 10 + (8 - serviceLevel) * 10; }
    double flySpeed() { return FLY_SPEED * (0.4D + (serviceLevel - 1) * 0.6D / 7.0D); }

    private static final EntityDataAccessor<ItemStack> CARRIED_ITEM =
            SynchedEntityData.defineId(AllayWaiter.class, EntityDataSerializers.ITEM_STACK);
    private static final double FLY_SPEED = 0.26D;
    private static final double HOVER_RADIUS = 1.7D;
    static final double HOVER_HEIGHT = 1.25D;

    private BlockPos home = BlockPos.ZERO;
    private int hoverSeed;
    private Vec3 moveTarget;
    int job = JOB_NONE;
    BlockPos jobPos = BlockPos.ZERO;
    UUID customerId;
    int jobCooldownTicks;

    // 每个实体自己保存动画进度，不能依赖渲染器上一帧画的是哪只悦灵。
    private int lerpSteps;
    private double lerpX, lerpY, lerpZ;
    private float lerpYaw, lerpPitch;
    private float holdingProgress, oldHoldingProgress;

    public AllayWaiter(EntityType<? extends AllayWaiter> type, Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
        setInvulnerable(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(CARRIED_ITEM, ItemStack.EMPTY);
    }

    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean isInvulnerableTo(DamageSource source) { return true; }
    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override public boolean fireImmune() { return true; }
    @Override public boolean isPushedByFluid() { return false; }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            if (lerpSteps > 0) {
                lerpPositionAndRotationStep(lerpSteps, lerpX, lerpY, lerpZ, lerpYaw, lerpPitch);
                lerpSteps--;
            }
            oldHoldingProgress = holdingProgress;
            holdingProgress = Mth.approach(holdingProgress, carriedItem().isEmpty() ? 0.0F : 1.0F, 0.2F);
            return;
        }
        if (level() instanceof net.minecraft.server.level.ServerLevel server && server.isLoaded(home)) {
            WaiterTeams data = WaiterTeams.get(server);
            UUID active = data.activeAt(home);
            if (!(server.getBlockEntity(home) instanceof cn.breezeth.ordertocook.block.entity.OrderMachineBlockEntity)
                    || data.levelAt(home) <= 0 || (active != null && !active.equals(getUUID()))) {
                discard();
                return;
            }
        }
        if (hoverSeed == 0) hoverSeed = Math.floorMod(getId(), 7) + 1;
        fly();
    }

    /** 把网络位置拆成连续的小步；基础 Entity 的实现会直接跳到新位置。 */
    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYaw = yaw;
        lerpPitch = pitch;
        lerpSteps = Math.max(1, steps);
    }

    public float holdingProgress(float partialTick) {
        return Mth.lerp(partialTick, oldHoldingProgress, holdingProgress);
    }

    private void fly() {
        Vec3 target = moveTarget != null ? moveTarget : hoverPosition();
        Vec3 difference = target.subtract(position());
        double distance = difference.length();
        if (distance < 0.02D) {
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        // 靠近目标时减速，避免停下和掉头都像撞墙。
        double step = Math.min(flySpeed(), distance * 0.4D);
        Vec3 velocity = difference.scale(step / distance);
        setDeltaMovement(velocity);
        setPos(position().add(velocity));
        if (velocity.horizontalDistanceSqr() > 1.0E-5D) {
            float yaw = (float) (Mth.atan2(velocity.z, velocity.x) * (180.0D / Math.PI)) - 90.0F;
            setYRot(Mth.approachDegrees(getYRot(), yaw, 18.0F));
        }
    }

    private Vec3 hoverPosition() {
        long tick = level().getGameTime();
        double angle = tick * 0.02D + hoverSeed * 1.7D;
        return new Vec3(home.getX() + 0.5D + Math.cos(angle) * HOVER_RADIUS,
                home.getY() + HOVER_HEIGHT + Math.sin(tick * 0.07D + hoverSeed) * 0.12D,
                home.getZ() + 0.5D + Math.sin(angle) * HOVER_RADIUS);
    }

    void setHome(BlockPos pos) { home = pos.immutable(); }
    BlockPos home() { return home; }
    boolean belongsTo(String tag) { return getTags().contains(tag); }
    void setMoveTarget(Vec3 target) { moveTarget = target; }

    void startJob(int kind, BlockPos pos, UUID customer) {
        job = kind;
        jobPos = pos.immutable();
        customerId = customer;
        jobCooldownTicks = 0;
        workTicks = 0;
    }

    void clearJob(int cooldownTicks) {
        job = JOB_NONE;
        jobPos = BlockPos.ZERO;
        customerId = null;
        jobCooldownTicks = Math.max(responseTicks(), cooldownTicks);
        workTicks = 0;
        moveTarget = null;
    }

    public ItemStack carriedItem() { return entityData.get(CARRIED_ITEM); }

    void setCarriedItem(ItemStack stack) {
        // 不原地修改同步物品，否则客户端收不到数量或内容变化。
        entityData.set(CARRIED_ITEM, stack.copy());
    }

    int plates() { return OtcItems.isDirtyPlate(carriedItem()) ? carriedItem().getCount() : 0; }

    void dropCarriedItem() {
        if (carriedItem().isEmpty()) return;
        spawnAtLocation(carriedItem().copy());
        setCarriedItem(ItemStack.EMPTY);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        home = BlockPos.of(tag.getLong("HomePos"));
        hoverSeed = tag.getInt("HoverSeed");
        ItemStack carried = tag.contains("CarriedItem")
                ? ItemStack.parseOptional(registryAccess(), tag.getCompound("CarriedItem"))
                : OtcItems.dirtyPlateStack(Math.max(0, tag.getInt("CarriedPlates")));
        setCarriedItem(carried);
        setServiceLevel(tag.getInt("ServiceLevel"));
        if (!carried.isEmpty() && !OtcItems.isDirtyPlate(carried) && !OtcItems.isCleanPlate(carried)) {
            job = JOB_DELIVER;
            jobPos = BlockPos.of(tag.getLong("PickupPos"));
            customerId = tag.hasUUID("Customer") ? tag.getUUID("Customer") : null;
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong("HomePos", home.asLong());
        tag.putInt("HoverSeed", hoverSeed);
        tag.putInt("ServiceLevel", serviceLevel);
        if (!carriedItem().isEmpty()) tag.put("CarriedItem", carriedItem().save(registryAccess()));
        tag.putLong("PickupPos", jobPos.asLong());
        if (customerId != null) tag.putUUID("Customer", customerId);
    }

    /** 合并旧服务员或拆机时退回手上的物品；卸载区块则由存档保存。 */
    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide() && (reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED)) {
            dropCarriedItem();
        }
        super.remove(reason);
    }
}
