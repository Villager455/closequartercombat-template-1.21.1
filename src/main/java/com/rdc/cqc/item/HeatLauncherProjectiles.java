package com.rdc.cqc.item;

import com.rdc.cqc.CQCEvents;
import com.rdc.cqc.entity.ThrownGrenadeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class HeatLauncherProjectiles
{
    private static final double BACKBLAST_LENGTH = 2.0D;
    private static final double BACKBLAST_RADIUS = 0.75D;
    private static final double BLOCKED_BACKBLAST_CHECK_DISTANCE = 0.5D;
    private static final double[] BLOCKED_BACKBLAST_CHECK_HEIGHTS = {0.2D, 0.9D, 1.55D};
    private static final float BACKBLAST_DAMAGE = 5.0F;
    private static final float BLOCKED_BACKBLAST_VELOCITY_MULTIPLIER = 0.5F;
    private static final int BLOCKED_BACKBLAST_FIRE_SECONDS = 5;
    private static final float HEAT_PROJECTILE_VELOCITY = 3.0F;
    private static final float LARGE_HEAT_PROJECTILE_VELOCITY = 2.0F;
    private static final float HIGH_EXPLOSIVE_PROJECTILE_VELOCITY = 1.8F;
    private static final float INCENDIARY_PROJECTILE_VELOCITY = 2.2F;
    private static final float FRAG_PROJECTILE_VELOCITY = 2.2F;
    private static final float PROJECTILE_INACCURACY = 2.0F;

    private HeatLauncherProjectiles()
    {
    }

    public static void launch(ServerLevel level, Player player, ThrownGrenadeEntity.Type projectileType)
    {
        ThrownGrenadeEntity projectile = new ThrownGrenadeEntity(level, player, projectileType);
        projectile.setItem(projectileType.getItem().getDefaultInstance());
        projectile.setFuse(200);
        boolean blockedBackblast = isBackblastBlocked(level, player);
        projectile.setBadLaunch(blockedBackblast);
        float velocity = getVelocity(projectileType);
        if (blockedBackblast)
        {
            velocity *= BLOCKED_BACKBLAST_VELOCITY_MULTIPLIER;
            player.igniteForSeconds(BLOCKED_BACKBLAST_FIRE_SECONDS);
            if (player instanceof ServerPlayer serverPlayer)
            {
                CQCEvents.awardAdvancement(serverPlayer, "read_the_manual");
            }
        }
        projectile.shootFromRotation(
                player,
                player.getXRot(),
                player.getYRot(),
                0.0F,
                velocity,
                PROJECTILE_INACCURACY
        );
        level.addFreshEntity(projectile);
        spawnBackblast(level, player, blockedBackblast);
        level.playSound(
                null,
                player.getX(), player.getEyeY(), player.getZ(),
                SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.PLAYERS,
                1.0F,
                0.85F + level.getRandom().nextFloat() * 0.15F
        );
    }

    private static boolean isBackblastBlocked(ServerLevel level, Player player)
    {
        Vec3 backward = getHorizontalBackblastDirection(player);
        Vec3 feet = player.position();
        for (double height : BLOCKED_BACKBLAST_CHECK_HEIGHTS)
        {
            Vec3 blockedPoint = feet.add(0.0D, height, 0.0D).add(backward.scale(BLOCKED_BACKBLAST_CHECK_DISTANCE));
            BlockPos blockPos = BlockPos.containing(blockedPoint);
            BlockState blockState = level.getBlockState(blockPos);
            if (blockState.isCollisionShapeFullBlock(level, blockPos))
            {
                return true;
            }
        }

        return false;
    }

    private static void spawnBackblast(ServerLevel level, Player player, boolean blockedBackblast)
    {
        Vec3 backward = getHorizontalBackblastDirection(player);
        Vec3 origin = player.getEyePosition().add(0.0D, -0.25D, 0.0D);

        for (int i = 0; i < 9; i++)
        {
            double distance = 0.25D + (BACKBLAST_LENGTH - 0.25D) * i / 8.0D;
            Vec3 particlePos = origin.add(backward.scale(distance));
            int smokeCount = Math.max(1, (int) Math.ceil((9 - i) / 3.0D));
            level.sendParticles(
                    i % 3 == 0 ? ParticleTypes.FLAME : ParticleTypes.SMOKE,
                    particlePos.x, particlePos.y, particlePos.z,
                    3,
                    0.08D, 0.05D, 0.08D,
                    0.03D
            );
            level.sendParticles(
                    blockedBackblast ? ParticleTypes.CLOUD : ParticleTypes.SMOKE,
                    particlePos.x, particlePos.y, particlePos.z,
                    smokeCount,
                    0.06D, 0.04D, 0.06D,
                    blockedBackblast ? 0.08D : 0.18D
            );
        }

        AABB damageBox = new AABB(origin, origin.add(backward.scale(BACKBLAST_LENGTH))).inflate(BACKBLAST_RADIUS);
        double radiusSqr = BACKBLAST_RADIUS * BACKBLAST_RADIUS;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, damageBox, target -> target != player && target.isAlive()))
        {
            Vec3 targetCenter = target.getBoundingBox().getCenter();
            Vec3 toTarget = targetCenter.subtract(origin);
            double distanceBehind = toTarget.dot(backward);
            if (distanceBehind < 0.0D || distanceBehind > BACKBLAST_LENGTH)
            {
                continue;
            }

            Vec3 closestPoint = origin.add(backward.scale(distanceBehind));
            if (targetCenter.distanceToSqr(closestPoint) <= radiusSqr)
            {
                target.hurt(level.damageSources().playerAttack(player), BACKBLAST_DAMAGE);
            }
        }
    }

    private static Vec3 getHorizontalBackblastDirection(Player player)
    {
        Vec3 look = player.getLookAngle();
        Vec3 backward = new Vec3(-look.x, 0.0D, -look.z);
        if (backward.lengthSqr() < 1.0E-4D)
        {
            double yaw = Math.toRadians(player.getYRot());
            backward = new Vec3(Math.sin(yaw), 0.0D, -Math.cos(yaw));
        }

        return backward.normalize();
    }

    private static float getVelocity(ThrownGrenadeEntity.Type projectileType)
    {
        return switch (projectileType)
        {
            case LARGE_HEAT_PROJECTILE -> LARGE_HEAT_PROJECTILE_VELOCITY;
            case HIGH_EXPLOSIVE_PROJECTILE -> HIGH_EXPLOSIVE_PROJECTILE_VELOCITY;
            case INCENDIARY_PROJECTILE -> INCENDIARY_PROJECTILE_VELOCITY;
            case FRAG_PROJECTILE -> FRAG_PROJECTILE_VELOCITY;
            default -> HEAT_PROJECTILE_VELOCITY;
        };
    }
}
