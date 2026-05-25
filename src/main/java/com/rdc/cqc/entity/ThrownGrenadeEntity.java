package com.rdc.cqc.entity;

import com.rdc.cqc.item.CQCItems;
import com.rdc.cqc.item.CQCDataComponents;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
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
 *     <li>Фьюз = 100 тіків (5 с). При досягненні нуля — вибух відповідно до типу.</li>
 * </ul>
 */
public class ThrownGrenadeEntity extends ThrowableItemProjectile
{
    private static final SoundEvent GRENADE_BOUNCE_SOUND = SoundEvent.createVariableRangeEvent(
            ResourceLocation.withDefaultNamespace("block.bone_block.break")
    );

    /** Giga важча, тому летить помітно ближче за інші гранати. */
    private static final float DEFAULT_THROW_VELOCITY = 0.7F;
    private static final float GIGA_THROW_VELOCITY = 0.45F;

    /** Тип гранати (порядковий номер у {@link Type}). Синхронізується з клієнтом для рендера. */
    private static final EntityDataAccessor<Integer> DATA_TYPE =
            SynchedEntityData.defineId(ThrownGrenadeEntity.class, EntityDataSerializers.INT);

    /** Чи граната зараз «лежить» (швидкість майже нуль). Клієнт використовує для зупинки обертання. */
    private static final EntityDataAccessor<Boolean> DATA_RESTING =
            SynchedEntityData.defineId(ThrownGrenadeEntity.class, EntityDataSerializers.BOOLEAN);

    /** Smoke-граната після детонації стає невидимим емітером димової завіси. */
    private static final EntityDataAccessor<Boolean> DATA_SMOKE_EMITTING =
            SynchedEntityData.defineId(ThrownGrenadeEntity.class, EntityDataSerializers.BOOLEAN);

    /** Gas-граната після детонації стає невидимим емітером низького газового шару. */
    private static final EntityDataAccessor<Boolean> DATA_GAS_EMITTING =
            SynchedEntityData.defineId(ThrownGrenadeEntity.class, EntityDataSerializers.BOOLEAN);

    /** Залишок фьюзу в тіках. Не синхронізується (логіка лише на сервері). */
    private int fuse = 100;
    private int smokeEmitterAge = 0;
    private int gasEmitterAge = 0;
    private boolean stickyStuck = false;
    private int stickyTargetId = -1;
    private Vec3 stickyEntityOffset = Vec3.ZERO;
    private Vec3 magneticJetDirection = Vec3.ZERO;

    /** Радіус ураження для осколкової гранати. */
    public static final float FRAG_GRENADE_EXPLOSION_RADIUS = 10.0F;

    /** Максимальна шкода від осколків Frag Grenade в центрі вибуху. */
    public static final float FRAG_GRENADE_SHRAPNEL_DAMAGE = 90.0F;

    /** Радіус вибуху для High Explosive Grenade (менший за TNT). */
    public static final float HIGH_EXPLOSIVE_GRENADE_EXPLOSION_RADIUS = 2.2F;

    /** Радіус вибуху для Impact Grenade — приблизно 66% від фугасної. */
    public static final float IMPACT_GRENADE_EXPLOSION_RADIUS = HIGH_EXPLOSIVE_GRENADE_EXPLOSION_RADIUS * 0.66F;

    /** Радіус вибуху для кумулятивної гранати — 20% від фугасної. */
    public static final float HEAT_GRENADE_EXPLOSION_RADIUS = HIGH_EXPLOSIVE_GRENADE_EXPLOSION_RADIUS * 0.2F;

    /** Шкода при прямому влучанні контактних гранат у моба/гравця. */
    private static final float IMPACT_GRENADE_DIRECT_HIT_DAMAGE = 30.0F;
    private static final float HEAT_GRENADE_DIRECT_HIT_DAMAGE = 150.0F;

    /** Довжина видимого кумулятивного струменя та відстань вибуху від точки удару. */
    private static final double SHAPED_CHARGE_JET_LENGTH = 3.0D;
    private static final double SHAPED_CHARGE_EXPLOSION_DISTANCE = 4.0D;
    private static final int MAGNETIC_GRENADE_FUSE_TICKS = 200;

    /** Радіус вибуху для GIGA гранати (трохи сильніший за TNT). */
    public static final float GIGA_EXPLOSION_RADIUS = 4.5F;

    /** Радіус газової хмари (стартовий). */
    private static final float GAS_CLOUD_RADIUS = 7.0F;

    /** Тривалість газової хмари у тіках (20 с = 400). */
    private static final int GAS_CLOUD_DURATION_TICKS = 600;

    /** Час розростання газового шару до повного радіуса (5 с). */
    private static final int GAS_CLOUD_GROWTH_TICKS = 100;

    /** Газ стелиться низько над підлогою. */
    private static final double GAS_CLOUD_HEIGHT = 0.75D;
    private static final double GAS_SURFACE_SCAN_UP = 4.0D;
    private static final double GAS_SURFACE_SCAN_DOWN = 3.0D;

    /** Радіус димової завіси після розростання. */
    private static final float SMOKE_CLOUD_RADIUS = 6.0F;

    /** Тривалість димової хмари у тіках (30 с = 600). */
    private static final int SMOKE_CLOUD_DURATION_TICKS = 1200;

    /** Час розростання димової завіси до повного радіуса (5 с). */
    private static final int SMOKE_CLOUD_GROWTH_TICKS = 100;

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
        builder.define(DATA_TYPE, Type.FRAG_GRENADE.ordinal());
        builder.define(DATA_RESTING, Boolean.FALSE);
        builder.define(DATA_SMOKE_EMITTING, Boolean.FALSE);
        builder.define(DATA_GAS_EMITTING, Boolean.FALSE);
    }

    /** Чи граната зараз лежить (швидкість майже нуль). Використовується клієнтом для зупинки обертання. */
    public boolean isResting()
    {
        return this.entityData.get(DATA_RESTING);
    }

    public boolean isSmokeEmitting()
    {
        return this.entityData.get(DATA_SMOKE_EMITTING);
    }

    public boolean isGasEmitting()
    {
        return this.entityData.get(DATA_GAS_EMITTING);
    }

    /** Виставляє залишок фьюзу (у тіках). Використовується для «винесення» залишку з активованої гранати-предмета. */
    public void setFuse(int fuse)
    {
        this.fuse = fuse;
    }

    @Override
    public boolean isPickable()
    {
        return getGrenadeType() != Type.IMPACT_GRENADE
                && getGrenadeType() != Type.SHAPED_CHARGE_GRENADE
                && getGrenadeType() != Type.MAGNETIC_GRENADE
                && !isSmokeEmitting()
                && !isGasEmitting();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand)
    {
        if (isSmokeEmitting() || isGasEmitting() || this.fuse <= 0)
        {
            return InteractionResult.PASS;
        }

        if (!this.level().isClientSide())
        {
            ItemStack pickedStack = getGrenadeType().getItem().getDefaultInstance();
            pickedStack.set(CQCDataComponents.GRENADE_FUSE.get(), this.fuse);

            if (!player.getInventory().add(pickedStack))
            {
                player.drop(pickedStack, false);
            }

            this.level().playSound(
                    null,
                    this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ITEM_PICKUP,
                    SoundSource.PLAYERS,
                    0.35F,
                    1.0F + this.random.nextFloat() * 0.2F
            );
            this.discard();
        }

        return InteractionResult.sidedSuccess(this.level().isClientSide());
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
            return CQCItems.FRAG_GRENADE.get();
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

        if (isSmokeEmitting())
        {
            if (!this.level().isClientSide())
            {
                this.setDeltaMovement(Vec3.ZERO);
                spawnSmokeEmitterTick();
                this.smokeEmitterAge++;
                if (this.smokeEmitterAge >= SMOKE_CLOUD_DURATION_TICKS)
                {
                    this.discard();
                }
            }
            return;
        }

        if (isGasEmitting())
        {
            if (!this.level().isClientSide())
            {
                this.setDeltaMovement(Vec3.ZERO);
                spawnGasEmitterTick();
                applyGasEffectsTick();
                this.gasEmitterAge++;
                if (this.gasEmitterAge >= GAS_CLOUD_DURATION_TICKS)
                {
                    this.discard();
                }
            }
            return;
        }

        // Лічильник + вибух + перерахунок resting-стану — лише на сервері.
        if (!this.level().isClientSide())
        {
            if (getGrenadeType() == Type.STICKY_GRENADE)
            {
                if (!this.stickyStuck)
                {
                    return;
                }

                updateStickyAttachment();
            }
            else if (getGrenadeType() == Type.MAGNETIC_GRENADE && !this.stickyStuck)
            {
                return;
            }

            // Після приземлення стан лишається true, щоб дрібне ковзання не запускало spin знову.
            boolean resting = this.entityData.get(DATA_RESTING) || this.onGround();
            if (resting != this.entityData.get(DATA_RESTING))
            {
                this.entityData.set(DATA_RESTING, resting);
            }

            this.fuse--;

            if (this.fuse <= 0 && !this.isRemoved())
            {
                Type type = getGrenadeType();
                detonate();
                if (type != Type.SMOKE && type != Type.GAS)
                {
                    this.discard();
                }
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
        if (tryImpactDetonate())
        {
            return;
        }

        if (tryShapedChargeDetonate(result.getLocation(), this.getDeltaMovement()))
        {
            return;
        }

        if (tryMagneticStickToBlock(result))
        {
            return;
        }

        if (tryStickToBlock(result))
        {
            return;
        }

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

        if (!this.level().isClientSide()
                && result.getDirection() == net.minecraft.core.Direction.UP
                && reflected.y == 0.0D)
        {
            this.entityData.set(DATA_RESTING, Boolean.TRUE);
        }

        // Маленький звук удару (тільки коли є помітна швидкість).
        if (velocity.lengthSqr() > 0.05D && !this.level().isClientSide())
        {
            this.level().playSound(
                    null, this.getX(), this.getY(), this.getZ(),
                    GRENADE_BOUNCE_SOUND, SoundSource.NEUTRAL,
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
        applyDirectHitDamage(result);

        if (tryImpactDetonate())
        {
            return;
        }

        if (tryShapedChargeDetonate(result.getLocation(), this.getDeltaMovement()))
        {
            return;
        }

        if (tryStickToEntity(result))
        {
            return;
        }

        super.onHitEntity(result);
        if (result.getEntity() instanceof LivingEntity living)
        {
            DamageSource src = this.damageSources().thrown(this, this.getOwner());
            living.hurt(src, 1.0F);
        }
        Vec3 v = this.getDeltaMovement();
        this.setDeltaMovement(v.scale(-0.3D));
    }

    private void applyDirectHitDamage(EntityHitResult result)
    {
        if (this.level().isClientSide() || !(result.getEntity() instanceof LivingEntity living))
        {
            return;
        }

        float damage = switch (getGrenadeType())
        {
            case IMPACT_GRENADE -> IMPACT_GRENADE_DIRECT_HIT_DAMAGE;
            case SHAPED_CHARGE_GRENADE -> HEAT_GRENADE_DIRECT_HIT_DAMAGE;
            default -> 0.0F;
        };

        if (damage <= 0.0F)
        {
            return;
        }

        DamageSource src = this.damageSources().thrown(this, this.getOwner());
        living.hurt(src, damage);
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

    private boolean tryImpactDetonate()
    {
        if (getGrenadeType() != Type.IMPACT_GRENADE)
        {
            return false;
        }

        if (!this.level().isClientSide() && !this.isRemoved())
        {
            detonate();
            this.discard();
        }

        return true;
    }

    private boolean tryShapedChargeDetonate(Vec3 impactPosition, Vec3 impactVelocity)
    {
        if (getGrenadeType() != Type.SHAPED_CHARGE_GRENADE)
        {
            return false;
        }

        if (!this.level().isClientSide() && !this.isRemoved())
        {
            detonateShapedCharge(impactPosition, impactVelocity);
            this.discard();
        }

        return true;
    }

    private void detonateShapedCharge(Vec3 impactPosition, Vec3 impactVelocity)
    {
        Vec3 direction = impactVelocity.lengthSqr() > 1.0E-4D
                ? impactVelocity.normalize()
                : this.getLookAngle().normalize();

        spawnShapedChargeJetParticles(impactPosition, direction);

        Vec3 explosionPosition = impactPosition.add(direction.scale(SHAPED_CHARGE_EXPLOSION_DISTANCE));
        this.level().explode(
                null,
                explosionPosition.x, explosionPosition.y, explosionPosition.z,
                HEAT_GRENADE_EXPLOSION_RADIUS,
                Level.ExplosionInteraction.TNT
        );
    }

    private void detonateMagneticGrenade()
    {
        Vec3 direction = this.magneticJetDirection.lengthSqr() > 1.0E-4D
                ? this.magneticJetDirection.normalize()
                : this.getLookAngle().normalize();

        Vec3 origin = this.position();
        spawnShapedChargeJetParticles(origin, direction);

        Vec3 explosionPosition = origin.add(direction.scale(SHAPED_CHARGE_EXPLOSION_DISTANCE));
        this.level().explode(
                null,
                origin.x, origin.y, origin.z,
                HIGH_EXPLOSIVE_GRENADE_EXPLOSION_RADIUS,
                Level.ExplosionInteraction.TNT
        );
        this.level().explode(
                null,
                explosionPosition.x, explosionPosition.y, explosionPosition.z,
                HIGH_EXPLOSIVE_GRENADE_EXPLOSION_RADIUS,
                Level.ExplosionInteraction.TNT
        );
    }

    private void spawnShapedChargeJetParticles(Vec3 origin, Vec3 direction)
    {
        if (!(this.level() instanceof ServerLevel serverLevel))
        {
            return;
        }

        int points = 18;
        for (int i = 0; i <= points; i++)
        {
            double progress = i / (double) points;
            Vec3 position = origin.add(direction.scale(SHAPED_CHARGE_JET_LENGTH * progress));
            Vec3 velocity = direction.scale(0.02D + progress * 0.04D);

            serverLevel.sendParticles(
                    ParticleTypes.FLAME,
                    position.x, position.y, position.z,
                    2,
                    0.025D, 0.025D, 0.025D,
                    0.01D
            );
            serverLevel.sendParticles(
                    ParticleTypes.SMALL_FLAME,
                    position.x, position.y, position.z,
                    0,
                    velocity.x, velocity.y, velocity.z,
                    1.0D
            );
        }
    }

    private boolean tryStickToBlock(BlockHitResult result)
    {
        if (getGrenadeType() != Type.STICKY_GRENADE)
        {
            return false;
        }

        if (!this.level().isClientSide() && !this.stickyStuck)
        {
            Vec3 normal = Vec3.atLowerCornerOf(result.getDirection().getNormal()).scale(0.08D);
            stickAt(result.getLocation().add(normal), 100);
        }

        return true;
    }

    private boolean tryMagneticStickToBlock(BlockHitResult result)
    {
        if (getGrenadeType() != Type.MAGNETIC_GRENADE)
        {
            return false;
        }

        if (!this.level().isClientSide() && !this.stickyStuck)
        {
            Vec3 velocity = this.getDeltaMovement();
            this.magneticJetDirection = velocity.lengthSqr() > 1.0E-4D
                    ? velocity.normalize()
                    : this.getLookAngle().normalize();
            Vec3 normal = Vec3.atLowerCornerOf(result.getDirection().getNormal()).scale(0.08D);
            stickAt(result.getLocation().add(normal), MAGNETIC_GRENADE_FUSE_TICKS);
        }

        return true;
    }

    private boolean tryStickToEntity(EntityHitResult result)
    {
        if (getGrenadeType() != Type.STICKY_GRENADE)
        {
            return false;
        }

        if (!this.level().isClientSide() && !this.stickyStuck)
        {
            Entity target = result.getEntity();
            this.stickyTargetId = target.getId();
            this.stickyEntityOffset = result.getLocation().subtract(target.position());
            stickAt(result.getLocation(), 100);
        }

        return true;
    }

    private void stickAt(Vec3 position, int fuseTicks)
    {
        this.stickyStuck = true;
        this.fuse = fuseTicks;
        this.setNoGravity(true);
        this.setDeltaMovement(Vec3.ZERO);
        this.setPos(position.x, position.y, position.z);
        this.entityData.set(DATA_RESTING, Boolean.TRUE);

        this.level().playSound(
                null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.SLIME_SQUISH, SoundSource.NEUTRAL,
                0.65F, 0.85F + this.random.nextFloat() * 0.25F
        );
    }

    private void updateStickyAttachment()
    {
        this.setDeltaMovement(Vec3.ZERO);

        if (this.stickyTargetId < 0)
        {
            return;
        }

        if (!(this.level() instanceof ServerLevel serverLevel))
        {
            return;
        }

        Entity target = serverLevel.getEntity(this.stickyTargetId);
        if (target == null || target.isRemoved())
        {
            this.stickyTargetId = -1;
            return;
        }

        Vec3 attachedPosition = target.position().add(this.stickyEntityOffset);
        this.setPos(attachedPosition.x, attachedPosition.y, attachedPosition.z);
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
            case FRAG_GRENADE ->
            {
                // Frag Grenade: шкода від осколків без ламання блоків.
                spawnShrapnelAndDamage(FRAG_GRENADE_EXPLOSION_RADIUS, FRAG_GRENADE_SHRAPNEL_DAMAGE);
                spawnFragExplosionParticles(this.level(), this.getX(), this.getY() + 0.25D, this.getZ());
                spawnShrapnelSmokeBurst(this.level(), this.getX(), this.getY() + 0.25D, this.getZ(), FRAG_GRENADE_EXPLOSION_RADIUS, this.random);

                // 🔥 КЛЮЧ: Синхронізуємо частинки на клієнти через levelEvent
                // Код 2009 = EXPLOSION_LARGE_SMOKE (як TNT) — автоматично синхронізується
                this.level().levelEvent(2009,
                        new net.minecraft.core.BlockPos((int)this.getX(), (int)this.getY(), (int)this.getZ()),
                        0);

                this.level().playSound(
                        null,
                        this.getX(), this.getY(), this.getZ(),
                        SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL,
                        1.6F, 1.0F
                );
            }
            case HIGH_EXPLOSIVE_GRENADE ->
            {
                // High Explosive Grenade: повний вибух з ламанням блоків, але слабше за TNT.
                this.level().explode(
                        null,
                        this.getX(), this.getY(), this.getZ(),
                        HIGH_EXPLOSIVE_GRENADE_EXPLOSION_RADIUS,
                        Level.ExplosionInteraction.TNT
                );
            }
            case IMPACT_GRENADE ->
            {
                this.level().explode(
                        null,
                        this.getX(), this.getY(), this.getZ(),
                        IMPACT_GRENADE_EXPLOSION_RADIUS,
                        Level.ExplosionInteraction.TNT
                );
            }
            case SHAPED_CHARGE_GRENADE ->
            {
                Vec3 direction = this.getDeltaMovement().lengthSqr() > 1.0E-4D
                        ? this.getDeltaMovement()
                        : this.getLookAngle();
                detonateShapedCharge(this.position(), direction);
            }
            case MAGNETIC_GRENADE ->
            {
                detonateMagneticGrenade();
            }
            case STICKY_GRENADE ->
            {
                this.level().explode(
                        null,
                        this.getX(), this.getY(), this.getZ(),
                        HIGH_EXPLOSIVE_GRENADE_EXPLOSION_RADIUS,
                        Level.ExplosionInteraction.TNT
                );
            }
            case GIGA ->
            {
                // Гіга граната: потужний вибух з ламанням блоків, трохи сильніше за TNT.
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
                startGasEmitter();
                this.level().playSound(
                        null,
                        this.getX(), this.getY(), this.getZ(),
                        SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.NEUTRAL,
                        1.0F, 1.2F
                );
            }
            case SMOKE ->
            {
                startSmokeEmitter();
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

    /** Додає ванільний TNT-like спалах вибухових частинок для Frag Grenade без ламання блоків. */
    public static void spawnFragExplosionParticles(Level level, double x, double y, double z)
    {
        if (!(level instanceof ServerLevel serverLevel))
        {
            return;
        }

        serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        serverLevel.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 8, 0.9D, 0.45D, 0.9D, 0.02D);
    }

    /**
     * Димові "штрихи" від центру вибуху назовні, щоб Frag Grenade виглядала як осколкова.
     */
    public static void spawnShrapnelSmokeBurst(Level level, double x, double y, double z,
                                               float radius, RandomSource random)
    {
        if (!(level instanceof ServerLevel serverLevel))
        {
            return;
        }

        int rays = 34;
        int pointsPerRay = 8;

        for (int i = 0; i < rays; i++)
        {
            double yaw = random.nextDouble() * Math.PI * 2.0D;
            double yDirection = random.nextDouble() * 2.0D - 1.0D;
            double horizontal = Math.sqrt(Math.max(0.0D, 1.0D - yDirection * yDirection));
            Vec3 direction = new Vec3(
                    Math.cos(yaw) * horizontal,
                    yDirection,
                    Math.sin(yaw) * horizontal
            ).normalize();

            double length = radius * (0.8D + random.nextDouble() * 0.8D);
            for (int point = 1; point <= pointsPerRay; point++)
            {
                double progress = point / (double) pointsPerRay;
                Vec3 position = new Vec3(x, y, z).add(direction.scale(length * progress));
                Vec3 velocity = direction.scale(0.10D + progress * 0.08D);

                serverLevel.sendParticles(
                        ParticleTypes.SMOKE,
                        position.x, position.y, position.z,
                        0,
                        velocity.x, velocity.y, velocity.z,
                        0.8D
                );
            }
        }
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

            if (!canShrapnelReach(level, new Vec3(x, y, z), entity))
            {
                continue;
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

            if (!canShrapnelReach(this.level(), this.position(), entity))
            {
                continue;
            }

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

    private static boolean canShrapnelReach(Level level, Vec3 origin, LivingEntity entity)
    {
        double targetX = entity.getX();
        double targetY = entity.getY();
        double targetZ = entity.getZ();

        return hasClearShrapnelPath(level, origin, entity, new Vec3(targetX, entity.getEyeY(), targetZ))
                || hasClearShrapnelPath(level, origin, entity, new Vec3(targetX, targetY + entity.getBbHeight() * 0.5D, targetZ))
                || hasClearShrapnelPath(level, origin, entity, new Vec3(targetX, targetY + 0.2D, targetZ));
    }

    private static boolean hasClearShrapnelPath(Level level, Vec3 origin, LivingEntity entity, Vec3 target)
    {
        BlockHitResult hit = level.clip(new ClipContext(
                origin,
                target,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                entity
        ));

        if (hit.getType() == HitResult.Type.MISS)
        {
            return true;
        }

        return origin.distanceToSqr(hit.getLocation()) >= origin.distanceToSqr(target) - 0.25D;
    }

    private void startSmokeEmitter()
    {
        this.entityData.set(DATA_SMOKE_EMITTING, Boolean.TRUE);
        this.entityData.set(DATA_RESTING, Boolean.TRUE);
        this.smokeEmitterAge = 0;
        this.setNoGravity(true);
        this.setDeltaMovement(Vec3.ZERO);
    }

    private void startGasEmitter()
    {
        this.entityData.set(DATA_GAS_EMITTING, Boolean.TRUE);
        this.entityData.set(DATA_RESTING, Boolean.TRUE);
        this.gasEmitterAge = 0;
        this.setNoGravity(true);
        this.setDeltaMovement(Vec3.ZERO);
    }

    private void spawnSmokeEmitterTick()
    {
        if (!(this.level() instanceof ServerLevel serverLevel))
        {
            return;
        }

        double growth = Math.min(1.0D, this.smokeEmitterAge / (double) SMOKE_CLOUD_GROWTH_TICKS);
        double easedGrowth = growth * growth * (3.0D - 2.0D * growth);
        double radius = 0.25D + SMOKE_CLOUD_RADIUS * easedGrowth;
        int particles = 8 + (int) (34 * easedGrowth);

        for (int i = 0; i < particles; i++)
        {
            double angle = this.random.nextDouble() * Math.PI * 2.0D;
            double distance = Math.sqrt(this.random.nextDouble()) * radius;
            double height = 0.05D + this.random.nextDouble() * (0.8D + 3.8D * easedGrowth);
            double wave = Math.sin((this.smokeEmitterAge + i * 13) * 0.13D) * 0.35D * easedGrowth;

            double x = this.getX() + Math.cos(angle) * distance + Math.cos(angle + Math.PI * 0.5D) * wave;
            double y = this.getY() + height;
            double z = this.getZ() + Math.sin(angle) * distance + Math.sin(angle + Math.PI * 0.5D) * wave;

            double vx = Math.cos(angle) * 0.01D * easedGrowth + this.random.nextGaussian() * 0.01D;
            double vy = 0.025D + this.random.nextDouble() * (0.035D + 0.025D * easedGrowth);
            double vz = Math.sin(angle) * 0.01D * easedGrowth + this.random.nextGaussian() * 0.01D;

            serverLevel.sendParticles(
                    easedGrowth < 0.55D ? ParticleTypes.SMOKE : ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    x, y, z,
                    0,
                    vx, vy, vz,
                    1.0D
            );
        }
    }

    private void spawnGasEmitterTick()
    {
        if (!(this.level() instanceof ServerLevel serverLevel))
        {
            return;
        }

        double growth = Math.min(1.0D, this.gasEmitterAge / (double) GAS_CLOUD_GROWTH_TICKS);
        double easedGrowth = growth * growth * (3.0D - 2.0D * growth);
        double radius = 0.2D + GAS_CLOUD_RADIUS * easedGrowth;
        int particles = 2 + (int) (7 * easedGrowth);

        for (int i = 0; i < particles; i++)
        {
            double angle = this.random.nextDouble() * Math.PI * 2.0D;
            double distance = Math.sqrt(this.random.nextDouble()) * radius;
            double wave = Math.sin((this.gasEmitterAge + i * 17) * 0.12D) * 0.25D * easedGrowth;

            double x = this.getX() + Math.cos(angle) * distance + Math.cos(angle + Math.PI * 0.5D) * wave;
            double z = this.getZ() + Math.sin(angle) * distance + Math.sin(angle + Math.PI * 0.5D) * wave;
            double surfaceY = findGasSurfaceY(x, z);
            if (Double.isNaN(surfaceY))
            {
                continue;
            }

            double y = surfaceY + this.random.nextDouble() * GAS_CLOUD_HEIGHT;

            double vx = Math.cos(angle) * 0.012D * easedGrowth + this.random.nextGaussian() * 0.006D;
            double vy = this.random.nextDouble() * 0.006D;
            double vz = Math.sin(angle) * 0.012D * easedGrowth + this.random.nextGaussian() * 0.006D;

            serverLevel.sendParticles(
                    ParticleTypes.SNEEZE,
                    x, y, z,
                    0,
                    vx, vy, vz,
                    1.0D
            );
        }
    }

    private void applyGasEffectsTick()
    {
        if (this.gasEmitterAge % 20 != 0)
        {
            return;
        }

        double growth = Math.min(1.0D, this.gasEmitterAge / (double) GAS_CLOUD_GROWTH_TICKS);
        double easedGrowth = growth * growth * (3.0D - 2.0D * growth);
        double radius = 0.2D + GAS_CLOUD_RADIUS * easedGrowth;

        List<LivingEntity> entities = this.level().getEntitiesOfClass(
                LivingEntity.class,
                new net.minecraft.world.phys.AABB(
                        this.getX() - radius, this.getY() - GAS_SURFACE_SCAN_DOWN, this.getZ() - radius,
                        this.getX() + radius, this.getY() + GAS_SURFACE_SCAN_UP + 2.0D, this.getZ() + radius
                )
        );

        for (LivingEntity entity : entities)
        {
            if (entity.position().distanceTo(new Vec3(this.getX(), entity.getY(), this.getZ())) > radius)
            {
                continue;
            }

            double surfaceY = findGasSurfaceY(entity.getX(), entity.getZ());
            if (Double.isNaN(surfaceY) || entity.getY() < surfaceY - 0.25D || entity.getY() > surfaceY + 1.6D)
            {
                continue;
            }

            entity.addEffect(new MobEffectInstance(MobEffects.POISON, GAS_CLOUD_DURATION_TICKS, 4));
            entity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, GAS_CLOUD_DURATION_TICKS, 0));
        }
    }

    private double findGasSurfaceY(double x, double z)
    {
        int blockX = Mth.floor(x);
        int blockZ = Mth.floor(z);
        int startY = Mth.floor(this.getY() + GAS_SURFACE_SCAN_UP);
        int minY = Mth.floor(this.getY() - GAS_SURFACE_SCAN_DOWN);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(blockX, startY, blockZ);

        for (int y = startY; y >= minY; y--)
        {
            pos.set(blockX, y, blockZ);
            if (this.level().getBlockState(pos).isFaceSturdy(this.level(), pos, Direction.UP))
            {
                return y + 1.05D;
            }
        }

        return Double.NaN;
    }

    /**
     * Допоміжний метод для метання гранати з руки гравця/мобу.
     */
    public static ThrownGrenadeEntity throwGrenade(Level level, LivingEntity thrower, Type type, ItemStack stack)
    {
        ThrownGrenadeEntity grenade = new ThrownGrenadeEntity(level, thrower, type);
        grenade.setItem(stack.copyWithCount(1));
        float velocity = type == Type.GIGA ? GIGA_THROW_VELOCITY : DEFAULT_THROW_VELOCITY;
        grenade.shootFromRotation(thrower, thrower.getXRot(), thrower.getYRot(), -20.0F, velocity, 1.0F);
        level.addFreshEntity(grenade);
        return grenade;
    }

    /** Тип гранати — визначає поведінку вибуху та item-модель для рендеру. */
    public enum Type
    {
        FRAG_GRENADE,            // осколкова граната
        HIGH_EXPLOSIVE_GRENADE,  // фугасна граната
        IMPACT_GRENADE,          // ударна граната
        STICKY_GRENADE,          // липка граната
        GAS,   // газова
        SMOKE, // димова
        GIGA,  // гіга граната
        SHAPED_CHARGE_GRENADE,   // кумулятивна граната
        MAGNETIC_GRENADE;        // магнітна граната

        public Item getItem()
        {
            return switch (this)
            {
                case FRAG_GRENADE -> CQCItems.FRAG_GRENADE.get();
                case HIGH_EXPLOSIVE_GRENADE -> CQCItems.HIGH_EXPLOSIVE_GRENADE.get();
                case IMPACT_GRENADE -> CQCItems.IMPACT_GRENADE.get();
                case SHAPED_CHARGE_GRENADE -> CQCItems.SHAPED_CHARGE_GRENADE.get();
                case MAGNETIC_GRENADE -> CQCItems.MAGNETIC_GRENADE.get();
                case STICKY_GRENADE -> CQCItems.STICKY_GRENADE.get();
                case GAS -> CQCItems.GAS_GRENADE.get();
                case SMOKE -> CQCItems.SMOKE_GRENADE.get();
                case GIGA -> CQCItems.GIGA_GRENADE.get();
            };
        }
    }
}   
