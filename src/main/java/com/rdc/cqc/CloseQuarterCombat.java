package com.rdc.cqc;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.rdc.cqc.entity.CQCEntities;
import com.rdc.cqc.item.CQCArmorMaterials;
import com.rdc.cqc.item.CQCDataComponents;
import com.rdc.cqc.item.CQCItems;
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

                    .icon(() -> CQCItems.OFFICER_SABER.get().getDefaultInstance())

                    .displayItems((parameters, output) ->
                    {
                        output.accept(CQCItems.OFFICER_SABER.get());
                        output.accept(CQCItems.MACHETE.get());
                        output.accept(CQCItems.COMBAT_KNIFE.get());
                        output.accept(CQCItems.TRENCH_SHOVEL.get());
                        output.accept(CQCItems.PICKAXE_WEAPON.get());

                        // Протигази
                        output.accept(CQCItems.GAS_MASK.get());
                        output.accept(CQCItems.GAS_MASK_S.get());
                        output.accept(CQCItems.GAS_MASK_X.get());

                        // Плащі — комплекти S та X
                        output.accept(CQCItems.TRENCHCOAT_CHEST_S.get());
                        output.accept(CQCItems.TRENCHCOAT_LEGS_S.get());
                        output.accept(CQCItems.TRENCHCOAT_CHEST_X.get());
                        output.accept(CQCItems.TRENCHCOAT_LEGS_X.get());

                        // Крабова броня
                        output.accept(CQCItems.LOBSTER_HELMET.get());
                        output.accept(CQCItems.LOBSTER_CHEST.get());

                        // Гранати
                        output.accept(CQCItems.GRENADE.get());
                        output.accept(CQCItems.DEMO_GRENADE.get());
                        output.accept(CQCItems.GAS_GRENADE.get());
                        output.accept(CQCItems.SMOKE_GRENADE.get());
                    })

                    .build());

    public CloseQuarterCombat(IEventBus modEventBus, ModContainer modContainer)
    {
        // Реєстрація матеріалів броні (для TrenchCoat S/X), предметів, сутностей,
        // data-компонентів та мережевих пакетів.
        CQCArmorMaterials.ARMOR_MATERIALS.register(modEventBus);
        CQCItems.ITEMS.register(modEventBus);
        CQCEntities.ENTITY_TYPES.register(modEventBus);
        CQCDataComponents.COMPONENTS.register(modEventBus);

        // Реєстрація вкладки
        CREATIVE_MODE_TABS.register(modEventBus);

        // Мережа: ЛКМ-висмикування чеки гранати з клієнта на сервер.
        modEventBus.addListener(CloseQuarterCombat::registerPayloads);

        NeoForge.EVENT_BUS.addListener(CQCEvents::onMobEffectApplicable);
        NeoForge.EVENT_BUS.addListener(CQCEvents::onLivingEquipmentChange);

        LOGGER.info("Close Quarter Combat loaded!");
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
