package com.rdc.cqc.item;

import com.rdc.cqc.CloseQuarterCombat;
import com.rdc.cqc.CQCEvents;
import com.rdc.cqc.entity.ThrownGrenadeEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Предмет-граната. Працює у дві стадії:
 *
 * <ol>
 *     <li><b>ЛКМ ({@link #pullPin}):</b> «висмикується чека» — на стек записується
 *         компонент {@link CQCDataComponents#GRENADE_FUSE} зі стартовим значенням 100 тіків.
 *         Граната ще лежить у руці; запускається ванільний звук запалу динаміту.</li>
 *     <li><b>ПКМ ({@link #use}):</b> викидаємо {@link ThrownGrenadeEntity}.
 *         Якщо чека вже висмикнута — entity отримує <i>залишок</i> фьюзу
 *         (час, який гравець уже протримав гранату, ВРАХОВУЄТЬСЯ). Інакше — entity
 *         має повний фьюз 100 тіків.</li>
 * </ol>
 *
 * <p>Якщо гравець не викине запалену гранату, {@link #inventoryTick} зменшує фьюз кожен тік
 * і коли він досягає нуля — викликається вибух прямо в руці гравця.</p>
 */
public class GrenadeItem extends Item
{
    /** Стартовий фьюз від моменту висмикування чеки. */
    public static final int DEFAULT_FUSE_TICKS = 100;
    private static final int SMALL_GRENADE_FUSE_TICKS = 60;
    private static final int CLUSTER_GRENADE_FUSE_TICKS = 20;
    private static final int SAPPER_BAG_FUSE_TICKS = 300;
    private static final int REMOTE_DYNAMITE_BUNDLE_SELF_DESTRUCT_TICKS = 6000;
    private static final int IMPROVISED_MIN_FUSE_TICKS = 10;
    private static final int IMPROVISED_QUICK_MAX_FUSE_TICKS = 20;
    private static final int IMPROVISED_MAX_FUSE_TICKS = 200;

    private final ThrownGrenadeEntity.Type type;

    public GrenadeItem(Properties properties, ThrownGrenadeEntity.Type type)
    {
        super(properties);
        this.type = type;
    }

    public ThrownGrenadeEntity.Type getGrenadeType()
    {
        return type;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
    }

    /**
     * Чи зараз у цього стека висмикнута чека (наявний компонент фьюзу).
     */
    public static boolean isPinPulled(ItemStack stack)
    {
        return stack.has(CQCDataComponents.GRENADE_FUSE.get());
    }

    /**
     * Поточний залишок фьюзу. {@code -1} якщо чека не висмикнута.
     */
    public static int getRemainingFuse(ItemStack stack)
    {
        Integer v = stack.get(CQCDataComponents.GRENADE_FUSE.get());
        return v == null ? -1 : v;
    }

    /**
     * Викликається серверним обробником {@link com.rdc.cqc.network.PullPinPayload}.
     * Виставляє компонент фьюзу й програє звук запалу. Повторне натискання — не робить нічого.
     */
    public void pullPin(ServerLevel level, Player player, InteractionHand hand, ItemStack stack)
    {
        if (this.type == ThrownGrenadeEntity.Type.IMPACT_GRENADE
                || this.type == ThrownGrenadeEntity.Type.SHAPED_CHARGE_GRENADE
                || this.type == ThrownGrenadeEntity.Type.MAGNETIC_GRENADE
                || this.type == ThrownGrenadeEntity.Type.REMOTE_DYNAMITE_BUNDLE
                || this.type == ThrownGrenadeEntity.Type.DYNAMITE_STICK
                || this.type == ThrownGrenadeEntity.Type.SMALL_GRENADE
                || this.type == ThrownGrenadeEntity.Type.IMPROVISED_GRENADE
                || this.type == ThrownGrenadeEntity.Type.CLUSTER_GRENADE
                || this.type == ThrownGrenadeEntity.Type.MOLOTOV)
        {
            return;
        }

        if (isPinPulled(stack))
        {
            return; // вже активовано
        }

        stack.set(CQCDataComponents.GRENADE_FUSE.get(), getStartingFuse(level));

        // Звук запалу (Shear) — як просив користувач.
        level.playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS,
                1.0F, 1.0F
        );
    }

    /**
     * При ПКМ — кидаємо гранату. Якщо чека вже висмикнута, час що вже минув,
     * враховується в entity (її fuse = залишок зі стека).
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);

        if (this.type == ThrownGrenadeEntity.Type.DYNAMITE_STICK && !hasFlintAndSteelInHand(player))
        {
            return InteractionResultHolder.fail(stack);
        }

        // Звук «кидка» (короткий «вух» сніжка).
        level.playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS,
                0.6F, 0.8F / (level.getRandom().nextFloat() * 0.4F + 0.8F)
        );

        if (!level.isClientSide())
        {
            int fuse = getRemainingFuse(stack);
            if (fuse < 0) fuse = getStartingFuse(level);

            ThrownGrenadeEntity grenade = ThrownGrenadeEntity.throwGrenade(level, player, this.type, stack);
            grenade.setFuse(fuse);

            if (player instanceof ServerPlayer serverPlayer)
            {
                if (this.type == ThrownGrenadeEntity.Type.DYNAMITE_STICK)
                {
                    awardAdvancement(serverPlayer, "kaboom_rico");
                }
                else if (this.type == ThrownGrenadeEntity.Type.INCENDIARY_GRENADE)
                {
                    CQCEvents.recordIncendiaryGrenadeUse(serverPlayer);
                }
            }

            if (this.type == ThrownGrenadeEntity.Type.REMOTE_DYNAMITE_BUNDLE)
            {
                ItemStack detonator = CQCItems.ACTIVE_DETONATOR.get().getDefaultInstance();
                detonator.set(CQCDataComponents.REMOTE_GRENADE_UUID.get(), grenade.getUUID().toString());
                player.setItemInHand(hand, detonator);
                player.awardStat(Stats.ITEM_USED.get(this));
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
            }
        }

        player.awardStat(Stats.ITEM_USED.get(this));

        stack.shrink(1);

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /**
     * Тік предмета в інвентарі: якщо чека висмикнута — зменшуємо фьюз. Коли
     * фьюз ≤ 0 — викликаємо вибух прямо в гравця і прибираємо стек.
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected)
    {
        if (level.isClientSide()) return;
        if (entity instanceof ServerPlayer serverPlayer)
        {
            awardInventoryAdvancements(serverPlayer);
        }
        if (!isPinPulled(stack)) return;
        if (!(entity instanceof Player player)) return;

        int fuse = getRemainingFuse(stack);
        fuse--;

        if (fuse <= 0)
        {
            // Вибух у руці гравця.
            boolean wasAlive = player.isAlive();
            detonateInHand(level, player);
            awardProfessionalIfKilledByHandGrenade(player, wasAlive);
            if (this.type == ThrownGrenadeEntity.Type.IMPROVISED_GRENADE)
            {
                awardAdvancementIfKilled(player, wasAlive, "price_of_saving");
            }
            stack.shrink(1);
        }
        else
        {
            stack.set(CQCDataComponents.GRENADE_FUSE.get(), fuse);
        }
    }

    /**
     * Вибух точно в позиції гравця (коли граната «вибухнула в руках»).
     * Тип взаємодії з блоками та модель пошкоджень — як у звичайної детонації.
     */
    private void detonateInHand(Level level, Player player)
    {
        switch (this.type)
        {
            case FRAG_GRENADE ->
            {
                // Frag Grenade: шкода від осколків без ламання блоків.
                double explosionY = player.getY() + 0.5D;
                int kills = ThrownGrenadeEntity.damageAndSpawnShrapnel(level, player.getX(), explosionY, player.getZ(), ThrownGrenadeEntity.FRAG_GRENADE_EXPLOSION_RADIUS, ThrownGrenadeEntity.FRAG_GRENADE_SHRAPNEL_DAMAGE, player);
                if (kills >= 5 && player instanceof ServerPlayer serverPlayer)
                {
                    awardAdvancement(serverPlayer, "fire_in_the_hole");
                }
                ThrownGrenadeEntity.spawnFragExplosionParticles(level, player.getX(), explosionY + 0.25D, player.getZ());
                ThrownGrenadeEntity.spawnShrapnelSmokeBurst(level, player.getX(), explosionY + 0.25D, player.getZ(), ThrownGrenadeEntity.FRAG_GRENADE_EXPLOSION_RADIUS, level.getRandom());
                level.levelEvent(2009,
                        new net.minecraft.core.BlockPos((int) player.getX(), (int) explosionY, (int) player.getZ()),
                        0);
                level.playSound(
                        null,
                        player.getX(), explosionY, player.getZ(),
                        SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL,
                        1.6F, 1.0F
                );
            }
            case AIRBURST_FRAG_GRENADE ->
            {
                double explosionY = player.getY() + 1.6D;
                ThrownGrenadeEntity.damageAndSpawnShrapnel(level, player.getX(), explosionY, player.getZ(), 40.0F, ThrownGrenadeEntity.FRAG_GRENADE_SHRAPNEL_DAMAGE, player);
                ThrownGrenadeEntity.spawnFragExplosionParticles(level, player.getX(), explosionY + 0.25D, player.getZ());
                ThrownGrenadeEntity.spawnShrapnelSmokeBurst(level, player.getX(), explosionY + 0.25D, player.getZ(), 40.0F, level.getRandom());
                level.levelEvent(2009,
                        new net.minecraft.core.BlockPos((int) player.getX(), (int) explosionY, (int) player.getZ()),
                        0);
                level.playSound(
                        null,
                        player.getX(), explosionY, player.getZ(),
                        SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL,
                        1.9F, 0.85F
                );
            }
            case HIGH_EXPLOSIVE_GRENADE ->
            {
                // High Explosive Grenade: повний вибух з ламанням блоків (мала сила).
                level.explode(
                        player,
                        player.getX(), player.getY() + 0.5D, player.getZ(),
                        ThrownGrenadeEntity.HIGH_EXPLOSIVE_GRENADE_EXPLOSION_RADIUS,
                        Level.ExplosionInteraction.TNT
                );
            }
            case SAPPER_BAG ->
            {
                boolean wasAlive = player.isAlive();
                level.explode(
                        player,
                        player.getX(), player.getY() + 0.5D, player.getZ(),
                        ThrownGrenadeEntity.SAPPER_BAG_EXPLOSION_RADIUS,
                        Level.ExplosionInteraction.TNT
                );
                awardAdvancementIfKilled(player, wasAlive, "more_dangerous_than_it_looks");
            }
            case SMALL_GRENADE ->
            {
                level.explode(
                        player,
                        player.getX(), player.getY() + 0.5D, player.getZ(),
                        ThrownGrenadeEntity.SMALL_GRENADE_EXPLOSION_RADIUS,
                        Level.ExplosionInteraction.TNT
                );
            }
            case DYNAMITE_STICK ->
            {
                level.explode(
                        player,
                        player.getX(), player.getY() + 0.5D, player.getZ(),
                        ThrownGrenadeEntity.DYNAMITE_STICK_EXPLOSION_RADIUS,
                        Level.ExplosionInteraction.TNT
                );
            }
            case IMPROVISED_GRENADE ->
            {
                level.explode(
                        player,
                        player.getX(), player.getY() + 0.5D, player.getZ(),
                        ThrownGrenadeEntity.HIGH_EXPLOSIVE_GRENADE_EXPLOSION_RADIUS,
                        Level.ExplosionInteraction.TNT
                );
            }
            case IMPACT_GRENADE ->
            {
                level.explode(
                        player,
                        player.getX(), player.getY() + 0.5D, player.getZ(),
                        ThrownGrenadeEntity.IMPACT_GRENADE_EXPLOSION_RADIUS,
                        Level.ExplosionInteraction.TNT
                );
            }
            case SHAPED_CHARGE_GRENADE ->
            {
                level.explode(
                        player,
                        player.getX(), player.getY() + 0.5D, player.getZ(),
                        ThrownGrenadeEntity.HEAT_GRENADE_EXPLOSION_RADIUS,
                        Level.ExplosionInteraction.TNT
                );
            }
            case MAGNETIC_GRENADE ->
            {
                level.explode(
                        player,
                        player.getX(), player.getY() + 0.5D, player.getZ(),
                        ThrownGrenadeEntity.HIGH_EXPLOSIVE_GRENADE_EXPLOSION_RADIUS,
                        Level.ExplosionInteraction.TNT
                );
            }
            case STICKY_GRENADE ->
            {
                level.explode(
                        player,
                        player.getX(), player.getY() + 0.5D, player.getZ(),
                        ThrownGrenadeEntity.HIGH_EXPLOSIVE_GRENADE_EXPLOSION_RADIUS,
                        Level.ExplosionInteraction.TNT
                );
            }
            case REMOTE_DYNAMITE_BUNDLE ->
            {
                level.explode(
                        player,
                        player.getX(), player.getY() + 0.5D, player.getZ(),
                        ThrownGrenadeEntity.HIGH_EXPLOSIVE_GRENADE_EXPLOSION_RADIUS,
                        Level.ExplosionInteraction.TNT
                );
            }
            case GIGA ->
            {
                // Гіга граната: потужний вибух з ламанням блоків
                level.explode(
                        player,
                        player.getX(), player.getY() + 0.5D, player.getZ(),
                        ThrownGrenadeEntity.GIGA_EXPLOSION_RADIUS,
                        Level.ExplosionInteraction.TNT
                );
                // Також наносимо урагу від осколків
                ThrownGrenadeEntity.damageAndSpawnShrapnel(level, player.getX(), player.getY() + 0.5D, player.getZ(), ThrownGrenadeEntity.GIGA_EXPLOSION_RADIUS, 70.0F, player);
            }
            case GIGA_GIGA ->
            {
                level.explode(
                        player,
                        player.getX(), player.getY() + 0.5D, player.getZ(),
                        ThrownGrenadeEntity.GIGA_GIGA_EXPLOSION_RADIUS,
                        Level.ExplosionInteraction.TNT
                );
                ThrownGrenadeEntity.damageAndSpawnShrapnel(level, player.getX(), player.getY() + 0.5D, player.getZ(), ThrownGrenadeEntity.GIGA_GIGA_EXPLOSION_RADIUS, 91.0F, player);
            }
            case GAS ->
            {
                // Створюємо тимчасову гранату-сутність, яка стане газовим емітером.
                ThrownGrenadeEntity dummy = new ThrownGrenadeEntity(level, player, ThrownGrenadeEntity.Type.GAS);
                dummy.setPos(player.getX(), player.getY() + 0.5D, player.getZ());
                level.addFreshEntity(dummy);
                dummy.setFuse(1); // вибухне на наступному тіку у власній позиції
            }
            case INCENDIARY_GRENADE ->
            {
                ThrownGrenadeEntity dummy = new ThrownGrenadeEntity(level, player, ThrownGrenadeEntity.Type.INCENDIARY_GRENADE);
                dummy.setPos(player.getX(), player.getY() + 0.5D, player.getZ());
                level.addFreshEntity(dummy);
                dummy.setFuse(1);
            }
        }
    }

    private int getStartingFuse(Level level)
    {
        if (this.type == ThrownGrenadeEntity.Type.SMALL_GRENADE)
        {
            return SMALL_GRENADE_FUSE_TICKS;
        }

        if (this.type == ThrownGrenadeEntity.Type.CLUSTER_GRENADE)
        {
            return CLUSTER_GRENADE_FUSE_TICKS;
        }

        if (this.type == ThrownGrenadeEntity.Type.SAPPER_BAG)
        {
            return SAPPER_BAG_FUSE_TICKS;
        }

        if (this.type == ThrownGrenadeEntity.Type.REMOTE_DYNAMITE_BUNDLE)
        {
            return REMOTE_DYNAMITE_BUNDLE_SELF_DESTRUCT_TICKS;
        }

        if (this.type != ThrownGrenadeEntity.Type.IMPROVISED_GRENADE)
        {
            return DEFAULT_FUSE_TICKS;
        }

        if (level.getRandom().nextFloat() < 0.3F)
        {
            return IMPROVISED_MIN_FUSE_TICKS
                    + level.getRandom().nextInt(IMPROVISED_QUICK_MAX_FUSE_TICKS - IMPROVISED_MIN_FUSE_TICKS + 1);
        }

        double centered = level.getRandom().nextBoolean()
                ? Math.pow(level.getRandom().nextDouble(), 2.0D)
                : 1.0D - Math.pow(level.getRandom().nextDouble(), 2.0D);
        int range = IMPROVISED_MAX_FUSE_TICKS - IMPROVISED_MIN_FUSE_TICKS;
        return IMPROVISED_MIN_FUSE_TICKS + Mth.clamp((int) Math.round(centered * range), 0, range);
    }

    private static boolean hasFlintAndSteelInHand(Player player)
    {
        return player.getMainHandItem().is(Items.FLINT_AND_STEEL)
                || player.getOffhandItem().is(Items.FLINT_AND_STEEL);
    }

    private static void awardProfessionalIfKilledByHandGrenade(Player player, boolean wasAlive)
    {
        if (!wasAlive || !(player instanceof ServerPlayer serverPlayer) || !player.isDeadOrDying())
        {
            return;
        }

        awardAdvancement(serverPlayer, "professional");
    }

    private static void awardAdvancementIfKilled(Player player, boolean wasAlive, String path)
    {
        if (!wasAlive || !(player instanceof ServerPlayer serverPlayer) || !player.isDeadOrDying())
        {
            return;
        }

        awardAdvancement(serverPlayer, path);
    }

    private static void awardAdvancement(ServerPlayer serverPlayer, String path)
    {
        AdvancementHolder advancement = serverPlayer.server.getAdvancements().get(
                ResourceLocation.fromNamespaceAndPath(CloseQuarterCombat.MODID, path)
        );
        if (advancement != null)
        {
            serverPlayer.getAdvancements().award(advancement, path);
        }
    }

    private void awardInventoryAdvancements(ServerPlayer player)
    {
        if (this.type == ThrownGrenadeEntity.Type.HIGH_EXPLOSIVE_GRENADE)
        {
            awardAdvancement(player, "explosive_power");
        }
        else if (this.type == ThrownGrenadeEntity.Type.GIGA)
        {
            awardAdvancement(player, "explosive_power");
            awardAdvancement(player, "excessive_power");
        }
        else if (this.type == ThrownGrenadeEntity.Type.GIGA_GIGA)
        {
            awardAdvancement(player, "explosive_power");
            awardAdvancement(player, "excessive_power");
            awardAdvancement(player, "excessive_excessiveness");
        }
    }
}
