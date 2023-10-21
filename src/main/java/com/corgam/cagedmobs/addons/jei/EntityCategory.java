package com.corgam.cagedmobs.addons.jei;

import com.corgam.cagedmobs.registers.CagedItems;
import com.corgam.cagedmobs.setup.Constants;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class EntityCategory implements IRecipeCategory<EntityWrapper> {

    public static final ResourceLocation ID = new ResourceLocation(Constants.MOD_ID, "entity");

    private static final int BG_PADDING = 5, BG_WIDTH = 166, BG_HEIGHT = 111;
    private final IDrawable icon;
    private final IDrawable background;
    private final RecipeType<EntityWrapper> type;
    private final IGuiHelper guiHelper;
    private final Component title;

    public EntityCategory(IGuiHelper gui, RecipeType<EntityWrapper> type){
        this.guiHelper = gui;
        this.icon = gui.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(CagedItems.MOB_CAGE.get()));
        ResourceLocation backgroundRL = new ResourceLocation(Constants.MOD_ID, "textures/gui/jei_recipe.png");
        this.background = gui.createDrawable(backgroundRL,0,0,BG_WIDTH+BG_PADDING*2,BG_HEIGHT+BG_PADDING*2);
        this.type = type;
        this.title = Component.translatable("jei.category.cagedmobs.entity");
    }

    @Override
    public RecipeType<EntityWrapper> getRecipeType() {
        return this.type;
    }

    @Override
    public Component getTitle() {
        return this.title;
    }

    @Override
    public IDrawable getBackground() {
        return this.background;
    }

    @Override
    public IDrawable getIcon() {
        return this.icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, EntityWrapper recipe, IFocusGroup focuses) {
        recipe.setRecipe(builder);
    }

    @Override
    public void draw(EntityWrapper recipe, IRecipeSlotsView view, GuiGraphics graphics, double mouseX, double mouseY) {
        recipe.draw(graphics, this.guiHelper);
    }
}