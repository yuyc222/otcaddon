package com.automods.otcaddon.registry;

import com.automods.otcaddon.OtcAddon;
import com.automods.otcaddon.money.MoneyJarBlock;
import com.automods.otcaddon.money.MoneyJarBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AddonBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(OtcAddon.MOD_ID);
    public static final DeferredBlock<MoneyJarBlock> MONEY_JAR = BLOCKS.register("money_jar",
            () -> new MoneyJarBlock(BlockBehaviour.Properties.of().strength(1.5F).noOcclusion()));
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, OtcAddon.MOD_ID);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MoneyJarBlockEntity>> MONEY_JAR_ENTITY =
            ENTITIES.register("money_jar", () -> BlockEntityType.Builder.of(MoneyJarBlockEntity::new, MONEY_JAR.get()).build(null));
    private AddonBlocks() { }
    public static void register(IEventBus bus) { BLOCKS.register(bus); ENTITIES.register(bus); }
}
