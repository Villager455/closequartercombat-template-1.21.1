package com.rdc.cqc.item;

import com.rdc.cqc.CloseQuarterCombat;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Матеріали броні для мода Close Quarter Combat.
 *
 * <p>Зараз тут реєструються лише матеріали для плащів TrenchCoat S/X.
 * Характеристики обрані наближено до ванільної залізної броні:
 * <ul>
 *     <li>Захист: chest = 6, legs = 5 (helmet/boots не використовуються,
 *         але слоти все одно прописані з нульовим захистом для повноти).</li>
 *     <li>Enchantment value = 9 (як у заліза).</li>
 *     <li>Toughness = 0, Knockback Resistance = 0.</li>
 *     <li>Звук одягання — як у залізної броні.</li>
 *     <li>Repair ingredient — залізний злиток.</li>
 * </ul>
 */
public class CQCArmorMaterials
{
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, CloseQuarterCombat.MODID);

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> TRENCHCOAT_S =
            ARMOR_MATERIALS.register("trenchcoat_s", () -> ironLikeMaterial("trenchcoat_s"));

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> TRENCHCOAT_X =
            ARMOR_MATERIALS.register("trenchcoat_x", () -> ironLikeMaterial("trenchcoat_x"));

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> LOBSTER_ARMOR =
            ARMOR_MATERIALS.register("lobster_armor", () -> ironLikeMaterial("lobster_armor"));

    /**
     * Створює iron-like {@link ArmorMaterial} з указаним ім'ям папки для armor-layer текстур.
     * Текстури очікуються за шляхами:
     * <pre>
     *   assets/closequartercombat/textures/models/armor/{name}_layer_1.png
     *   assets/closequartercombat/textures/models/armor/{name}_layer_2.png
     * </pre>
     */
    private static ArmorMaterial ironLikeMaterial(String name)
    {
        return new ArmorMaterial(
                Util.make(new EnumMap<>(ArmorItem.Type.class), defense ->
                {
                    defense.put(ArmorItem.Type.BOOTS, 0);
                    defense.put(ArmorItem.Type.LEGGINGS, 5);
                    defense.put(ArmorItem.Type.CHESTPLATE, 6);
                    defense.put(ArmorItem.Type.HELMET, 0);
                    defense.put(ArmorItem.Type.BODY, 0);
                }),
                9, // enchantment value (iron)
                SoundEvents.ARMOR_EQUIP_IRON,
                () -> Ingredient.of(Items.IRON_INGOT),
                List.of(new ArmorMaterial.Layer(
                        ResourceLocation.fromNamespaceAndPath(CloseQuarterCombat.MODID, name)
                )),
                0.0F, // toughness
                0.0F  // knockback resistance
        );
    }

    public static Holder<ArmorMaterial> trenchcoatS()
    {
        return TRENCHCOAT_S;
    }

    public static Holder<ArmorMaterial> trenchcoatX()
    {
        return TRENCHCOAT_X;
    }

    public static Holder<ArmorMaterial> lobsterArmor()
    {
        return LOBSTER_ARMOR;
    }
}
