package com.rdc.cqc.item;

import com.rdc.cqc.CQCEvents;
import com.rdc.cqc.entity.ThrownGrenadeEntity;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class ImpactMineItem extends Item
{
    private static final float USER_DAMAGE = 5.0F;

    public ImpactMineItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable(this.getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        Player player = context.getPlayer();
        if (player == null)
        {
            return InteractionResult.PASS;
        }

        Level level = context.getLevel();
        if (!level.isClientSide())
        {
            Vec3 direction = player.getLookAngle();
            if (direction.lengthSqr() <= 1.0E-4D)
            {
                direction = Vec3.atLowerCornerOf(context.getClickedFace().getNormal()).scale(-1.0D);
            }
            detonate(level, player, context.getClickLocation(), direction, context.getItemInHand(), null);
        }

        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand)
    {
        Level level = player.level();
        if (!level.isClientSide())
        {
            Vec3 impactPosition = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
            detonate(level, player, impactPosition, player.getLookAngle(), stack, target);
        }

        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    private static void detonate(Level level, Player player, Vec3 impactPosition, Vec3 direction, ItemStack stack, LivingEntity directTarget)
    {
        ServerPlayer serverPlayer = player instanceof ServerPlayer owner ? owner : null;
        if (serverPlayer != null)
        {
            CQCEvents.beginInHandGrenadeDetonation(serverPlayer, ThrownGrenadeEntity.Type.SHAPED_CHARGE_GRENADE);
        }

        try
        {
            if (directTarget != null && directTarget.isAlive())
            {
                directTarget.hurt(player.damageSources().thrown(null, player), ThrownGrenadeEntity.HEAT_GRENADE_DIRECT_HIT_DAMAGE);
            }
            ThrownGrenadeEntity.detonateShapedChargeAt(level, player, serverPlayer, impactPosition, direction);
        }
        finally
        {
            if (serverPlayer != null)
            {
                CQCEvents.endInHandGrenadeDetonation();
            }
        }

        if (player.isAlive())
        {
            player.hurt(player.damageSources().explosion(player, player), USER_DAMAGE);
        }
        stack.shrink(1);
    }
}
