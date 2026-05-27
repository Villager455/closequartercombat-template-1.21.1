package com.rdc.cqc;

import com.rdc.cqc.item.CQCItems;
import com.rdc.cqc.entity.ThrownGrenadeEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cod;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Salmon;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.TropicalFish;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public class CQCEvents
{
    private static final int MOLOTOV_BURN_TRACK_TICKS = 20 * 40;
    private static final int MOLOTOV_FIRE_TRACK_TICKS = 20 * 45;
    private static final Map<UUID, MolotovBurn> MOLOTOV_BURNS = new HashMap<>();
    private static final Map<MolotovFirePos, MolotovBurn> MOLOTOV_FIRES = new HashMap<>();
    private static final ThreadLocal<GrenadeKill> ACTIVE_IN_HAND_GRENADE = new ThreadLocal<>();

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

    public static void onLivingDeath(LivingDeathEvent event)
    {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel serverLevel))
        {
            return;
        }
        cleanupMolotovTracking(serverLevel);

        GrenadeKill grenadeKill = getGrenadeKill(serverLevel, victim, event.getSource());
        if (grenadeKill == null || grenadeKill.owner() == null)
        {
            return;
        }

        awardNextProgress(grenadeKill.owner(), "good_demoman");

        if (isPillagerVariant(victim))
        {
            awardNextProgress(grenadeKill.owner(), "chornobaivka");
        }

        if (victim instanceof Ravager)
        {
            awardNextProgress(grenadeKill.owner(), "panzerhenker");
        }

        if (grenadeKill.type() == ThrownGrenadeEntity.Type.MOLOTOV)
        {
            awardNextProgress(grenadeKill.owner(), "revolution");
            if (isMeatMob(victim))
            {
                awardAdvancement(grenadeKill.owner(), "medium_rare");
            }
            if (isPillagerVariant(victim)
                    && victim.level().getBiome(victim.blockPosition()).value().coldEnoughToSnow(victim.blockPosition()))
            {
                awardAdvancement(grenadeKill.owner(), "winter_war");
            }
        }
    }

    public static void trackMolotovBurn(LivingEntity target, ServerPlayer owner)
    {
        if (target.level().isClientSide())
        {
            return;
        }

        cleanupMolotovTracking((ServerLevel) target.level());
        MOLOTOV_BURNS.put(
                target.getUUID(),
                new MolotovBurn(owner.getUUID(), target.level().getGameTime() + MOLOTOV_BURN_TRACK_TICKS)
        );
    }

    public static void trackMolotovFire(Level level, BlockPos firePos, ServerPlayer owner)
    {
        if (!(level instanceof ServerLevel serverLevel))
        {
            return;
        }

        cleanupMolotovTracking(serverLevel);
        MOLOTOV_FIRES.put(
                new MolotovFirePos(serverLevel.dimension().location(), firePos.immutable()),
                new MolotovBurn(owner.getUUID(), serverLevel.getGameTime() + MOLOTOV_FIRE_TRACK_TICKS)
        );
    }

    public static void beginInHandGrenadeDetonation(ServerPlayer owner, ThrownGrenadeEntity.Type type)
    {
        ACTIVE_IN_HAND_GRENADE.set(new GrenadeKill(owner, type));
    }

    public static void endInHandGrenadeDetonation()
    {
        ACTIVE_IN_HAND_GRENADE.remove();
    }

    public static void recordIncendiaryGrenadeUse(ServerPlayer player)
    {
        awardNextProgress(player, "love_napalm_in_the_morning");
    }

    public static void awardAdvancement(ServerPlayer serverPlayer, String path)
    {
        AdvancementHolder advancement = serverPlayer.server.getAdvancements().get(
                ResourceLocation.fromNamespaceAndPath(CloseQuarterCombat.MODID, path)
        );
        if (advancement != null)
        {
            serverPlayer.getAdvancements().award(advancement, path);
        }
    }

    private static GrenadeKill getGrenadeKill(ServerLevel level, LivingEntity victim, DamageSource source)
    {
        Entity direct = source.getDirectEntity();
        Entity causing = source.getEntity();
        ThrownGrenadeEntity grenade = null;

        if (direct instanceof ThrownGrenadeEntity directGrenade)
        {
            grenade = directGrenade;
        }
        else if (causing instanceof ThrownGrenadeEntity causingGrenade)
        {
            grenade = causingGrenade;
        }

        if (grenade != null)
        {
            ServerPlayer owner = grenade.getOwner() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
            return new GrenadeKill(owner, grenade.getGrenadeType());
        }

        GrenadeKill inHandGrenade = ACTIVE_IN_HAND_GRENADE.get();
        if (inHandGrenade != null)
        {
            Entity sourceEntity = source.getEntity();
            if (sourceEntity == null || sourceEntity == inHandGrenade.owner())
            {
                return inHandGrenade;
            }
        }

        if (isFireDamage(source))
        {
            MolotovBurn burn = MOLOTOV_BURNS.get(victim.getUUID());
            if (burn != null)
            {
                if (level.getGameTime() > burn.expiresAt())
                {
                    MOLOTOV_BURNS.remove(victim.getUUID());
                }
                else
                {
                    ServerPlayer owner = level.getServer().getPlayerList().getPlayer(burn.ownerId());
                    MOLOTOV_BURNS.remove(victim.getUUID());
                    return new GrenadeKill(owner, ThrownGrenadeEntity.Type.MOLOTOV);
                }
            }

            burn = findMolotovFireBurn(level, victim.blockPosition());
            if (burn != null)
            {
                ServerPlayer owner = level.getServer().getPlayerList().getPlayer(burn.ownerId());
                return new GrenadeKill(owner, ThrownGrenadeEntity.Type.MOLOTOV);
            }
        }

        return null;
    }

    private static MolotovBurn findMolotovFireBurn(ServerLevel level, BlockPos victimPos)
    {
        ResourceLocation dimension = level.dimension().location();
        for (BlockPos nearby : BlockPos.betweenClosed(victimPos.offset(-1, -1, -1), victimPos.offset(1, 1, 1)))
        {
            MolotovBurn burn = MOLOTOV_FIRES.get(new MolotovFirePos(dimension, nearby.immutable()));
            if (burn != null)
            {
                if (level.getGameTime() > burn.expiresAt())
                {
                    MOLOTOV_FIRES.remove(new MolotovFirePos(dimension, nearby.immutable()));
                    continue;
                }
                return burn;
            }
        }
        return null;
    }

    private static void cleanupMolotovTracking(ServerLevel level)
    {
        long gameTime = level.getGameTime();
        ResourceLocation dimension = level.dimension().location();
        MOLOTOV_BURNS.entrySet().removeIf(entry -> gameTime > entry.getValue().expiresAt());
        MOLOTOV_FIRES.entrySet().removeIf(entry ->
                entry.getKey().dimension().equals(dimension) && gameTime > entry.getValue().expiresAt()
        );
    }

    private static boolean isFireDamage(DamageSource source)
    {
        return source.is(DamageTypes.IN_FIRE)
                || source.is(DamageTypes.ON_FIRE)
                || source.is(DamageTypes.LAVA);
    }

    private static boolean isPillagerVariant(LivingEntity entity)
    {
        return entity instanceof Raider && !(entity instanceof Vex);
    }

    private static boolean isMeatMob(LivingEntity entity)
    {
        return entity instanceof Cow
                || entity instanceof Pig
                || entity instanceof Sheep
                || entity instanceof Chicken
                || entity instanceof Rabbit
                || entity instanceof MushroomCow
                || entity instanceof Cod
                || entity instanceof Salmon
                || entity instanceof TropicalFish
                || entity instanceof Hoglin;
    }

    private static void awardNextProgress(ServerPlayer player, String path)
    {
        AdvancementHolder holder = player.server.getAdvancements().get(
                ResourceLocation.fromNamespaceAndPath(CloseQuarterCombat.MODID, path)
        );
        if (holder == null || player.getAdvancements().getOrStartProgress(holder).isDone())
        {
            return;
        }

        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        for (String criterion : progress.getRemainingCriteria())
        {
            player.getAdvancements().award(holder, criterion);
            return;
        }
    }

    private record GrenadeKill(ServerPlayer owner, ThrownGrenadeEntity.Type type) {}

    private record MolotovBurn(UUID ownerId, long expiresAt) {}

    private record MolotovFirePos(ResourceLocation dimension, BlockPos pos) {}

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
