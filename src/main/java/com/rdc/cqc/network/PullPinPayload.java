package com.rdc.cqc.network;

import com.rdc.cqc.CloseQuarterCombat;
import com.rdc.cqc.item.GrenadeItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Серверний пакет: гравець натиснув ЛКМ, щоб «висмикнути чеку» гранати, яку тримає в руці.
 *
 * <p>Корисне навантаження містить лише руку — відповідний {@link ItemStack} і так
 * визначається на сервері з гравця-відправника. Це безпечніше, ніж довіряти
 * клієнту повний стек.</p>
 */
public record PullPinPayload(boolean offHand) implements CustomPacketPayload
{
    public static final CustomPacketPayload.Type<PullPinPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(CloseQuarterCombat.MODID, "pull_pin")
            );

    public static final StreamCodec<ByteBuf, PullPinPayload> STREAM_CODEC =
            StreamCodec.composite(
                    net.minecraft.network.codec.ByteBufCodecs.BOOL, PullPinPayload::offHand,
                    PullPinPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    /** Обробник на сервері — викликає логіку висмикування чеки у {@link GrenadeItem}. */
    public static void handle(PullPinPayload payload, IPayloadContext context)
    {
        context.enqueueWork(() ->
        {
            if (!(context.player() instanceof ServerPlayer player)) return;

            InteractionHand hand = payload.offHand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            ItemStack stack = player.getItemInHand(hand);

            if (stack.getItem() instanceof GrenadeItem grenadeItem)
            {
                grenadeItem.pullPin(player.serverLevel(), player, hand, stack);
            }
        });
    }
}
