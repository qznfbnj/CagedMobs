package com.corgam.cagedmobs.addons.crafttweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.ingredient.IIngredient;
import com.blamejared.crafttweaker.api.item.IItemStack;
import com.corgam.cagedmobs.serializers.entity.EntityData;
import com.corgam.cagedmobs.serializers.entity.LootData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.openzen.zencode.java.ZenCodeType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

@ZenRegister
@ZenCodeType.Name("mods.cagedmobs.Entity")
public class CTEntity {

    private final EntityData data;

    public CTEntity(String entityId, int growTicks, boolean requiresWater, int tier, String[] environments){
        this(new EntityData(entityId, new ArrayList<>(Arrays.asList(environments)), growTicks, requiresWater, new ArrayList<>(),tier));
    }

    public CTEntity(EntityData entityData){
        this.data = entityData;
    }

    @ZenCodeType.Method
    public CTEntity addEnvironment(String environment){
        this.data.getEnvironments().add(environment);
        return this;
    }

    @ZenCodeType.Method
    public CTEntity removeEnvironment(String environment){
        HashSet<String> categories = new HashSet<>(this.data.getEnvironments());
        categories.remove(environment);
        this.data.setEnvironments(new ArrayList<>(categories));
        return this;
    }

    @ZenCodeType.Method
    public CTEntity clearEnvironments(){
        this.data.getEnvironments().clear();
        return this;
    }

    @ZenCodeType.Method
    public CTEntity addLoot(IItemStack item, float chance){
        return this.addLoot(item, chance, 1, 1);
    }

    @ZenCodeType.Method
    public CTEntity addLoot(IItemStack item, float chance, int min, int max){
        return this.addLoot(item, null, chance, min, max, false, false, -1, false, "", "");
    }

    @ZenCodeType.Method
    public CTEntity addLoot(IItemStack item, IItemStack cookedItem, float chance, int min, int max){
        return this.addLoot(item, cookedItem, chance, min, max, false, false, -1, false, "", "");
    }

    @ZenCodeType.Method
    public CTEntity addLoot(IItemStack item, float chance, int min, int max, boolean lightning, boolean arrow){
        return this.addLoot(item, null, chance, min, max, lightning, arrow, -1, false, "", "");
    }

    @ZenCodeType.Method
    public CTEntity addLoot(IItemStack item, IItemStack cookedItem, float chance, int min, int max, boolean lighting, boolean arrow, int color, boolean randomDurability, String nbtName, String nbtData){
        // To prevent adding the same item twice, look if it's already there
        for(LootData loot : this.data.getResults()){
            if(loot.getItem().getItems()[0].equals(item.getInternal(), false)){
                return this;
            }
        }
        // If there is a cooked variant
        if(cookedItem == null || cookedItem.getInternal().getItem().equals(Items.AIR)){
            this.data.getResults().add(new LootData(Ingredient.of(item.getInternal()), Ingredient.EMPTY, chance, min, max, lighting, arrow, color, randomDurability, nbtName, nbtData));
        }else{
            this.data.getResults().add(new LootData(Ingredient.of(item.getInternal()), Ingredient.of(cookedItem.getInternal()), chance, min, max, lighting, arrow, color, randomDurability, nbtName, nbtData));
        }
        return this;
    }

    @ZenCodeType.Method
    public CTEntity clearLoot(){
        this.data.getResults().clear();
        return this;
    }

    @ZenCodeType.Method
    public CTEntity removeLoot(IIngredient remove){
        final Ingredient ing = remove.asVanillaIngredient();
        HashSet<LootData> loots = new HashSet<>(this.data.getResults());
        loots.removeIf(drop -> ing.test(drop.getItem().getItems()[0]));
        this.data.setResults(new ArrayList<>(loots));
        return this;
    }

    @ZenCodeType.Method
    public CTEntity setGrowthTicks(int ticks) {
        this.data.setTotalGrowTicks(ticks);
        return this;
    }

    @ZenCodeType.Method
    public CTEntity setEntityType(String entityId) {
        this.data.setEntityId(entityId);
        return this;
    }

    @ZenCodeType.Method
    public CTEntity setTier(int tier) {
        this.data.setSamplerTier(tier);
        return this;
    }

    @ZenCodeType.Method
    public CTEntity setIfRequiresWater(boolean requiresWater) {
        this.data.setIfRequiresWater(requiresWater);
        return this;
    }

    public RecipeHolder<EntityData> getEntityData() {
        return new RecipeHolder<>(new ResourceLocation("entity_data_" + this.data.getEntityId()), this.data);
    }
}
