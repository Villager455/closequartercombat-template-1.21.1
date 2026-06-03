package com.rdc.cqc.effect;

import com.rdc.cqc.CloseQuarterCombat;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class CQCEffects
{
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, CloseQuarterCombat.MODID);

    public static final DeferredHolder<MobEffect, MobEffect> FLASHING = MOB_EFFECTS.register(
            "flashing",
            () -> new SimpleMobEffect(MobEffectCategory.HARMFUL, 0xFFFFFF)
    );

    private CQCEffects()
    {
    }

    private static class SimpleMobEffect extends MobEffect
    {
        protected SimpleMobEffect(MobEffectCategory category, int color)
        {
            super(category, color);
        }
    }
}
