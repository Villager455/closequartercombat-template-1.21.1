package com.rdc.cqc.entity;

import com.rdc.cqc.CloseQuarterCombat;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Реєстр сутностей мода Close Quarter Combat.
 */
public class CQCEntities
{
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, CloseQuarterCombat.MODID);

    /** Універсальна гранатна сутність (HE/DEMO/GAS — обирається через {@link ThrownGrenadeEntity#getGrenadeType()}). */
    public static final DeferredHolder<EntityType<?>, EntityType<ThrownGrenadeEntity>> THROWN_GRENADE =
            ENTITY_TYPES.register("thrown_grenade", () -> EntityType.Builder
                    .<ThrownGrenadeEntity>of(ThrownGrenadeEntity::new, MobCategory.MISC)
                    .sized(0.35F, 0.35F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build(ResourceLocation
                            .fromNamespaceAndPath(CloseQuarterCombat.MODID, "thrown_grenade")
                            .toString()));
}
