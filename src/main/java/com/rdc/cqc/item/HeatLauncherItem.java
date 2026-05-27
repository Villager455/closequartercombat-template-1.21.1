package com.rdc.cqc.item;

import com.rdc.cqc.entity.ThrownGrenadeEntity;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class HeatLauncherItem extends Item
{
    private final ThrownGrenadeEntity.Type projectileType;

    public HeatLauncherItem(Properties properties, ThrownGrenadeEntity.Type projectileType)
    {
        super(properties);
        this.projectileType = projectileType;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable(this.getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide())
        {
            HeatLauncherProjectiles.launch((ServerLevel) level, player, this.projectileType);
            player.setItemInHand(hand, CQCItems.EMPTY_LAUNCH_TUBE.get().getDefaultInstance());
            player.awardStat(Stats.ITEM_USED.get(this));
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
