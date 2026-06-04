package com.rdc.cqc.mine;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

public class PressureMineItem extends Item
{
    private final CQCMines.Type mineType;

    public PressureMineItem(Properties properties, CQCMines.Type mineType)
    {
        super(properties);
        this.mineType = mineType;
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        return CQCMines.tryPlantMine(context, this.mineType);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
    }
}
