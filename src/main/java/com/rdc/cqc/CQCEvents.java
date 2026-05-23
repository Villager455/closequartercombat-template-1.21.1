package com.rdc.cqc;

import com.rdc.cqc.item.CQCItems;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;

public class CQCEvents
{
    /**
     * Блокує накладання будь-якого {@link net.minecraft.world.effect.MobEffect MobEffect}
     * на гравця, поки той у слоті голови має будь-який варіант протигазу.
     */
    public static void onMobEffectApplicable(MobEffectEvent.Applicable event)
    {
        if (event.getEntity() instanceof Player player && CQCItems.isWearingGasMask(player))
        {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    /**
     * При надяганні протигаза в слот голови (з будь-чого, що не було протигазом) —
     * скидаємо всі активні ефекти.
     */
    public static void onLivingEquipmentChange(LivingEquipmentChangeEvent event)
    {
        if (event.getSlot() == EquipmentSlot.HEAD
                && event.getEntity() instanceof Player player
                && !CQCItems.isGasMask(event.getFrom())
                && CQCItems.isGasMask(event.getTo()))
        {
            player.removeAllEffects();
        }
    }
}
