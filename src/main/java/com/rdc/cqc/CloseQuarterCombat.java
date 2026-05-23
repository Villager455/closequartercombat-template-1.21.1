package com.rdc.cqc;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.rdc.cqc.item.CQCItems;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
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
                        output.accept(CQCItems.GAS_MASK.get());
                    })

                    .build());

    public CloseQuarterCombat(IEventBus modEventBus, ModContainer modContainer)
    {
        // Реєстрація предметів
        CQCItems.ITEMS.register(modEventBus);

        // Реєстрація вкладки
        CREATIVE_MODE_TABS.register(modEventBus);

        NeoForge.EVENT_BUS.addListener(CQCEvents::onMobEffectApplicable);
        NeoForge.EVENT_BUS.addListener(CQCEvents::onLivingEquipmentChange);

        LOGGER.info("Close Quarter Combat loaded!");
    }
}
