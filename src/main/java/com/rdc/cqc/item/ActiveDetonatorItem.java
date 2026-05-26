package com.rdc.cqc.item;

import com.rdc.cqc.entity.ThrownGrenadeEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class ActiveDetonatorItem extends Item
{
    public ActiveDetonatorItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide())
        {
            Integer entityId = stack.get(CQCDataComponents.REMOTE_GRENADE_ENTITY_ID.get());
            if (entityId != null && level instanceof ServerLevel serverLevel)
            {
                Entity entity = serverLevel.getEntity(entityId);
                if (entity instanceof ThrownGrenadeEntity grenade)
                {
                    grenade.triggerRemoteDetonation();
                }
            }

            player.setItemInHand(hand, CQCItems.REMOTE_DETONATOR.get().getDefaultInstance());
            level.playSound(
                    null,
                    player.getX(), player.getY(), player.getZ(),
                    SoundEvents.LEVER_CLICK,
                    SoundSource.PLAYERS,
                    0.8F,
                    0.85F
            );
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
