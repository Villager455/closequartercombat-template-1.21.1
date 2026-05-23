package com.rdc.cqc.item;

import com.mojang.serialization.Codec;
import com.rdc.cqc.CloseQuarterCombat;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Реєстр {@link DataComponentType}-ів моду.
 *
 * <p>Наразі тут лише один компонент — {@link #GRENADE_FUSE}, який позначає
 * запалену гранату в інвентарі гравця та зберігає залишок фьюзу в тіках.</p>
 */
public class CQCDataComponents
{
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, CloseQuarterCombat.MODID);

    /**
     * Запалена граната: залишок фьюзу в тіках. Відсутність компонента означає, що
     * чека ще не висмикнута. Збереження в NBT — для синхронізації клієнт-сервер
     * та переживання збереження світу.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> GRENADE_FUSE =
            COMPONENTS.registerComponentType(
                    "grenade_fuse",
                    builder -> builder
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.VAR_INT)
            );
}
