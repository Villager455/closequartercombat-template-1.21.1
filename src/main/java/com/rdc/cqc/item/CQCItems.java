package com.rdc.cqc.item;

import com.rdc.cqc.CloseQuarterCombat;
import com.rdc.cqc.entity.ThrownGrenadeEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class CQCItems
{
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(CloseQuarterCombat.MODID);

    // ============================
    //         ПРОТИГАЗ
    // ============================
    // Звичайний предмет (не броня) у слот HEAD. Не дає захисту,
    // але блокують зілля та малюють оверлей (див. CQCEvents та CloseQuarterCombatClient).

    public static final DeferredItem<Item> GAS_MASK = ITEMS.register(
            "gas_mask",
            () -> new GasMaskItem(new Item.Properties().stacksTo(1))
    );

    // ============================
    //      ІНГРЕДІЄНТИ КРАФТУ
    // ============================
    // Прості предмети без власної логіки. Використовуються як компоненти майбутніх рецептів гранат.

    public static final DeferredItem<Item> FUSE = ITEMS.register(
            "fuse",
            () -> new Item(new Item.Properties())
    );

    public static final DeferredItem<Item> CONTACT_DETONATOR = ITEMS.register(
            "contact_detonator",
            () -> new Item(new Item.Properties())
    );

    public static final DeferredItem<Item> ACTIVE_DETONATOR = ITEMS.register(
            "active_detonator",
            () -> new ActiveDetonatorItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredItem<Item> REMOTE_DETONATOR = ITEMS.register(
            "remote_detonator",
            () -> new Item(new Item.Properties())
    );

    public static final DeferredItem<Item> EXPLOSIVE_GRENADE_PART = ITEMS.register(
            "explosive_grenade_part",
            () -> new Item(new Item.Properties())
    );

    public static final DeferredItem<Item> HEAT_GRENADE_PART = ITEMS.register(
            "heat_grenade_part",
            () -> new Item(new Item.Properties())
    );

    // ============================
    //          ГРАНАТИ
    // ============================
    // Кидаються з ПКМ. Через 5 секунд вибух, газова хмара або дим.
    // Логіка фізики/детонації — у ThrownGrenadeEntity. Stack-розмір = 1 (не стакаються).

    public static final DeferredItem<Item> FRAG_GRENADE = ITEMS.register(
            "frag_grenade",
            () -> new GrenadeItem(
                    new Item.Properties().stacksTo(1),
                    ThrownGrenadeEntity.Type.FRAG_GRENADE
            )
    );

    public static final DeferredItem<Item> AIRBURST_FRAG_GRENADE = ITEMS.register(
            "airburst_frag_grenade",
            () -> new GrenadeItem(
                    new Item.Properties().stacksTo(1),
                    ThrownGrenadeEntity.Type.AIRBURST_FRAG_GRENADE
            )
    );

    public static final DeferredItem<Item> HIGH_EXPLOSIVE_GRENADE = ITEMS.register(
            "high_explosive_grenade",
            () -> new GrenadeItem(
                    new Item.Properties().stacksTo(1),
                    ThrownGrenadeEntity.Type.HIGH_EXPLOSIVE_GRENADE
            )
    );

    public static final DeferredItem<Item> SAPPER_BAG = ITEMS.register(
            "sapper_bag",
            () -> new GrenadeItem(
                    new Item.Properties().stacksTo(1),
                    ThrownGrenadeEntity.Type.SAPPER_BAG
            )
    );

    public static final DeferredItem<Item> SMALL_GRENADE = ITEMS.register(
            "small_grenade",
            () -> new GrenadeItem(
                    new Item.Properties().stacksTo(3),
                    ThrownGrenadeEntity.Type.SMALL_GRENADE
            )
    );

    public static final DeferredItem<Item> DYNAMITE_STICK = ITEMS.register(
            "dynamite_stick",
            () -> new GrenadeItem(
                    new Item.Properties().stacksTo(9),
                    ThrownGrenadeEntity.Type.DYNAMITE_STICK
            )
    );

    public static final DeferredItem<Item> REMOTE_DYNAMITE_BUNDLE = ITEMS.register(
            "remote_dynamite_bundle",
            () -> new GrenadeItem(
                    new Item.Properties().stacksTo(1),
                    ThrownGrenadeEntity.Type.REMOTE_DYNAMITE_BUNDLE
            )
    );

    public static final DeferredItem<Item> IMPROVISED_GRENADE = ITEMS.register(
            "improvised_grenade",
            () -> new GrenadeItem(
                    new Item.Properties().stacksTo(3),
                    ThrownGrenadeEntity.Type.IMPROVISED_GRENADE
            )
    );

    public static final DeferredItem<Item> MOLOTOV = ITEMS.register(
            "molotov",
            () -> new GrenadeItem(
                    new Item.Properties().stacksTo(1),
                    ThrownGrenadeEntity.Type.MOLOTOV
            )
    );

    public static final DeferredItem<Item> INCENDIARY_GRENADE = ITEMS.register(
            "incendiary_grenade",
            () -> new GrenadeItem(
                    new Item.Properties().stacksTo(1),
                    ThrownGrenadeEntity.Type.INCENDIARY_GRENADE
            )
    );

    public static final DeferredItem<Item> CLUSTER_GRENADE = ITEMS.register(
            "cluster_grenade",
            () -> new GrenadeItem(
                    new Item.Properties().stacksTo(1),
                    ThrownGrenadeEntity.Type.CLUSTER_GRENADE
            )
    );

    public static final DeferredItem<Item> FLASHBANG_GRENADE = ITEMS.register(
            "flashbang_grenade",
            () -> new GrenadeItem(
                    new Item.Properties().stacksTo(1),
                    ThrownGrenadeEntity.Type.FLASHBANG_GRENADE
            )
    );

    public static final DeferredItem<Item> IMPACT_GRENADE = ITEMS.register(
            "impact_grenade",
            () -> new GrenadeItem(
                    new Item.Properties().stacksTo(1),
                    ThrownGrenadeEntity.Type.IMPACT_GRENADE
            )
    );

    public static final DeferredItem<Item> SHAPED_CHARGE_GRENADE = ITEMS.register(
            "heat_grenade",
            () -> new GrenadeItem(
                    new Item.Properties().stacksTo(1),
                    ThrownGrenadeEntity.Type.SHAPED_CHARGE_GRENADE
            )
    );

    public static final DeferredItem<Item> IMPACT_MINE = ITEMS.register(
            "impact_mine",
            () -> new ImpactMineItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredItem<Item> HEAT_LAUNCHER = ITEMS.register(
            "heat_launcher",
            () -> new HeatLauncherItem(new Item.Properties().stacksTo(1), ThrownGrenadeEntity.Type.HEAT_PROJECTILE)
    );

    public static final DeferredItem<Item> LARGE_HEAT_LAUNCHER = ITEMS.register(
            "large_heat_launcher",
            () -> new HeatLauncherItem(new Item.Properties().stacksTo(1), ThrownGrenadeEntity.Type.LARGE_HEAT_PROJECTILE)
    );

    public static final DeferredItem<Item> HIGH_EXPLOSIVE_LAUNCHER = ITEMS.register(
            "high_explosive_launcher",
            () -> new HeatLauncherItem(new Item.Properties().stacksTo(1), ThrownGrenadeEntity.Type.HIGH_EXPLOSIVE_PROJECTILE)
    );

    public static final DeferredItem<Item> INCENDIARY_LAUNCHER = ITEMS.register(
            "incendiary_launcher",
            () -> new HeatLauncherItem(new Item.Properties().stacksTo(1), ThrownGrenadeEntity.Type.INCENDIARY_PROJECTILE)
    );

    public static final DeferredItem<Item> FRAG_LAUNCHER = ITEMS.register(
            "frag_launcher",
            () -> new HeatLauncherItem(new Item.Properties().stacksTo(1), ThrownGrenadeEntity.Type.FRAG_PROJECTILE)
    );

    public static final DeferredItem<Item> EMPTY_LAUNCH_TUBE = ITEMS.register(
            "empty_launch_tube",
            () -> new Item(new Item.Properties().stacksTo(1))
    );

    public static final DeferredItem<Item> MAGNETIC_GRENADE = ITEMS.register(
            "magnetic_grenade",
            () -> new GrenadeItem(
                    new Item.Properties().stacksTo(1),
                    ThrownGrenadeEntity.Type.MAGNETIC_GRENADE
            )
    );

    public static final DeferredItem<Item> STICKY_GRENADE = ITEMS.register(
            "sticky_grenade",
            () -> new GrenadeItem(
                    new Item.Properties().stacksTo(1),
                    ThrownGrenadeEntity.Type.STICKY_GRENADE
            )
    );

    public static final DeferredItem<Item> GAS_GRENADE = ITEMS.register(
            "gas_grenade",
            () -> new GrenadeItem(
                    new Item.Properties().stacksTo(1),
                    ThrownGrenadeEntity.Type.GAS
            )
    );

    public static final DeferredItem<Item> SMOKE_GRENADE = ITEMS.register(
            "smoke_grenade",
            () -> new GrenadeItem(
                    new Item.Properties().stacksTo(1),
                    ThrownGrenadeEntity.Type.SMOKE
            )
    );

    public static final DeferredItem<Item> GIGA_GRENADE = ITEMS.register(
            "giga_grenade",
            () -> new GrenadeItem(
                    new Item.Properties().stacksTo(1),
                    ThrownGrenadeEntity.Type.GIGA
            )
    );

    public static final DeferredItem<Item> GIGA_GIGA_GRENADE = ITEMS.register(
            "giga_giga_grenade",
            () -> new GrenadeItem(
                    new Item.Properties().stacksTo(1),
                    ThrownGrenadeEntity.Type.GIGA_GIGA
            )
    );

    /**
     * Повертає {@code true}, якщо у слоті голови — протигаз мода.
     */
    public static boolean isWearingGasMask(LivingEntity entity)
    {
        ItemStack head = entity.getItemBySlot(EquipmentSlot.HEAD);
        return isGasMask(head);
    }

    /**
     * Перевіряє, чи переданий стек є протигазом мода.
     */
    public static boolean isGasMask(ItemStack stack)
    {
        return stack.is(GAS_MASK.get());
    }
}
