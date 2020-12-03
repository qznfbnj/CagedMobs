package com.corgam.cagedmobs.addons.crafttweaker;

import com.blamejared.crafttweaker.api.CraftTweakerAPI;
import com.blamejared.crafttweaker.api.annotations.ZenRegister;
import com.blamejared.crafttweaker.api.managers.IRecipeManager;
import com.blamejared.crafttweaker.impl.actions.recipes.ActionAddRecipe;
import com.blamejared.crafttweaker.impl.entity.MCEntityType;
import com.corgam.cagedmobs.serializers.RecipesHelper;
import com.corgam.cagedmobs.serializers.mob.AdditionalLootData;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.IRecipeType;
import net.minecraft.util.ResourceLocation;
import org.openzen.zencode.java.ZenCodeType;

@ZenRegister
@ZenCodeType.Name("mods.cagedmobs.AdditionalLoots")
public class AdditionalLoots implements IRecipeManager {

    public AdditionalLoots() {}

    // Used for creating new additionalLootRecipe
    @ZenCodeType.Method
    public CTAdditionalLoot create(String id, MCEntityType entityType) {
        final CTAdditionalLoot additionalLoot = new CTAdditionalLoot(id, entityType);
        CraftTweakerAPI.apply(new ActionAddRecipe(this, additionalLoot.getAdditionalLootData(), ""));
        return additionalLoot;
    }

    @ZenCodeType.Method
    public CTAdditionalLoot getAdditionalLoot(String id){
        final IRecipe<?> recipe = this.getRecipes().get(ResourceLocation.tryCreate(id));
        if (recipe instanceof AdditionalLootData) {
            return new CTAdditionalLoot((AdditionalLootData) recipe);
        }else{
            throw new IllegalStateException("CAGEDMOBS: Invalid CraftTweaker additionalLootRecipe ID: " + id);
        }
    }

    @Override
    public ResourceLocation getBracketResourceLocation () {
        return AdditionalLootData.SERIALIZER.getRegistryName();
    }

    @Override
    public IRecipeType<?> getRecipeType () {
        return RecipesHelper.ADDITIONAL_LOOT_RECIPE;
    }
}