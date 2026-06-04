package com.rdc.cqc.mine;

import com.rdc.cqc.entity.ThrownGrenadeEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public class CQCMines
{
    private static final Map<MineKey, PlantedMine> PLANTED_MINES = new HashMap<>();

    private CQCMines()
    {
    }

    public static InteractionResult tryPlantMine(UseOnContext context, Type mineType)
    {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();

        if (!canPlantOn(level, pos))
        {
            return InteractionResult.PASS;
        }

        if (level.isClientSide())
        {
            return InteractionResult.SUCCESS;
        }

        MineKey key = MineKey.of((ServerLevel) level, pos);
        if (PLANTED_MINES.containsKey(key))
        {
            return InteractionResult.FAIL;
        }

        UUID ownerId = player == null ? null : player.getUUID();
        PLANTED_MINES.put(key, new PlantedMine(mineType, ownerId));

        ItemStack stack = context.getItemInHand();
        if (player == null || !player.getAbilities().instabuild)
        {
            stack.shrink(1);
        }

        level.playSound(
                null,
                pos.getX() + 0.5D,
                pos.getY() + 1.0D,
                pos.getZ() + 0.5D,
                SoundEvents.GRAVEL_PLACE,
                SoundSource.BLOCKS,
                0.35F,
                0.85F + level.getRandom().nextFloat() * 0.2F
        );
        return InteractionResult.SUCCESS;
    }

    public static void onEntityTick(EntityTickEvent.Post event)
    {
        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity living) || entity.level().isClientSide() || !living.isAlive())
        {
            return;
        }

        BlockPos steppedPos = entity.getOnPos();
        ServerLevel level = (ServerLevel) entity.level();
        MineKey key = MineKey.of(level, steppedPos);
        PlantedMine mine = PLANTED_MINES.remove(key);
        if (mine == null)
        {
            return;
        }
        if (!canPlantOn(level, steppedPos))
        {
            return;
        }

        mine.type().detonate(level, steppedPos, mine.owner(level));
    }

    private static boolean canPlantOn(Level level, BlockPos pos)
    {
        Block block = level.getBlockState(pos).getBlock();
        return block == Blocks.DIRT
                || block == Blocks.GRASS_BLOCK
                || block == Blocks.DIRT_PATH
                || block == Blocks.COARSE_DIRT
                || block == Blocks.MYCELIUM
                || block == Blocks.SAND
                || block == Blocks.RED_SAND
                || block == Blocks.PODZOL;
    }

    public enum Type
    {
        FRAG_PRESSURE
                {
                    @Override
                    void detonate(ServerLevel level, BlockPos pos, LivingEntity owner)
                    {
                        Vec3 origin = Vec3.atCenterOf(pos).add(0.0D, 1.0D, 0.0D);
                        int kills = ThrownGrenadeEntity.damageAndSpawnShrapnel(
                                level,
                                origin.x,
                                origin.y,
                                origin.z,
                                ThrownGrenadeEntity.FRAG_GRENADE_EXPLOSION_RADIUS,
                                ThrownGrenadeEntity.FRAG_GRENADE_SHRAPNEL_DAMAGE,
                                owner
                        );
                        if (kills >= 5 && owner instanceof ServerPlayer serverPlayer)
                        {
                            com.rdc.cqc.CQCEvents.awardAdvancement(serverPlayer, "fire_in_the_hole");
                        }
                        ThrownGrenadeEntity.spawnShrapnelSmokeBurst(
                                level,
                                origin.x,
                                origin.y + 0.25D,
                                origin.z,
                                ThrownGrenadeEntity.FRAG_GRENADE_EXPLOSION_RADIUS,
                                level.getRandom()
                        );
                        level.levelEvent(2009, BlockPos.containing(origin), 0);
                        level.playSound(
                                null,
                                origin.x,
                                origin.y,
                                origin.z,
                                SoundEvents.GENERIC_EXPLODE.value(),
                                SoundSource.NEUTRAL,
                                1.6F,
                                1.0F
                        );
                    }
                },

        HIGH_EXPLOSIVE_PRESSURE
                {
                    @Override
                    void detonate(ServerLevel level, BlockPos pos, LivingEntity owner)
                    {
                        Vec3 origin = Vec3.atCenterOf(pos).add(0.0D, 1.0D, 0.0D);
                        level.explode(
                                owner,
                                origin.x,
                                origin.y,
                                origin.z,
                                ThrownGrenadeEntity.HIGH_EXPLOSIVE_GRENADE_EXPLOSION_RADIUS,
                                Level.ExplosionInteraction.TNT
                        );
                    }
                };

        abstract void detonate(ServerLevel level, BlockPos pos, LivingEntity owner);
    }

    private record PlantedMine(Type type, UUID ownerId)
    {
        LivingEntity owner(ServerLevel level)
        {
            if (ownerId == null)
            {
                return null;
            }
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(ownerId);
            return player;
        }
    }

    private record MineKey(ResourceLocation dimension, BlockPos pos)
    {
        static MineKey of(ServerLevel level, BlockPos pos)
        {
            return new MineKey(level.dimension().location(), pos.immutable());
        }
    }
}
