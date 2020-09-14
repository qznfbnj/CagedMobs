package com.corgam.cagedmobs.serializers.env;

import com.corgam.cagedmobs.serializers.RecipesHelper;
import com.corgam.cagedmobs.serializers.SerializationHelper;
import com.corgam.cagedmobs.serializers.mob.MobData;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.crafting.IRecipeSerializer;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.JSONUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistryEntry;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

public class EnvironmentDataSerializer extends ForgeRegistryEntry<IRecipeSerializer<?>> implements IRecipeSerializer<EnvironmentData>{

    EnvironmentDataSerializer(){
        this.setRegistryName(new ResourceLocation("cagedmobs","env_data"));
    }

    // Used to serialize all EnvData recipes from JSON files
    @Override
    public EnvironmentData read(ResourceLocation recipeId, JsonObject json) {
        // Input item
        final Ingredient inputItem = Ingredient.deserialize(json.getAsJsonObject("input"));
        // Block to render
        final BlockState renderState = SerializationHelper.deserializeBlockState(json.getAsJsonPrimitive("render"));
        // Grow modifier
        final float growModifier = JSONUtils.getFloat(json, "growModifier");
        // Categories
        final Set<String> categories = new HashSet<>();
        for(final JsonElement e : json.getAsJsonArray("categories")){
            categories.add(e.getAsString().toLowerCase());
        }

        // Error checks
        if (growModifier <= -1) {
            throw new IllegalArgumentException("Environment " + recipeId + " has an invalid grow modifier. It must be greater than -1.");
        }

        return new EnvironmentData(recipeId, inputItem, renderState, growModifier, categories);
    }

    @Override
    public EnvironmentData read(ResourceLocation recipeId, PacketBuffer buffer) {
        // Input item
        final Ingredient inputItem = Ingredient.read(buffer);
        // Block to render
        final BlockState renderState = SerializationHelper.deserializeBlockState(buffer);
        // Grow modifier
        final float growModifier = buffer.readFloat();
        // Categories
        final Set<String> categories = new HashSet<>();
        SerializationHelper.deserializeStringCollection(buffer, categories);

        return new EnvironmentData(recipeId, inputItem, renderState, growModifier, categories);
    }

    @Override
    public void write(PacketBuffer buffer, EnvironmentData recipe) {
        // Input item
        recipe.getInputItem().write(buffer);
        // Block to render
        SerializationHelper.serializeBlockState(buffer, recipe.getRenderState());
        // Grow modifier
        buffer.writeFloat(recipe.getGrowModifier());
        // Categories
        SerializationHelper.serializeStringCollection(buffer, recipe.getCategories());
    }
}
