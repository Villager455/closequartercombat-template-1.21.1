package com.rdc.cqc.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Протигаз — звичайний предмет (НЕ броня), який можна надягати в слот голови.
 * Не дає захисту від ударів, але дозволяє рендерити оверлей та блокувати зілля
 * через обробники подій у {@link com.rdc.cqc.CQCEvents}.
 *
 * <p>Реалізація:
 * <ul>
 *   <li>Не наслідує {@link net.minecraft.world.item.ArmorItem}, тож не додає атрибутів броні
 *       (Armor / Armor Toughness / Knockback Resistance).</li>
 *   <li>Перевизначає {@link #getEquipmentSlot(ItemStack)} → {@link EquipmentSlot#HEAD}.
 *       Це NeoForge-екстеншен (IItemExtension), завдяки якому диспенсери, мобі-пікап та
 *       hotkey "F" (Curios/опційно) розпізнаватимуть предмет як спорядження голови.</li>
 *   <li>{@link #use(Level, Player, InteractionHand)} — при ПКМ у руці предмет переміщується
 *       у слот голови, якщо той вільний (та обмінюється, якщо ні).</li>
 * </ul>
 */
public class GasMaskItem extends Item
{
    public GasMaskItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public EquipmentSlot getEquipmentSlot(ItemStack stack)
    {
        return EquipmentSlot.HEAD;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);
        EquipmentSlot slot = EquipmentSlot.HEAD;
        ItemStack equipped = player.getItemBySlot(slot);

        // Якщо в слоті голови вже щось є — не дозволяємо просто так перезаписати.
        if (!equipped.isEmpty())
        {
            return InteractionResultHolder.fail(stack);
        }

        // Переміщуємо предмет з руки у слот голови.
        player.setItemSlot(slot, stack.copy());
        stack.setCount(0);

        // Звук одягання (як у шкіряної броні).
        level.playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARMOR_EQUIP_LEATHER.value(),
                SoundSource.PLAYERS,
                1.0F, 1.0F
        );

        if (!level.isClientSide())
        {
            player.awardStat(Stats.ITEM_USED.get(this));
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
