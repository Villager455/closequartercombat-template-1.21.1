package com.rdc.cqc.item;

import com.rdc.cqc.CloseQuarterCombat;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class CQCItems
{
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(CloseQuarterCombat.MODID);

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

    // Протигаз — звичайний предмет (не броня), який можна надягати у слот голови.
    // Не дає захисту від ударів, але блокує зілля та малює оверлей (див. CQCEvents
    // та CloseQuarterCombatClient). Стек = 1, без durability — як простий аксесуар.
    public static final DeferredItem<Item> GAS_MASK = ITEMS.register(
            "gas_mask",
            () -> new GasMaskItem(
                    new Item.Properties()
                            .stacksTo(1)
            )
    );

    public static boolean isWearingGasMask(LivingEntity entity)
    {
        return entity.getItemBySlot(EquipmentSlot.HEAD).is(GAS_MASK.get());
    }
}
