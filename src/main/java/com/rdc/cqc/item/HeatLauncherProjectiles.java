package com.rdc.cqc.item;

import com.rdc.cqc.entity.ThrownGrenadeEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

public final class HeatLauncherProjectiles
{
    private static final float PROJECTILE_VELOCITY = 3.0F;
    private static final float PROJECTILE_INACCURACY = 2.0F;

    private HeatLauncherProjectiles()
    {
    }

    public static void launch(ServerLevel level, Player player, ThrownGrenadeEntity.Type projectileType)
    {
        ThrownGrenadeEntity projectile = new ThrownGrenadeEntity(level, player, projectileType);
        projectile.setItem(CQCItems.SHAPED_CHARGE_GRENADE.get().getDefaultInstance());
        projectile.setFuse(200);
        projectile.shootFromRotation(
                player,
                player.getXRot(),
                player.getYRot(),
                0.0F,
                PROJECTILE_VELOCITY,
                PROJECTILE_INACCURACY
        );
        level.addFreshEntity(projectile);
        level.playSound(
                null,
                player.getX(), player.getEyeY(), player.getZ(),
                SoundEvents.CROSSBOW_SHOOT,
                SoundSource.PLAYERS,
                1.0F,
                0.75F + level.getRandom().nextFloat() * 0.15F
        );
    }
}
