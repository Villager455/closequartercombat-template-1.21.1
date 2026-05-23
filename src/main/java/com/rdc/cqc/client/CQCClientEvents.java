package com.rdc.cqc.client;

import com.rdc.cqc.CloseQuarterCombat;
import com.rdc.cqc.item.GrenadeItem;
import com.rdc.cqc.network.PullPinPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Клієнтські обробники подій моду.
 *
 * <p>Зокрема ловить ЛКМ із гранатою в руці й посилає {@link PullPinPayload} на сервер,
 * щоб ініціювати фьюз. Дві події ({@link PlayerInteractEvent.LeftClickEmpty} та
 * {@link PlayerInteractEvent.LeftClickBlock}) покривають клік як по повітрю, так і по блоку.</p>
 */
@EventBusSubscriber(modid = CloseQuarterCombat.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class CQCClientEvents
{
    private CQCClientEvents() {}

    /** ЛКМ по повітрю — нічого не наведено. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event)
    {
        tryPullPin();
    }

    /** ЛКМ по блоку — забороняємо ламати, висмикуємо чеку. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event)
    {
        if (tryPullPin())
        {
            // Зупиняємо подальшу обробку (інакше гравець почне ламати блок).
            event.setCanceled(true);
        }
    }

    /**
     * Перевіряє чи у руках гранат, і якщо так — шле пакет на сервер.
     * @return true, якщо пакет надіслано.
     */
    private static boolean tryPullPin()
    {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return false;

        // Спочатку перевіряємо основну руку, потім off-hand. Тільки якщо чека ще не висмикнута.
        ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (main.getItem() instanceof GrenadeItem && !GrenadeItem.isPinPulled(main))
        {
            PacketDistributor.sendToServer(new PullPinPayload(false));
            return true;
        }

        ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
        if (off.getItem() instanceof GrenadeItem && !GrenadeItem.isPinPulled(off))
        {
            PacketDistributor.sendToServer(new PullPinPayload(true));
            return true;
        }

        return false;
    }
}
