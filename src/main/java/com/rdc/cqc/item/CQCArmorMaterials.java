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

public class CQCArmorMaterials
{
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, CloseQuarterCombat.MODID);

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> GAS_MASK =
            ARMOR_MATERIALS.register("gas_mask", () -> new ArmorMaterial(
                    Util.make(new EnumMap<>(ArmorItem.Type.class), defense ->
                    {
                        defense.put(ArmorItem.Type.BOOTS, 0);
                        defense.put(ArmorItem.Type.LEGGINGS, 0);
                        defense.put(ArmorItem.Type.CHESTPLATE, 0);
                        defense.put(ArmorItem.Type.HELMET, 1);
                        defense.put(ArmorItem.Type.BODY, 0);
                    }),
                    8,
                    SoundEvents.ARMOR_EQUIP_LEATHER,
                    () -> Ingredient.of(Items.LEATHER, Items.IRON_INGOT),
                    List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(CloseQuarterCombat.MODID, "gas_mask"))),
                    0.0F,
                    0.0F
            ));

    public static Holder<ArmorMaterial> gasMask()
    {
        return GAS_MASK;
    }
}
