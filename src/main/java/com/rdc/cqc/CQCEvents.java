package com.rdc.cqc;

import com.rdc.cqc.item.CQCItems;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public class CQCEvents
{
    /**
     * Блокує накладання будь-якого {@link net.minecraft.world.effect.MobEffect MobEffect}
     * на гравця, поки той у слоті голови має будь-який варіант протигазу.
     */
    public static void onMobEffectApplicable(MobEffectEvent.Applicable event)
    {
        if (event.getEntity() instanceof Player player && CQCItems.isWearingGasMask(player))
        {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    /**
     * При надяганні протигаза в слот голови (з будь-чого, що не було протигазом) —
     * скидаємо всі активні ефекти.
     */
    public static void onLivingEquipmentChange(LivingEquipmentChangeEvent event)
    {
        if (event.getSlot() == EquipmentSlot.HEAD
                && event.getEntity() instanceof Player player
                && !CQCItems.isGasMask(event.getFrom())
                && CQCItems.isGasMask(event.getTo()))
        {
            player.removeAllEffects();
        }
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event)
    {
        Level level = event.getLevel();
        ItemStack held = event.getItemStack();
        BlockPos pos = event.getHitVec().getBlockPos();
        if (!held.is(Items.GLASS_BOTTLE) || !level.getFluidState(pos).is(FluidTags.LAVA))
        {
            return;
        }

        Player player = event.getEntity();
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide()));
        event.setCanceled(true);

        fillMolotov(level, player, event.getHand(), held);
    }

    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event)
    {
        Level level = event.getLevel();
        ItemStack held = event.getItemStack();
        if (!held.is(Items.GLASS_BOTTLE))
        {
            return;
        }

        Player player = event.getEntity();
        Vec3 from = player.getEyePosition();
        Vec3 to = from.add(player.getViewVector(1.0F).scale(5.0D));
        BlockHitResult hit = level.clip(new ClipContext(
                from,
                to,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.SOURCE_ONLY,
                player
        ));

        if (hit.getType() != HitResult.Type.BLOCK || !level.getFluidState(hit.getBlockPos()).is(FluidTags.LAVA))
        {
            return;
        }

        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide()));
        event.setCanceled(true);

        fillMolotov(level, player, event.getHand(), held);
    }

    private static void fillMolotov(Level level, Player player, InteractionHand hand, ItemStack held)
    {
        if (level.isClientSide())
        {
            return;
        }

        ItemStack molotov = CQCItems.MOLOTOV.get().getDefaultInstance();
        if (held.getCount() == 1)
        {
            player.setItemInHand(hand, molotov);
        }
        else
        {
            held.shrink(1);
            if (!player.getInventory().add(molotov))
            {
                player.drop(molotov, false);
            }
        }

        level.playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS,
                1.0F, 0.8F
        );
    }
}
