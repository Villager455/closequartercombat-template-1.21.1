package com.rdc.cqc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rdc.cqc.entity.ThrownGrenadeEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import com.mojang.math.Axis;

/**
 * Кастомний рендер для {@link ThrownGrenadeEntity}.
 *
 * <p>На відміну від ванільного {@link net.minecraft.client.renderer.entity.ThrownItemRenderer},
 * цей рендер обертає item-модель довкола її локальної осі — щоб граната «крутилася»
 * під час польоту.</p>
 *
 * <p>Швидкість обертання залежить від модуля швидкості (швидше летить — швидше крутиться)
 * з мінімальною базовою кутовою швидкістю, щоб граната ще трішки крутилась навіть
 * після відскоку.</p>
 */
public class GrenadeRenderer extends EntityRenderer<ThrownGrenadeEntity>
{
    /** Базова швидкість обертання у градусах за тік. */
    private static final float BASE_SPIN_DEG_PER_TICK = 8.0F;
    /** Додатковий внесок швидкості (град/тік на одиницю швидкості). */
    private static final float SPIN_FROM_VELOCITY = 50.0F;
    /** Giga важча, тому у польоті обертається повільніше. */
    private static final float GIGA_SPIN_MULTIPLIER = 0.6F;
    /** Масштаб, з яким малюємо item-модель. */
    private static final float MODEL_SCALE = 0.85F;

    private final ItemRenderer itemRenderer;

    public GrenadeRenderer(EntityRendererProvider.Context context)
    {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(ThrownGrenadeEntity entity,
                       float entityYaw,
                       float partialTick,
                       PoseStack poseStack,
                       MultiBufferSource buffer,
                       int packedLight)
    {
        if (entity.isSmokeEmitting()
                || entity.isGasEmitting()
                || entity.getGrenadeType() == ThrownGrenadeEntity.Type.INCENDIARY_FRAGMENT
                || entity.getGrenadeType() == ThrownGrenadeEntity.Type.CLUSTER_SUBMUNITION)
        {
            return;
        }

        poseStack.pushPose();

        // Збираємо item-модель, що відповідає типу гранати.
        ItemStack stack = new ItemStack(entity.getGrenadeType().getItem());

        // Масштаб (як у ванільного ThrownItemRenderer).
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);

        // Логіка обертання:
        //  • Якщо граната лежить (resting) — миттєвий поворот на 90° по X для ефекту
        //    «граната на боку». Це стосується ВСІХ типів.
        //  • Якщо в польоті — обертаємо High Explosive, Sticky та Giga Grenade.
        ThrownGrenadeEntity.Type grenadeType = entity.getGrenadeType();
        boolean shouldSpin = grenadeType == ThrownGrenadeEntity.Type.HIGH_EXPLOSIVE_GRENADE
                || grenadeType == ThrownGrenadeEntity.Type.IMPROVISED_GRENADE
                || grenadeType == ThrownGrenadeEntity.Type.STICKY_GRENADE
                || grenadeType == ThrownGrenadeEntity.Type.SHAPED_CHARGE_GRENADE
                || grenadeType == ThrownGrenadeEntity.Type.GIGA
                || grenadeType == ThrownGrenadeEntity.Type.GIGA_GIGA;

        if (entity.isResting())
        {
            if (grenadeType == ThrownGrenadeEntity.Type.MAGNETIC_GRENADE && entity.getMagneticAttachedFace() != null)
            {
                alignMagneticGrenadeToSurface(poseStack, entity.getMagneticAttachedFace(), entity);
            }
            else
            {
                // Лежить у стабільній world-space позі, а не дивиться на камеру.
                poseStack.mulPose(Axis.YP.rotationDegrees(stableRestingYaw(entity)));
                poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            }
        }
        else
        {
            Vec3 velocity = entity.getDeltaMovement();
            float speed = (float) velocity.length();

            if (shouldSpin && speed > 0.01F)
            {
                float yaw = (float) Math.toDegrees(Math.atan2(velocity.x, velocity.z));
                poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
                poseStack.mulPose(Axis.XP.rotationDegrees(25.0F));
            }
            else
            {
                poseStack.mulPose(Axis.YP.rotationDegrees(stableRestingYaw(entity)));
                poseStack.mulPose(Axis.XP.rotationDegrees(25.0F));
            }

            if (shouldSpin)
            {
                float spinMultiplier = (grenadeType == ThrownGrenadeEntity.Type.GIGA || grenadeType == ThrownGrenadeEntity.Type.GIGA_GIGA)
                        ? GIGA_SPIN_MULTIPLIER
                        : 1.0F;
                float spinPerTick = (BASE_SPIN_DEG_PER_TICK + speed * SPIN_FROM_VELOCITY) * spinMultiplier;
                float spinAngle = (entity.tickCount + partialTick) * spinPerTick;

                // Обертаємо навколо локальної осі X, вже без camera-facing billboard.
                poseStack.mulPose(Axis.XP.rotationDegrees(spinAngle));
            }
        }

        // Рендеримо item-модель.
        this.itemRenderer.renderStatic(
                stack,
                ItemDisplayContext.GROUND,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                buffer,
                entity.level(),
                entity.getId()
        );

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private static float stableRestingYaw(ThrownGrenadeEntity entity)
    {
        return (entity.getId() * 47) % 360;
    }

    private static void alignMagneticGrenadeToSurface(PoseStack poseStack, Direction face, ThrownGrenadeEntity entity)
    {
        float roll = stableRestingYaw(entity);
        switch (face)
        {
            case UP ->
            {
                poseStack.mulPose(Axis.YP.rotationDegrees(roll));
                poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            }
            case DOWN ->
            {
                poseStack.mulPose(Axis.YP.rotationDegrees(roll));
                poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            }
            case NORTH -> poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            case SOUTH -> poseStack.mulPose(Axis.YP.rotationDegrees(0.0F));
            case EAST -> poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            case WEST -> poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
        }
    }

    @Override
    public ResourceLocation getTextureLocation(ThrownGrenadeEntity entity)
    {
        // Реальна текстура витягується через ItemRenderer, цей метод EntityRenderer
        // лише вимагає валідного ResourceLocation. Використовуємо ванільний block-атлас.
        return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;
    }
}
