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
