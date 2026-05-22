package com.rdc.cqc;

import com.rdc.cqc.item.CQCItems;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;

public class    CQCEvents
{
    public static void onMobEffectApplicable(MobEffectEvent.Applicable event)
    {
        if (event.getEntity() instanceof Player player && CQCItems.isWearingGasMask(player))
        {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    public static void onLivingEquipmentChange(LivingEquipmentChangeEvent event)
    {
        if (event.getSlot() == EquipmentSlot.HEAD
                && event.getEntity() instanceof Player player
                && !event.getFrom().is(CQCItems.GAS_MASK.get())
                && event.getTo().is(CQCItems.GAS_MASK.get()))
        {
            player.removeAllEffects();
        }
    }
}
