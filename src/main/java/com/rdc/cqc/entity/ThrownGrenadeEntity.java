package com.rdc.cqc.entity;

import com.rdc.cqc.item.CQCItems;
import java.util.List;
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
    private static final float HE_EXPLOSION_RADIUS = 6.0F;

    /** Радіус вибуху для DEMO гранати (меньший за TNT). */
    private static final float DEMO_EXPLOSION_RADIUS = 2.4F;

    /** Радіус вибуху для GIGA гранати (як TNT). */
    private static final float GIGA_EXPLOSION_RADIUS = 4.0F;

    /** Радіус газової хмари (стартовий). */
    private static final float GAS_CLOUD_RADIUS = 7.0F;

    /** Тривалість газової хмари у тіках (20 с = 400). */
    private static final int GAS_CLOUD_DURATION_TICKS = 400;

    /** Висота "газового стовпа" — кількість шарів AreaEffectCloud по вертикалі. */
    private static final int GAS_CLOUD_VERTICAL_LAYERS = 3;
    private static final double GAS_CLOUD_LAYER_OFFSET_Y = 1.4D;

    /** Радіус димової хмари (більший за газову). */
    private static final float SMOKE_CLOUD_RADIUS = 10.0F;

    /** Тривалість димової хмари у тіках (30 с = 600). */
    private static final int SMOKE_CLOUD_DURATION_TICKS = 600;

    /** Висота "димового стовпа" — кількість шарів AreaEffectCloud по вертикалі. */
    private static final int SMOKE_CLOUD_VERTICAL_LAYERS = 4;
    private static final double SMOKE_CLOUD_LAYER_OFFSET_Y = 1.5D;

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
        // Тип може ще не бути встановлений під час дуже ранньої ініціализації — захищаємось.
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
     * ВИПРАВЛЕНО: частинки тепер правильно синхронізуються на клієнти через levelEvent
     */
    private void detonate()
    {
        if (this.level().isClientSide()) return;

        Type type = getGrenadeType();
        switch (type)
        {
            case HE ->
            {
                // HE граната: шкода від осколків без ламання блоків
                spawnShrapnelAndDamage(HE_EXPLOSION_RADIUS, 70.0F);

                // 🔥 КЛЮЧ: Синхронізуємо частинки на клієнти через levelEvent
                // Код 2009 = EXPLOSION_LARGE_SMOKE (як TNT) — автоматично синхронізується
                this.level().levelEvent(2009,
                        new net.minecraft.core.BlockPos((int)this.getX(), (int)this.getY(), (int)this.getZ()),
                        0);

                this.level().playSound(
                        null,
                        this.getX(), this.getY(), this.getZ(),
                        SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.NEUTRAL,
                        1.0F, 1.2F
                );
            }
            case DEMO ->
            {
                // Демо граната: повний вибух з ламанням блоків (мала сила - в 5 разів менше)
                this.level().explode(
                        null,
                        this.getX(), this.getY(), this.getZ(),
                        DEMO_EXPLOSION_RADIUS,
                        Level.ExplosionInteraction.TNT
                );
            }
            case GIGA ->
            {
                // Гіга граната: потужний вибух з ламанням блоків (сила динаміту)
                this.level().explode(
                        null,
                        this.getX(), this.getY(), this.getZ(),
                        GIGA_EXPLOSION_RADIUS,
                        Level.ExplosionInteraction.TNT
                );
                // Також наносимо урагу від осколків
                spawnShrapnelAndDamage(GIGA_EXPLOSION_RADIUS, 70.0F);
                
                this.level().levelEvent(2009,
                        new net.minecraft.core.BlockPos((int)this.getX(), (int)this.getY(), (int)this.getZ()),
                        0);
            }
            case GAS ->
            {
                spawnPoisonCloud();
                spawnRandomGasParticles();
                this.level().playSound(
                        null,
                        this.getX(), this.getY(), this.getZ(),
                        SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.NEUTRAL,
                        1.0F, 1.2F
                );
            }
            case SMOKE ->
            {
                spawnSmokeCloud();
                spawnRandomSmokeParticles();
                this.level().playSound(
                        null,
                        this.getX(), this.getY(), this.getZ(),
                        SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.NEUTRAL,
                        0.8F, 1.0F
                );
            }
        }
    }

    /**
     * Спавнить осколки та наносить урагу всім живим істотам у радіусі.
     * Емітує частинки осколків у всіх напрямках від центру вибуху.
     */
    private void spawnShrapnelAndDamage(float radius, float damage)
    {
        damageAndSpawnShrapnel(this.level(), this.getX(), this.getY(), this.getZ(), radius, damage, (LivingEntity) this.getOwner());
    }

    /**
     * Статичний метод для ураження істот в радіусі вибуху від довільної позиції.
     * Використовується як для кинутих гранат, так і для вибухів в руці.
     *
     * ВИПРАВЛЕНО: Відокремлено від спавнювання частинок
     */
    public static void damageAndSpawnShrapnel(Level level, double x, double y, double z,
                                              float radius, float damage, LivingEntity thrower)
    {
        System.out.println("[GRENADE] damageAndSpawnShrapnel called at " + x + ", " + y + ", " + z + " with radius " + radius);

        // Знаходимо все живі істоти в радіусі
        List<LivingEntity> entities = level.getEntitiesOfClass(
                LivingEntity.class,
                new net.minecraft.world.phys.AABB(x - radius, y - radius, z - radius,
                        x + radius, y + radius, z + radius)
        );

        System.out.println("[GRENADE] Found " + entities.size() + " entities in radius");

        for (LivingEntity entity : entities)
        {
            double dist = entity.position().distanceTo(new Vec3(x, y, z));
            if (dist > radius)
            {
                continue; // Entity too far
            }

            // Шкода зменшується з відстанню
            float falloff = 1.0F - (float)(dist / radius);
            float actualDamage = damage * falloff;

            // Мінімум 1 урагу в центрі вибуху
            if (actualDamage < 1.0F) actualDamage = 1.0F;

            System.out.println("[GRENADE] Damaging entity " + entity.getName().getString() + " for " + actualDamage + " damage");

            DamageSource src = level.damageSources().thrown(null, thrower);
            entity.hurt(src, actualDamage);

            // Легкий штовхач від вибуху
            Vec3 entityPos = entity.position();
            Vec3 direction = entityPos.subtract(x, y, z).normalize();
            entity.knockback(0.5D * falloff, direction.x, direction.z);
        }
    }

    /**
     * Наносить шкоду всім живим істотам у радіусі, імітуючи осколки від гранати.
     */
    private void damageEntitiesInRadius(float radius, float damage)
    {
        List<LivingEntity> entities = this.level().getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(radius)
        );

        for (LivingEntity entity : entities)
        {
            if (entity == this.getOwner()) continue; // не ранимо власника

            double dist = this.distanceTo(entity);
            if (dist > radius) continue;

            // Шкода зменшується з відстанню
            float falloff = 1.0F - (float)(dist / radius);
            float actualDamage = damage * falloff;

            DamageSource src = this.damageSources().thrown(this, this.getOwner());
            entity.hurt(src, actualDamage);

            // Легкий штовхач від вибуху
            Vec3 direction = entity.position().subtract(this.position()).normalize();
            entity.knockback(0.5D * falloff, direction.x, direction.z);
        }
    }

    /**
     * Створює важку отруйну газову хмару, яка стелиться по землі
     * та утворює нерівномірні токсичні згустки.
     * Висновок: створює лише AreaEffectCloud, БЕЗ партіклів.
     */
    private void spawnPoisonCloud()
    {
        for (int i = 0; i < GAS_CLOUD_VERTICAL_LAYERS; i++)
        {
            double yOffset = i * (GAS_CLOUD_LAYER_OFFSET_Y * 0.4D);

            double offsetX = (this.random.nextDouble() - 0.5D) * 1.6D;

            double offsetZ = (this.random.nextDouble() - 0.5D) * 1.6D;

            float radius = GAS_CLOUD_RADIUS * (0.6F + this.random.nextFloat() * 0.6F);

            AreaEffectCloud cloud = new AreaEffectCloud(
                    this.level(),
                    this.getX() + offsetX,
                    this.getY() + yOffset,
                    this.getZ() + offsetZ
            );

            if (this.getOwner() instanceof LivingEntity owner)
            {
                cloud.setOwner(owner);
            }

            cloud.setRadius(radius);
            cloud.setRadiusOnUse(0.0F);
            cloud.setRadiusPerTick(0.001F);
            cloud.setWaitTime(3 + this.random.nextInt(8));
            cloud.setDuration(GAS_CLOUD_DURATION_TICKS);

            cloud.addEffect(new MobEffectInstance(
                    MobEffects.POISON,
                    600,
                    5
            ));

            cloud.addEffect(new MobEffectInstance(
                    MobEffects.CONFUSION,
                    600,
                    0
            ));

            this.level().addFreshEntity(cloud);
        }
    }

    /**
     * Нова система газу на базі noise-патерну.
     * Частинки спавняться як "щільні області хвилі", а не випадково.
     */
    private void spawnRandomGasParticles()
    {
        // 🔥 дуже повільний глобальний темп спавну
        if (this.random.nextFloat() < 0.85F)
        {
            return;
        }

        // -----------------------------------
        // 🌫 NOISE TIME (псевдо-час хвилі)
        // -----------------------------------
        double time = this.level().getGameTime() * 0.02D;

        // -----------------------------------
        // 🌫 центральна точка газу
        // -----------------------------------
        double centerX = this.getX();
        double centerY = this.getY();
        double centerZ = this.getZ();

        // -----------------------------------
        // 🌫 кількість "семплів поля"
        // -----------------------------------
        int samples = 6 + this.random.nextInt(4);

        for (int i = 0; i < samples; i++)
        {
            double angle = (Math.PI * 2.0D) * (i / (double) samples);

            // -----------------------------------
            // 🌫 NOISE-радіус (хвиля замість кола)
            // -----------------------------------
            double wave =
                    Math.sin(angle * 2.0D + time) * 0.5D +
                            Math.cos(angle * 3.0D - time * 1.3D) * 0.5D;

            double baseRadius =
                    GAS_CLOUD_RADIUS * (0.4D + wave * 0.4D);

            // -----------------------------------
            // 🌫 позиція в полі
            // -----------------------------------
            double x =
                    centerX + Math.cos(angle) * baseRadius;

            double z =
                    centerZ + Math.sin(angle) * baseRadius;

            // -----------------------------------
            // 🌫 вертикальний noise (низький газ)
            // -----------------------------------
            double y =
                    centerY +
                            (Math.sin(time + i) * 0.15D) +
                            this.random.nextDouble() * 0.1D;

            // -----------------------------------
            // 🌫 ще менше руху (майже статичний газ)
            // -----------------------------------
            double vx =
                    Math.sin(time + i) * 0.002D;

            double vy =
                    -0.002D;

            double vz =
                    Math.cos(time + i) * 0.002D;

            this.level().addParticle(
                    ParticleTypes.SNEEZE,
                    x, y, z,
                    vx, vy, vz
            );
        }

        // -----------------------------------
        // 🌫 рідкісні "сплески" (деталі життя газу)
        // -----------------------------------
        if (this.random.nextFloat() < 0.2F)
        {
            spawnGasMicroBursts();
        }
    }

    /**
     * Дуже рідкі мікро-сплески газу (локальні згустки).
     */
    private void spawnGasMicroBursts()
    {
        int count = 2 + this.random.nextInt(2);

        for (int i = 0; i < count; i++)
        {
            double x = this.getX() + (this.random.nextGaussian() * 0.2D);
            double y = this.getY() + this.random.nextDouble() * 0.15D;
            double z = this.getZ() + (this.random.nextGaussian() * 0.2D);

            this.level().addParticle(
                    ParticleTypes.SNEEZE,
                    x, y, z,
                    0.0D,
                    -0.002D,
                    0.0D
            );
        }
    }

    /**
     * Створює велику безшкідливу димову хмару для задимлення (SMOKE гранат).
     * На відміну від газової гранати, не має ефектів отруєння/нудоти.
     */
    private void spawnSmokeCloud()
    {
        for (int i = 0; i < SMOKE_CLOUD_VERTICAL_LAYERS; i++)
        {
            double yOffset = i * SMOKE_CLOUD_LAYER_OFFSET_Y;
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
            cloud.setRadius(SMOKE_CLOUD_RADIUS);
            cloud.setRadiusOnUse(0.0F);
            cloud.setRadiusPerTick(0.0F);
            cloud.setWaitTime(10);
            cloud.setDuration(SMOKE_CLOUD_DURATION_TICKS);
            // Дим без шкідливих ефектів - просто візуальний ефект
            cloud.setParticle(ParticleTypes.SMOKE);

            this.level().addFreshEntity(cloud);
        }
    }

    /**
     * Генерує випадкові сіро-білі частинки дима у великій зоні.
     */
    private void spawnRandomSmokeParticles()
    {
        int particleCount = 120;
        for (int i = 0; i < particleCount; i++)
        {
            // Випадкова позиція у межах більшої зони
            double angle = this.random.nextDouble() * Math.PI * 2;
            double distance = this.random.nextDouble() * SMOKE_CLOUD_RADIUS;
            double height = this.random.nextDouble() * (SMOKE_CLOUD_VERTICAL_LAYERS * SMOKE_CLOUD_LAYER_OFFSET_Y);

            double x = this.getX() + Math.cos(angle) * distance;
            double y = this.getY() + height;
            double z = this.getZ() + Math.sin(angle) * distance;

            // Уповільнена швидкість для реалістичного розповсюдження дима
            double vx = (this.random.nextDouble() - 0.5D) * 0.15D;
            double vy = this.random.nextDouble() * 0.05D;
            double vz = (this.random.nextDouble() - 0.5D) * 0.15D;

            this.level().addParticle(ParticleTypes.SMOKE, x, y, z, vx, vy, vz);
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
        HE,    // звичайна (grenade)
        DEMO,  // граната з ручкою
        GAS,   // газова
        SMOKE, // димова
        GIGA;  // гіга граната

        public Item getItem()
        {
            return switch (this)
            {
                case HE -> CQCItems.GRENADE.get();
                case DEMO -> CQCItems.DEMO_GRENADE.get();
                case GAS -> CQCItems.GAS_GRENADE.get();
                case SMOKE -> CQCItems.SMOKE_GRENADE.get();
                case GIGA -> CQCItems.GIGA_GRENADE.get();
            };
        }
    }
}   