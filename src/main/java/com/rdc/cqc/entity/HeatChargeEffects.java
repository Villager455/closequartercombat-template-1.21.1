package com.rdc.cqc.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class HeatChargeEffects
{
    private static final double JET_LENGTH = 3.0D;
    private static final double EXPLOSION_DISTANCE = 4.0D;
    public static final float LARGE_HEAT_PROJECTILE_EXPLOSION_RADIUS = ThrownGrenadeEntity.HIGH_EXPLOSIVE_GRENADE_EXPLOSION_RADIUS;

    private HeatChargeEffects()
    {
    }

    public static void detonateDoubleBlast(Level level, Entity explosionSource, Vec3 origin, Vec3 direction, float explosionRadius)
    {
        Vec3 normalizedDirection = normalizeOrForward(direction);
        spawnJetParticles(level, origin, normalizedDirection);

        Vec3 explosionPosition = origin.add(normalizedDirection.scale(EXPLOSION_DISTANCE));
        level.explode(
                explosionSource,
                origin.x, origin.y, origin.z,
                explosionRadius,
                Level.ExplosionInteraction.TNT
        );
        level.explode(
                explosionSource,
                explosionPosition.x, explosionPosition.y, explosionPosition.z,
                explosionRadius,
                Level.ExplosionInteraction.TNT
        );
    }

    public static void spawnJetParticles(Level level, Vec3 origin, Vec3 direction)
    {
        if (!(level instanceof ServerLevel serverLevel))
        {
            return;
        }

        Vec3 normalizedDirection = normalizeOrForward(direction);
        int points = 18;
        for (int i = 0; i <= points; i++)
        {
            double progress = i / (double) points;
            Vec3 position = origin.add(normalizedDirection.scale(JET_LENGTH * progress));
            Vec3 velocity = normalizedDirection.scale(0.02D + progress * 0.04D);

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

    private static Vec3 normalizeOrForward(Vec3 direction)
    {
        return direction.lengthSqr() > 1.0E-4D
                ? direction.normalize()
                : new Vec3(0.0D, 0.0D, 1.0D);
    }
}
