package com.rdc.cqc.item;

import com.rdc.cqc.entity.ThrownGrenadeEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
        if (this.type == ThrownGrenadeEntity.Type.IMPACT_GRENADE)
        {
            return;
        }

        if (isPinPulled(stack))
        {
            return; // вже активовано
        }

        stack.set(CQCDataComponents.GRENADE_FUSE.get(), DEFAULT_FUSE_TICKS);

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
            if (fuse < 0) fuse = DEFAULT_FUSE_TICKS;

            ThrownGrenadeEntity grenade = ThrownGrenadeEntity.throwGrenade(level, player, this.type, stack);
            grenade.setFuse(fuse);
        }

        player.awardStat(Stats.ITEM_USED.get(this));

        if (!player.getAbilities().instabuild)
        {
            stack.shrink(1);
        }

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
        if (!isPinPulled(stack)) return;
        if (!(entity instanceof Player player)) return;

        int fuse = getRemainingFuse(stack);
        fuse--;

        if (fuse <= 0)
        {
            // Вибух у руці гравця.
            detonateInHand(level, player);
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
                ThrownGrenadeEntity.damageAndSpawnShrapnel(level, player.getX(), explosionY, player.getZ(), ThrownGrenadeEntity.FRAG_GRENADE_EXPLOSION_RADIUS, ThrownGrenadeEntity.FRAG_GRENADE_SHRAPNEL_DAMAGE, player);
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
            case HIGH_EXPLOSIVE_GRENADE ->
            {
                // High Explosive Grenade: повний вибух з ламанням блоків (мала сила).
                level.explode(
                        null,
                        player.getX(), player.getY() + 0.5D, player.getZ(),
                        ThrownGrenadeEntity.HIGH_EXPLOSIVE_GRENADE_EXPLOSION_RADIUS,
                        Level.ExplosionInteraction.TNT
                );
            }
            case IMPACT_GRENADE ->
            {
                level.explode(
                        null,
                        player.getX(), player.getY() + 0.5D, player.getZ(),
                        ThrownGrenadeEntity.IMPACT_GRENADE_EXPLOSION_RADIUS,
                        Level.ExplosionInteraction.TNT
                );
            }
            case STICKY_GRENADE ->
            {
                level.explode(
                        null,
                        player.getX(), player.getY() + 0.5D, player.getZ(),
                        ThrownGrenadeEntity.HIGH_EXPLOSIVE_GRENADE_EXPLOSION_RADIUS,
                        Level.ExplosionInteraction.TNT
                );
            }
            case GIGA ->
            {
                // Гіга граната: потужний вибух з ламанням блоків
                level.explode(
                        null,
                        player.getX(), player.getY() + 0.5D, player.getZ(),
                        ThrownGrenadeEntity.GIGA_EXPLOSION_RADIUS,
                        Level.ExplosionInteraction.TNT
                );
                // Також наносимо урагу від осколків
                ThrownGrenadeEntity.damageAndSpawnShrapnel(level, player.getX(), player.getY() + 0.5D, player.getZ(), ThrownGrenadeEntity.GIGA_EXPLOSION_RADIUS, 70.0F, player);
            }
            case GAS ->
            {
                // Створюємо тимчасову гранату-сутність, яка стане газовим емітером.
                ThrownGrenadeEntity dummy = new ThrownGrenadeEntity(level, player, ThrownGrenadeEntity.Type.GAS);
                dummy.setPos(player.getX(), player.getY() + 0.5D, player.getZ());
                level.addFreshEntity(dummy);
                dummy.setFuse(1); // вибухне на наступному тіку у власній позиції
            }
        }
    }
}
