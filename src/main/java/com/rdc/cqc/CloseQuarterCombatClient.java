package com.rdc.cqc;

import com.rdc.cqc.item.CQCItems;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.systems.RenderSystem;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = CloseQuarterCombat.MODID, dist = Dist.CLIENT)
public class CloseQuarterCombatClient {
    private static final ResourceLocation GAS_MASK_OVERLAY_LAYER =
            ResourceLocation.fromNamespaceAndPath(CloseQuarterCombat.MODID, "gas_mask_overlay");
    private static final ResourceLocation GAS_MASK_OVERLAY_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CloseQuarterCombat.MODID, "textures/gasmask_overlay.png");

    public CloseQuarterCombatClient(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(CloseQuarterCombatClient::onClientSetup);
        modEventBus.addListener(CloseQuarterCombatClient::registerGuiLayers);

        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    static void onClientSetup(FMLClientSetupEvent event) {
        // Some client setup code
        CloseQuarterCombat.LOGGER.info("HELLO FROM CLIENT SETUP");
        CloseQuarterCombat.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }

    static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, GAS_MASK_OVERLAY_LAYER, CloseQuarterCombatClient::renderGasMaskOverlay);
    }

    private static void renderGasMaskOverlay(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || !minecraft.options.getCameraType().isFirstPerson()
                || !CQCItems.isWearingGasMask(minecraft.player)) {
            return;
        }

        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();
        RenderSystem.enableBlend();
        guiGraphics.blit(GAS_MASK_OVERLAY_TEXTURE, 0, 0, width, height, 0.0F, 0.0F, 256, 256, 256, 256);
        RenderSystem.disableBlend();
    }
}
