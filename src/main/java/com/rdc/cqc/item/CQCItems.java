package com.rdc.cqc.item;

import com.rdc.cqc.CloseQuarterCombat;
import com.rdc.cqc.entity.ThrownGrenadeEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class CQCItems
{
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(CloseQuarterCombat.MODID);

    // ============================
    //          ЗБРОЯ
    // ============================
    // Характеристики:
    // 50 шкоди
    // дуже повільна атака

    public static final DeferredItem<Item> OFFICER_SABER = ITEMS.register(
            "officer_saber",
            () -> new SwordItem(
                    Tiers.IRON,
                    new Item.Properties()
                            .attributes(SwordItem.createAttributes(
                                    Tiers.IRON,
                                    50,
                                    -3.4F
                            ))
            )
    );

    public static final DeferredItem<Item> MACHETE = ITEMS.register(
            "machete",
            () -> new SwordItem(
                    Tiers.IRON,
                    new Item.Properties()
                            .attributes(SwordItem.createAttributes(
                                    Tiers.IRON,
                                    50,
                                    -3.4F
                            ))
            )
    );

    public static final DeferredItem<Item> COMBAT_KNIFE = ITEMS.register(
            "combat_knife",
            () -> new SwordItem(
                    Tiers.IRON,
                    new Item.Properties()
                            .attributes(SwordItem.createAttributes(
                                    Tiers.IRON,
                                    50,
                                    -3.4F
                            ))
            )
    );

    public static final DeferredItem<Item> TRENCH_SHOVEL = ITEMS.register(
            "trench_shovel",
            () -> new SwordItem(
                    Tiers.IRON,
                    new Item.Properties()
                            .attributes(SwordItem.createAttributes(
                                    Tiers.IRON,
                                    50,
                                    -3.4F
                            ))
            )
    );

    public static final DeferredItem<Item> PICKAXE_WEAPON = ITEMS.register(
            "pickaxe_weapon",
            () -> new SwordItem(
                    Tiers.IRON,
                    new Item.Properties()
                            .attributes(SwordItem.createAttributes(
                                    Tiers.IRON,
                                    50,
                                    -3.4F
                            ))
            )
    );

    // ============================
    //         ПРОТИГАЗИ
    // ============================
    // Звичайні предмети (не броня) у слот HEAD. Не дають захисту,
    // але блокують зілля та малюють оверлей (див. CQCEvents та CloseQuarterCombatClient).

    public static final DeferredItem<Item> GAS_MASK = ITEMS.register(
            "gas_mask",
            () -> new GasMaskItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredItem<Item> GAS_MASK_S = ITEMS.register(
            "gas_mask_s",
            () -> new GasMaskItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredItem<Item> GAS_MASK_X = ITEMS.register(
            "gas_mask_x",
            () -> new GasMaskItem(new Item.Properties().stacksTo(1))
    );

    // ============================
    //       ПЛАЩ "TRENCHCOAT"
    // ============================
    // Дві версії (S та X) — два окремі комплекти броні з iron-like характеристиками.
    // Комплект складається з нагрудника та штанів (helmet/boots не передбачені).

    public static final DeferredItem<Item> TRENCHCOAT_CHEST_S = ITEMS.register(
            "trenchcoat_chest_s",
            () -> new ArmorItem(
                    CQCArmorMaterials.trenchcoatS(),
                    ArmorItem.Type.CHESTPLATE,
                    new Item.Properties()
                            .durability(ArmorItem.Type.CHESTPLATE.getDurability(15))
            )
    );

    public static final DeferredItem<Item> TRENCHCOAT_LEGS_S = ITEMS.register(
            "trenchcoat_legs_s",
            () -> new ArmorItem(
                    CQCArmorMaterials.trenchcoatS(),
                    ArmorItem.Type.LEGGINGS,
                    new Item.Properties()
                            .durability(ArmorItem.Type.LEGGINGS.getDurability(15))
            )
    );

    public static final DeferredItem<Item> TRENCHCOAT_CHEST_X = ITEMS.register(
            "trenchcoat_chest_x",
            () -> new ArmorItem(
                    CQCArmorMaterials.trenchcoatX(),
                    ArmorItem.Type.CHESTPLATE,
                    new Item.Properties()
                            .durability(ArmorItem.Type.CHESTPLATE.getDurability(15))
            )
    );

    public static final DeferredItem<Item> TRENCHCOAT_LEGS_X = ITEMS.register(
            "trenchcoat_legs_x",
            () -> new ArmorItem(
                    CQCArmorMaterials.trenchcoatX(),
                    ArmorItem.Type.LEGGINGS,
                    new Item.Properties()
                            .durability(ArmorItem.Type.LEGGINGS.getDurability(15))
            )
    );

    // ============================
    //       БРОНЯ "LOBSTER ARMOR"
    // ============================
    // Iron-like броня з крабовим дизайном.
    // Комплект складається з шолома (звичайний предмет) та нагрудника (броня).
    // Шолом функціонує як газова маска — звичайний предмет з 3D моделлю.

    public static final DeferredItem<Item> LOBSTER_HELMET = ITEMS.register(
            "lobster_helmet",
            () -> new GasMaskItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredItem<Item> LOBSTER_CHEST = ITEMS.register(
            "lobster_chest",
            () -> new ArmorItem(
                    CQCArmorMaterials.lobsterArmor(),
                    ArmorItem.Type.CHESTPLATE,
                    new Item.Properties()
                            .durability(ArmorItem.Type.CHESTPLATE.getDurability(15))
            )
    );

    // ============================
    //          ГРАНАТИ
    // ============================
    // Кидаються з ПКМ. Через 3 секунди вибух (HE/DEMO), газова хмара (GAS) або дим (SMOKE).
    // Логіка фізики/детонації — у ThrownGrenadeEntity. Stack-розмір = 1 (не стакаються).

    public static final DeferredItem<Item> GRENADE = ITEMS.register(
            "grenade",
            () -> new GrenadeItem(
                    new Item.Properties().stacksTo(1),
                    ThrownGrenadeEntity.Type.HE
            )
    );

    public static final DeferredItem<Item> DEMO_GRENADE = ITEMS.register(
            "demo_grenade",
            () -> new GrenadeItem(
                    new Item.Properties().stacksTo(1),
                    ThrownGrenadeEntity.Type.DEMO
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

    /**
     * Повертає {@code true}, якщо у слоті голови — будь-який з варіантів протигазу
     * (звичайний / S / X). Використовується для оверлею та блокування зілль.
     */
    public static boolean isWearingGasMask(LivingEntity entity)
    {
        ItemStack head = entity.getItemBySlot(EquipmentSlot.HEAD);
        return isGasMask(head);
    }

    /**
     * Перевіряє, чи переданий стек є будь-яким варіантом протигазу мода.
     */
    public static boolean isGasMask(ItemStack stack)
    {
        return stack.is(GAS_MASK.get())
                || stack.is(GAS_MASK_S.get())
                || stack.is(GAS_MASK_X.get());
    }
}
