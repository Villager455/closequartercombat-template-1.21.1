package com.rdc.cqc;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.rdc.cqc.effect.CQCEffects;
import com.rdc.cqc.entity.CQCEntities;
import com.rdc.cqc.item.CQCDataComponents;
import com.rdc.cqc.item.CQCItems;
import com.rdc.cqc.mine.CQCMines;
import com.rdc.cqc.network.PullPinPayload;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(CloseQuarterCombat.MODID)
public class CloseQuarterCombat
{
    public static final String MODID = "closequartercombat";

    public static final Logger LOGGER = LogUtils.getLogger();

    // Creative Tabs
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // Вкладка мода
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CQC_TAB =
            CREATIVE_MODE_TABS.register("cqc_tab", () -> CreativeModeTab.builder()

                    .title(Component.translatable("itemGroup.closequartercombat.cqc_tab"))

                    .withTabsBefore(CreativeModeTabs.COMBAT)

                    .icon(() -> CQCItems.FRAG_GRENADE.get().getDefaultInstance())

                    .displayItems((parameters, output) ->
                    {
                        // Гранати
                        output.accept(CQCItems.FRAG_GRENADE.get());
                        output.accept(CQCItems.AIRBURST_FRAG_GRENADE.get());
                        output.accept(CQCItems.HIGH_EXPLOSIVE_GRENADE.get());
                        output.accept(CQCItems.SAPPER_BAG.get());
                        output.accept(CQCItems.SMALL_GRENADE.get());
                        output.accept(CQCItems.DYNAMITE_STICK.get());
                        output.accept(CQCItems.REMOTE_DYNAMITE_BUNDLE.get());
                        output.accept(CQCItems.IMPROVISED_GRENADE.get());
                        output.accept(CQCItems.MOLOTOV.get());
                        output.accept(CQCItems.INCENDIARY_GRENADE.get());
                        output.accept(CQCItems.CLUSTER_GRENADE.get());
                        output.accept(CQCItems.FLASHBANG_GRENADE.get());
                        output.accept(CQCItems.IMPACT_GRENADE.get());
                        output.accept(CQCItems.SHAPED_CHARGE_GRENADE.get());
                        output.accept(CQCItems.IMPACT_MINE.get());
                        output.accept(CQCItems.FRAG_PRESSURE_MINE.get());
                        output.accept(CQCItems.HIGH_EXPLOSIVE_PRESSURE_MINE.get());
                        output.accept(CQCItems.HEAT_LAUNCHER.get());
                        output.accept(CQCItems.LARGE_HEAT_LAUNCHER.get());
                        output.accept(CQCItems.HIGH_EXPLOSIVE_LAUNCHER.get());
                        output.accept(CQCItems.INCENDIARY_LAUNCHER.get());
                        output.accept(CQCItems.FRAG_LAUNCHER.get());
                        output.accept(CQCItems.MAGNETIC_GRENADE.get());
                        output.accept(CQCItems.STICKY_GRENADE.get());
                        output.accept(CQCItems.GIGA_GRENADE.get());
                        output.accept(CQCItems.GIGA_GIGA_GRENADE.get());
                        output.accept(CQCItems.GAS_GRENADE.get());
                        output.accept(CQCItems.SMOKE_GRENADE.get());

                        // Протигаз
                        output.accept(CQCItems.GAS_MASK.get());

                        // Інгредієнти
                        output.accept(CQCItems.FUSE.get());
                        output.accept(CQCItems.CONTACT_DETONATOR.get());
                        output.accept(CQCItems.REMOTE_DETONATOR.get());
                        output.accept(CQCItems.EMPTY_LAUNCH_TUBE.get());
                        output.accept(CQCItems.EXPLOSIVE_GRENADE_PART.get());
                        output.accept(CQCItems.HEAT_GRENADE_PART.get());
                    })

                    .build());

    public CloseQuarterCombat(IEventBus modEventBus, ModContainer modContainer)
    {
        // Реєстрація предметів, сутностей, data-компонентів та мережевих пакетів.
        CQCItems.ITEMS.register(modEventBus);
        CQCEntities.ENTITY_TYPES.register(modEventBus);
        CQCDataComponents.COMPONENTS.register(modEventBus);
        CQCEffects.MOB_EFFECTS.register(modEventBus);

        // Реєстрація вкладки
        CREATIVE_MODE_TABS.register(modEventBus);

        // Мережа: ЛКМ-висмикування чеки гранати з клієнта на сервер.
        modEventBus.addListener(CloseQuarterCombat::registerPayloads);

        NeoForge.EVENT_BUS.addListener(CQCEvents::onMobEffectApplicable);
        NeoForge.EVENT_BUS.addListener(CQCEvents::onLivingEquipmentChange);
        NeoForge.EVENT_BUS.addListener(CQCEvents::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(CQCEvents::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(CQCEvents::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(CQCMines::onEntityTick);

        LOGGER.info("Simple Grenades loaded!");
    }

    /** Реєстрація custom payload-ів моду. */
    private static void registerPayloads(RegisterPayloadHandlersEvent event)
    {
        final PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(
                PullPinPayload.TYPE,
                PullPinPayload.STREAM_CODEC,
                PullPinPayload::handle
        );
    }
}
