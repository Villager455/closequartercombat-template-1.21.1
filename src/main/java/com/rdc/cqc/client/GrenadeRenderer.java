package com.rdc.cqc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rdc.cqc.entity.ThrownGrenadeEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
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
        poseStack.pushPose();

        // Збираємо item-модель, що відповідає типу гранати.
        ItemStack stack = new ItemStack(entity.getGrenadeType().getItem());

        // Масштаб (як у ванільного ThrownItemRenderer).
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);

        // Орієнтуємо модель «обличчям» до камери — щоб гранату було видно з будь-якого ракурсу.
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());

        // Логіка обертання:
        //  • Якщо граната лежить (resting) — миттєвий поворот на 90° по X для ефекту
        //    «граната на боку». Це стосується ВСІХ типів.
        //  • Якщо в польоті — обертаємо ЛИШЕ Demolition Grenade (з ручкою), бо інші
        //    типи мають симетричну круглу форму, для якої кручення непомітне/непотрібне.
        boolean isDemo = entity.getGrenadeType() == ThrownGrenadeEntity.Type.DEMO;

        if (entity.isResting())
        {
            // Лежить — повернути на 90 градусів по X, та й все.
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        }
        else if (isDemo)
        {
            Vec3 velocity = entity.getDeltaMovement();
            float speed = (float) velocity.length();
            float spinPerTick = BASE_SPIN_DEG_PER_TICK + speed * SPIN_FROM_VELOCITY;
            float spinAngle = (entity.tickCount + partialTick) * spinPerTick;

            // Обертаємо ЛИШЕ навколо осі X у view-space (горизонтальна вісь камери).
            // Створює ефект «гранати, що перевертається через ноги».
            poseStack.mulPose(Axis.XP.rotationDegrees(spinAngle));
        }
        // Інакше (HE/GAS у польоті) — нічого не множимо, граната летить в статичній позі.

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

    @Override
    public ResourceLocation getTextureLocation(ThrownGrenadeEntity entity)
    {
        // Реальна текстура витягується через ItemRenderer, цей метод EntityRenderer
        // лише вимагає валідного ResourceLocation. Використовуємо ванільний block-атлас.
        return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;
    }
}
