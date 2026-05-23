package com.rdc.cqc.entity;

import com.rdc.cqc.item.CQCItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Сутність кинутої гранати. Один клас на всі типи, конкретний тип зберігається у
 * sync-data {@link #DATA_TYPE} (передається клієнту для правильного візуального
 * рендера через {@link #getDefaultItem()}).
 *
 * <p>Фізика:
 * <ul>
 *     <li>Гравітація 0.04 (трохи важче за сніжок).</li>
 *     <li>При зіткненні з блоком — м'який відскік з великими втратами енергії
 *         (близько 30% по горизонталі, 25% по вертикалі) — щоб граната не дуже
 *         далеко стрибала.</li>
 *     <li>При зіткненні з ентіті — несильний штовхач (knockback), граната продовжує літати.</li>
 *     <li>Фьюз = 60 тіків (3 с). При досягненні нуля — вибух відповідно до типу.</li>
 * </ul>
 */
public class ThrownGrenadeEntity extends ThrowableItemProjectile
{
    /** Тип гранати (порядковий номер у {@link Type}). Синхронізується з клієнтом для рендера. */
    private static final EntityDataAccessor<Integer> DATA_TYPE =
            SynchedEntityData.defineId(ThrownGrenadeEntity.class, EntityDataSerializers.INT);

    /** Чи граната зараз «лежить» (швидкість майже нуль). Клієнт використовує для зупинки обертання. */
    private static final EntityDataAccessor<Boolean> DATA_RESTING =
            SynchedEntityData.defineId(ThrownGrenadeEntity.class, EntityDataSerializers.BOOLEAN);

    /** Залишок фьюзу в тіках. Не синхронізується (логіка лише на сервері). */
    private int fuse = 60;

    /** Радіус вибуху для HE/демо гранат (TNT = 4.0F). */
    private static final float HE_EXPLOSION_RADIUS = 3.0F;

    /** Радіус газової хмари (стартовий). */
    private static final float GAS_CLOUD_RADIUS = 7.0F;

    /** Тривалість газової хмари у тіках (20 с = 400). */
    private static final int GAS_CLOUD_DURATION_TICKS = 400;

    /** Висота "газового стовпа" — кількість шарів AreaEffectCloud по вертикалі. */
    private static final int GAS_CLOUD_VERTICAL_LAYERS = 3;
    private static final double GAS_CLOUD_LAYER_OFFSET_Y = 1.4D;

    public ThrownGrenadeEntity(EntityType<? extends ThrownGrenadeEntity> entityType, Level level)
    {
        super(entityType, level);
    }

    /**
     * Серверний конструктор: створює гранату з заданим типом і кидальником.
     */
    public ThrownGrenadeEntity(Level level, LivingEntity thrower, Type type)
    {
        super(CQCEntities.THROWN_GRENADE.get(), thrower, level);
        this.entityData.set(DATA_TYPE, type.ordinal());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(DATA_TYPE, Type.HE.ordinal());
        builder.define(DATA_RESTING, Boolean.FALSE);
    }

    /** Чи граната зараз лежить (швидкість майже нуль). Використовується клієнтом для зупинки обертання. */
    public boolean isResting()
    {
        return this.entityData.get(DATA_RESTING);
    }

    /** Виставляє залишок фьюзу (у тіках). Використовується для «винесення» залишку з активованої гранати-предмета. */
    public void setFuse(int fuse)
    {
        this.fuse = fuse;
    }

    public Type getGrenadeType()
    {
        int idx = this.entityData.get(DATA_TYPE);
        Type[] values = Type.values();
        return values[Math.floorMod(idx, values.length)];
    }

    public void setGrenadeType(Type type)
    {
        this.entityData.set(DATA_TYPE, type.ordinal());
    }

    @Override
    protected Item getDefaultItem()
    {
        // Тип може ще не бути встановлений під час дуже ранньої ініціалізації — захищаємось.
        try
        {
            return getGrenadeType().getItem();
        }
        catch (Exception ignored)
        {
            return CQCItems.GRENADE.get();
        }
    }

    /** Гравітація трохи важча за стандартну. */
    @Override
    protected double getDefaultGravity()
    {
        return 0.04D;
    }

    /**
     * Основний тік: рух виконує super, далі — лічильник фьюзу та вибух на сервері.
     */
    @Override
    public void tick()
    {
        super.tick();

        // Лічильник + вибух + перерахунок resting-стану — лише на сервері.
        if (!this.level().isClientSide())
        {
            // Граната вважається «лежить», коли і onGround, і швидкість дуже маленька.
            // Це сигналізує клієнту, що крутити її більше не треба.
            boolean resting = this.onGround() && this.getDeltaMovement().lengthSqr() < 0.0025D;
            if (resting != this.entityData.get(DATA_RESTING))
            {
                this.entityData.set(DATA_RESTING, resting);
            }

            this.fuse--;

            if (this.fuse <= 0 && !this.isRemoved())
            {
                detonate();
                this.discard();
            }
        }
        else
        {
            // Невеликий «димок» з гранати, щоб видно було, що вона активна.
            if (this.tickCount % 2 == 0)
            {
                this.level().addParticle(
                        ParticleTypes.SMOKE,
                        this.getX(), this.getY() + 0.2D, this.getZ(),
                        0.0D, 0.02D, 0.0D
                );
            }
        }
    }

    /**
     * Зіткнення з блоком — м'який відскік з великими втратами енергії, без видалення гранати.
     */
    @Override
    protected void onHitBlock(BlockHitResult result)
    {
        // НЕ викликаємо super — інакше ThrowableItemProjectile сам себе discard'не.
        Vec3 velocity = this.getDeltaMovement();
        Vec3 reflected;

        // Множники втрат: відбита (нормальна) вісь ×0.15, ковзні осі ×0.5 (сильне тертя).
        switch (result.getDirection().getAxis())
        {
            case X -> reflected = new Vec3(-velocity.x * 0.15D, velocity.y * 0.5D, velocity.z * 0.5D);
            case Y -> reflected = new Vec3(velocity.x * 0.5D, -velocity.y * 0.1D, velocity.z * 0.5D);
            case Z -> reflected = new Vec3(velocity.x * 0.5D, velocity.y * 0.5D, -velocity.z * 0.15D);
            default -> reflected = velocity;
        }

        // Якщо швидкість майже нуль — обнулимо горизонталь, щоб не дрижала.
        if (reflected.horizontalDistanceSqr() < 0.01D)
        {
            reflected = new Vec3(0.0D, reflected.y, 0.0D);
        }
        if (Math.abs(reflected.y) < 0.08D)
        {
            reflected = new Vec3(reflected.x, 0.0D, reflected.z);
        }

        this.setDeltaMovement(reflected);

        // Маленький звук удару (тільки коли є помітна швидкість).
        if (velocity.lengthSqr() > 0.05D && !this.level().isClientSide())
        {
            this.level().playSound(
                    null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.WOOL_PLACE, SoundSource.NEUTRAL,
                    0.4F, 1.0F + (this.random.nextFloat() - 0.5F) * 0.2F
            );
        }
    }

    /**
     * Зіткнення з ентіті: символічна шкода 1, граната відскакує.
     */
    @Override
    protected void onHitEntity(EntityHitResult result)
    {
        super.onHitEntity(result);
        if (result.getEntity() instanceof LivingEntity living)
        {
            DamageSource src = this.damageSources().thrown(this, this.getOwner());
            living.hurt(src, 1.0F);
        }
        Vec3 v = this.getDeltaMovement();
        this.setDeltaMovement(v.scale(-0.3D));
    }

    /**
     * Основний хіт-колбек: НЕ використовуємо стандартне видалення з super.onHit().
     * Перевизначаємо щоб обробити блок/ентіті без знищення сутності.
     */
    @Override
    protected void onHit(HitResult result)
    {
        HitResult.Type type = result.getType();
        if (type == HitResult.Type.ENTITY)
        {
            onHitEntity((EntityHitResult) result);
        }
        else if (type == HitResult.Type.BLOCK)
        {
            onHitBlock((BlockHitResult) result);
        }
        // НЕ викликаємо super.onHit() — це би видалило сутність.
    }

    /**
     * Вибух гранати відповідно до типу. Викликається лише на сервері.
     */
    private void detonate()
    {
        if (this.level().isClientSide()) return;

        Type type = getGrenadeType();
        switch (type)
        {
            case HE, DEMO ->
            {
                // Передаємо null як source — щоб ваніль не виключала з шкоди власника гранати.
                // Граната все одно завдасть шкоди всім у радіусі, включно з кидальником.
                this.level().explode(
                        null,
                        this.getX(), this.getY(), this.getZ(),
                        HE_EXPLOSION_RADIUS,
                        Level.ExplosionInteraction.TNT
                );
            }
            case GAS ->
            {
                spawnPoisonCloud();
                this.level().playSound(
                        null,
                        this.getX(), this.getY(), this.getZ(),
                        SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL,
                        1.0F, 1.2F
                );
            }
        }
    }

    /**
     * Створює AreaEffectCloud з ефектом отруєння замість вибуху.
     * Кілька шарів по вертикалі — щоб хмара була високою, а не лише по землі.
     */
    private void spawnPoisonCloud()
    {
        for (int i = 0; i < GAS_CLOUD_VERTICAL_LAYERS; i++)
        {
            double yOffset = i * GAS_CLOUD_LAYER_OFFSET_Y;
            AreaEffectCloud cloud = new AreaEffectCloud(
                    this.level(),
                    this.getX(),
                    this.getY() + yOffset,
                    this.getZ()
            );
            if (this.getOwner() instanceof LivingEntity owner)
            {
                cloud.setOwner(owner);
            }
            cloud.setRadius(GAS_CLOUD_RADIUS);
            cloud.setRadiusOnUse(0.0F);          // не зменшується від використання
            cloud.setRadiusPerTick(0.0F);        // тримає радіус увесь час життя
            cloud.setWaitTime(10);
            cloud.setDuration(GAS_CLOUD_DURATION_TICKS);
            // Отруєння — кожні 25 тіків може накладатися; setReapplicationDelay = 25 за замовч.
            cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 120, 1));
            // Тошнота (Nausea) — «хитає» камеру жертви, поки вона у хмарі.
            // Тривалість трохи більша за reapplicationDelay (25), щоб ефект стабільно
            // поновлювався, але швидко затухав після виходу з хмари.
            cloud.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0));
            // Зеленувата димна хмара.
            cloud.setParticle(ParticleTypes.SNEEZE);

            this.level().addFreshEntity(cloud);
        }
    }

    /**
     * Допоміжний метод для метання гранати з руки гравця/мобу.
     */
    public static ThrownGrenadeEntity throwGrenade(Level level, LivingEntity thrower, Type type, ItemStack stack)
    {
        ThrownGrenadeEntity grenade = new ThrownGrenadeEntity(level, thrower, type);
        grenade.setItem(stack.copyWithCount(1));
        // Швидкість метання ще трохи зменшено (0.85 → 0.7) для м'якшого кидка.
        grenade.shootFromRotation(thrower, thrower.getXRot(), thrower.getYRot(), -20.0F, 0.7F, 1.0F);
        level.addFreshEntity(grenade);
        return grenade;
    }

    /** Тип гранати — визначає поведінку вибуху та item-модель для рендеру. */
    public enum Type
    {
        HE,    // звичайна (granade)
        DEMO,  // граната з ручкою
        GAS;   // газова

        public Item getItem()
        {
            return switch (this)
            {
                case HE -> CQCItems.GRENADE.get();
                case DEMO -> CQCItems.DEMO_GRENADE.get();
                case GAS -> CQCItems.GAS_GRENADE.get();
            };
        }
    }
}
