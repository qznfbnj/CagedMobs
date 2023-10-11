package com.corgam.cagedmobs.block_entities;

import com.corgam.cagedmobs.CagedMobs;
import com.corgam.cagedmobs.helpers.AdaptedItemHandler;
import com.corgam.cagedmobs.registers.CagedBlockEntities;
import com.corgam.cagedmobs.registers.CagedItems;
import com.corgam.cagedmobs.registers.CagedRecipeTypes;
import com.corgam.cagedmobs.serializers.RecipesHelper;
import com.corgam.cagedmobs.serializers.SerializationHelper;
import com.corgam.cagedmobs.serializers.env.EnvironmentData;
import com.corgam.cagedmobs.serializers.mob.AdditionalLootData;
import com.corgam.cagedmobs.serializers.mob.LootData;
import com.corgam.cagedmobs.serializers.mob.MobData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.EmptyHandler;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;

import static com.corgam.cagedmobs.blocks.MobCageBlock.HOPPING;

public class MobCageBlockEntity extends BlockEntity {

    // Entity and Environment data
    private EnvironmentData environmentData = null;
    private MobData entity = null;
    private EntityType<?> entityType = null;
    private Entity cachedEntity;
    private SpawnData renderedEntity;
    // Ticks
    private int currentGrowTicks = 0;
    private int totalGrowTicks = 0;
    private boolean waitingForHarvest = false;
    // Color of entity
    private int color = 0;
    // Saving and loading
    public static final String ITEMS_TAG = "Inventory";
    // Item capability
    public static int UPGRADES_COUNT = 3;
    public static int ENVIRONMENT_SLOT = 0;
    public static int SLOT_COUNT = UPGRADES_COUNT + 1;
    // Item handlers
    private final ItemStackHandler items = createItemHandler();
    private final LazyOptional<IItemHandler> itemHandler = LazyOptional.of(() -> items);
    private final LazyOptional<IItemHandler> restrictedItemHandler = LazyOptional.of(() -> new AdaptedItemHandler(items){
        /**
         * Does not allow item extraction (for example hoppers).
         * @param slot     Slot to extract from.
         * @param amount   Amount to extract
         * @param simulate If true, the extraction is only simulated
         * @return empty item stack
         */
        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        /**
         * Does not allow item insertion (for example hoppers).
         * @param slot     Slot to insert into.
         * @param stack    ItemStack to insert. This must not be modified by the item handler.
         * @param simulate If true, the insertion is only simulated
         * @return the same input stack
         */
        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return stack;
        }
    });

    /**
     * Creates a new cage block entity
     * @param pPos the position of the entity
     * @param pBlockState the state of the entity
     */
    public MobCageBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(CagedBlockEntities.MOB_CAGE_BLOCK_ENTITY.get(), pPos, pBlockState);
    }

    /**
     * Creates the item handler.
     * @return the item handler.
     */
    @Nonnull
    private ItemStackHandler createItemHandler(){
        return new ItemStackHandler(SLOT_COUNT){
            @Override
            protected void onContentsChanged(int slot){
                setChanged();
                if(level != null){
                    level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
                }
            }
        };
    }

    /**
     * Invalidates the item capability when block is destroyed.
     */
    @Override
    public void invalidateCaps(){
        super.invalidateCaps();
        itemHandler.invalidate();
        restrictedItemHandler.invalidate();
    }

    /**
     * Returns the item handler for environment and upgrades
     * @return items handler
     */
    public ItemStackHandler getInventoryHandler(){
        return this.items;
    }

    /**
     * Returns the item handler capabilities.
     * @param cap The capability to check
     * @param side The Side to check from,
     *   <strong>CAN BE NULL</strong>. Null is defined to represent 'internal' or 'self'
     * @return the item capability
     */
    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if(cap == ForgeCapabilities.ITEM_HANDLER){
            if (side == null){
                return itemHandler.cast();
            }else {
                // Return restricted item handler when any side is accessed.
                return restrictedItemHandler.cast();
            }
        }else{
            return super.getCapability(cap, side);
        }
    }

    /**
     * Main method executed in every tick. Runs only on the server instance.
     * @param level the world in which the block entity is located
     * @param pos the position of the block entity
     * @param state the state of the block entity
     * @param blockEntity the block entity class
     */
    public static void tick(Level level, BlockPos pos, BlockState state, MobCageBlockEntity blockEntity) {
        //Tick only when env and mob is inside
        if(blockEntity.hasEnvAndEntity() && !blockEntity.waitingForHarvest) {
            // Check if ready to harvest
            if(blockEntity.currentGrowTicks >= blockEntity.getTotalGrowTicks()) {
                blockEntity.attemptHarvest(state);
            }else {
                // Add one tick (if entity requires waterlogging check for it)
                if(!blockEntity.entity.ifRequiresWater() || blockEntity.getBlockState().getValue(BlockStateProperties.WATERLOGGED)){
                    blockEntity.currentGrowTicks++;
                }
            }
        }
        // Check if the cage has cooking upgrade and spawn particles
        if(blockEntity.hasUpgrades(CagedItems.COOKING_UPGRADE.get(), 1) && CagedMobs.CLIENT_CONFIG.shouldUpgradesParticles()){
            Random rand = new Random();
            if (!(level instanceof ServerLevel)) {
                if (rand.nextInt(10) == 0) {
                    Level world = blockEntity.getLevel();
                    BlockPos blockpos = blockEntity.getBlockPos();
                    double d3 = (double) blockpos.getX() + world.random.nextDouble();
                    double d4 = (double) blockpos.getY() + (world.random.nextDouble()/3);
                    double d5 = (double) blockpos.getZ() + world.random.nextDouble();
                    if(!blockEntity.getBlockState().getValue(BlockStateProperties.WATERLOGGED)){
                        // If not waterlogged emit fire particles
                        world.addParticle(ParticleTypes.SMOKE, d3, d4, d5, 0.0D, 0.0D, 0.0D);
                        world.addParticle(ParticleTypes.FLAME, d3, d4, d5, 0.0D, 0.0D, 0.0D);
                    }else{
                        // If waterlogged emit blue fire particles
                        world.addParticle(ParticleTypes.SMOKE, d3, d4, d5, 0.0D, 0.0D, 0.0D);
                        world.addParticle(ParticleTypes.SOUL_FIRE_FLAME, d3, d4, d5, 0.0D, 0.0D, 0.0D);
                    }

                }
            }
        }
        // Check if the cage has lighting upgrade and spawn particles
        if(blockEntity.hasUpgrades(CagedItems.LIGHTNING_UPGRADE.get(), 1) && CagedMobs.CLIENT_CONFIG.shouldUpgradesParticles()){
            Random rand = new Random();
            if (!(level instanceof ServerLevel)) {
                if (rand.nextInt(30) == 0) {
                    Level world = blockEntity.getLevel();
                    BlockPos blockpos = blockEntity.getBlockPos();
                    double d3 = (double) blockpos.getX() + 0.4 + (world.random.nextDouble()/5);
                    double d4 = (double) blockpos.getY() + 0.8;
                    double d5 = (double) blockpos.getZ() +  0.4 + (world.random.nextDouble()/5);
                    world.addParticle(ParticleTypes.END_ROD, d3, d4, d5, 0.0D, 0.0D, 0.0D);
                }
            }
        }
        // Check if the cage has arrows upgrade and spawn particles
        if(blockEntity.hasUpgrades(CagedItems.ARROW_UPGRADE.get(), 1) && CagedMobs.CLIENT_CONFIG.shouldUpgradesParticles()){
            Random rand = new Random();
            if (!(level instanceof ServerLevel)) {
                if (rand.nextInt(30) == 0) {
                    Level world = blockEntity.getLevel();
                    BlockPos blockpos = blockEntity.getBlockPos();
                    double d3 = (double) blockpos.getX() + 0.4 + (world.random.nextDouble()/5);
                    double d4 = (double) blockpos.getY() + 0.8;
                    double d5 = (double) blockpos.getZ() +  0.4 + (world.random.nextDouble()/5);
                    world.addParticle(ParticleTypes.CRIT, d3, d4, d5, 0.0D, -0.5D, 0.0D);
                }
            }
        }
    }

    /**
     * Drops the whole inventory on the ground.
     */
    public void dropInventory(){
        for (int i = 0; i < this.items.getSlots(); i++) {
            this.dropItem(this.items.getStackInSlot(i));
        }
    }

    /**
     * Creates a single item entity on the ground.
     * @param item item to drop
     */
    private void dropItem(ItemStack item) {
        if(this.level != null && !this.level.isClientSide) {
            final double offsetX = (double) (level.random.nextFloat() * 0.7F) + (double) 0.15F;
            final double offsetY = (double) (level.random.nextFloat() * 0.7F) + (double) 0.060000002F + 0.6D;
            final double offsetZ = (double) (level.random.nextFloat() * 0.7F) + (double) 0.15F;
            final ItemEntity itemEntity = new ItemEntity(this.level, this.worldPosition.getX() + offsetX, this.worldPosition.getY() + offsetY, this.worldPosition.getZ() + offsetZ, item);
            itemEntity.setDefaultPickUpDelay();
            this.level.addFreshEntity(itemEntity);
        }
    }

    // ENVIRONMENT FUNCTIONS

    /**
     * Sets the environment data from held item.
     * @param heldItem the item to set the environment from
     */
    public void setEnvironment(ItemStack heldItem) {
        this.environmentData = getEnvironmentDataFromItemStack(heldItem);
        // Set the env item
        ItemStack itemstack = heldItem.copy();
        itemstack.setCount(1);
        this.items.insertItem(ENVIRONMENT_SLOT, itemstack, false);
        // Check this block as a block to be saved upon exiting the chunk
        this.setChanged();
        // Sync with client
        if(this.level != null){
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * Returns the environment data from item stack
     * @param heldItem the item stack
     * @return the environment data
     */
    public static EnvironmentData getEnvironmentDataFromItemStack(ItemStack heldItem) {
        EnvironmentData finalEnvData = null;
        for(final Recipe<?> recipe : RecipesHelper.getRecipes(CagedRecipeTypes.ENV_RECIPE.get(), RecipesHelper.getRecipeManager()).values()) {
            if(recipe instanceof EnvironmentData) {
                final EnvironmentData envData = (EnvironmentData) recipe;
                if(envData.getInputItem().test(heldItem)) {
                    finalEnvData = envData;
                    break;
                }
            }
        }
        return finalEnvData;
    }

    /**
     * Removes the environment data.
     */
    public void removeEnvironment(){
        this.environmentData = null;
        // Check this block as a block to be saved upon exiting the chunk
        this.setChanged();
        // Sync with client
        if(this.level != null){
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * Returns the environment data.
     * @return the environment data
     */
    public EnvironmentData getEnvironment() {
        return this.environmentData;
    }

    /**
     * Checks if the block entity has environment data.
     * @return if block entity has environment data.
     */
    public boolean hasEnvironment() {
        return this.environmentData != null;
    }

    /**
     * Check if there exists an environment data from the given item.
     * @param heldItem item to check
     * @return if environment data exists
     */
    public static boolean existsEnvironmentFromItemStack(ItemStack heldItem) {
        // Check if the hand is empty
        if(heldItem.isEmpty()){
            return false;
        }
        // Check the recipes
        for(final Recipe<?> recipe : RecipesHelper.getRecipes(CagedRecipeTypes.ENV_RECIPE.get(), RecipesHelper.getRecipeManager()).values()) {
            if(recipe instanceof EnvironmentData) {
                final EnvironmentData envData = (EnvironmentData) recipe;
                if(envData.getInputItem().test(heldItem)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check the current environment is suitable for given entity type.
     * @param player player checking
     * @param entityType entity type to check
     * @param state block state
     * @return if environment is suitable
     */
    public boolean isEnvironmentSuitable(Player player, EntityType<?> entityType, BlockState state) {
        MobData recipe = getMobDataFromType(entityType);
        // Check if entity needs waterlogged cage
        if(recipe.ifRequiresWater() && !state.getValue(BlockStateProperties.WATERLOGGED)){
            player.displayClientMessage(Component.translatable("block.cagedmobs.mobcage.requiresWater").withStyle(ChatFormatting.RED), true);
            return false;
        }
        for(String env : this.environmentData.getEnvironments()){
            if(recipe.getValidEnvs().contains(env)){
                return true;
            }
        }
        player.displayClientMessage(Component.translatable("block.cagedmobs.mobcage.envNotSuitable").withStyle(ChatFormatting.RED), true);
        return false;
    }

    // ENTITY FUNCTIONS

    /**
     * Sets the environment data from sampler item.
     * @param entityType entity type
     * @param sampler sampler item
     */
    public void setEntityFromSampler(EntityType<?> entityType, ItemStack sampler) {
        // Lookup the entity color
        if(entityType.toString().contains("sheep")){
            if(sampler.hasTag() && sampler.getTag() != null && sampler.getTag().contains("Color") ){
                this.color = sampler.getTag().getInt("Color");
            }
        }
        // Load the mob data
        MobData mobData = getMobDataFromType(entityType);
        this.entity = mobData;
        this.entityType = entityType;
        // Calculate required ticks (take into account growthModifier from env)
        int basicTotalGrowTicks = Math.round(mobData.getTotalGrowTicks()/this.environmentData.getGrowModifier());
        this.totalGrowTicks = (int) Math.round(basicTotalGrowTicks/ CagedMobs.SERVER_CONFIG.getSpeedOfCages());
        // Sync with client
        if(this.level != null){
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
        }
        // Check this block as a block to be saved upon exiting the chunk
        this.setChanged();
    }

    /**
     * Returns the current entity data.
     * @return entity data
     */
    public MobData getEntity() {
        return this.entity;
    }

    /**
     * Returns the current entity type
     * @return entity type
     */
    public EntityType<?> getEntityType() {
        return this.entityType;
    }

    /**
     * Removes current entity.
     */
    public void removeEntity() {
        // Entity
        this.entity = null;
        this.entityType = null;
        // Entity render
        this.cachedEntity = null;
        this.renderedEntity = null;
        // Ticks
        this.currentGrowTicks = 0;
        this.totalGrowTicks = 0;
        this.waitingForHarvest = false;
        // Color
        this.color = 0;
        // Sync with client
        if(this.level != null){
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
        }
        // Check this block as a block to be saved upon exiting the chunk
        this.setChanged();
    }

    /**
     * Returns if there is entity.
     * @return if there exists entity
     */
    public boolean hasEntity() {
        return this.entity != null;
    }

    /**
     * Checks if there exists a mob data from entity type
     * @param entityType the entity type to check
     * @return if there exists mob data
     */
    public boolean existsEntityDataFromType(EntityType<?> entityType) {
        for(final Recipe<?> recipe : RecipesHelper.getRecipes(CagedRecipeTypes.MOB_RECIPE.get(), RecipesHelper.getRecipeManager()).values()) {
            if(recipe instanceof MobData) {
                final MobData mobData = (MobData) recipe;
                // Check for null exception
                if(mobData.getEntityType() == null){continue;}
                if(mobData.getEntityType().equals(entityType)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns the cached entity.
     * @param level the world of the entity
     * @return the entity
     */
    public Entity getCachedEntity(Level level) {
        if (this.cachedEntity == null) {
            if(this.renderedEntity == null){
                CompoundTag nbt = new CompoundTag();
                nbt.putString("id", EntityType.getKey(this.entityType).toString());
                this.renderedEntity = new SpawnData(nbt, Optional.empty());
            }
            this.cachedEntity = EntityType.loadEntityRecursive(this.renderedEntity.getEntityToSpawn(), level, Function.identity());
        }
        return this.cachedEntity;
    }

    /**
     * Used to get the MobData from entity type. Also adds all additional Loot to the entity.
     * @param type entity type
     * @return mob data
     */
    private static MobData getMobDataFromType(EntityType<?> type){
        MobData finalMobData = null;
        // Get the mobData
        for(final Recipe<?> recipe : RecipesHelper.getRecipes(CagedRecipeTypes.MOB_RECIPE.get(), RecipesHelper.getRecipeManager()).values()) {
            if(recipe instanceof MobData) {
                final MobData mobData = (MobData) recipe;
                // Check for null exception
                if(mobData.getEntityType() == null){continue;}
                if(mobData.getEntityType().equals(type)) {
                    finalMobData = mobData;
                    break;
                }
            }
        }
        // Add additional Loot
        if(finalMobData != null){
            for(final Recipe<?> recipe : RecipesHelper.getRecipes(CagedRecipeTypes.ADDITIONAL_LOOT_RECIPE.get(), RecipesHelper.getRecipeManager()).values()) {
                if(recipe instanceof AdditionalLootData) {
                    final AdditionalLootData additionalLootData = (AdditionalLootData) recipe;
                    // Check for null exception
                    if(finalMobData.getEntityType() == null){continue;}
                    if(finalMobData.getEntityType().equals(additionalLootData.getEntityType())) {
                        for(LootData data : additionalLootData.getResults()){
                            if(!finalMobData.getResults().contains(data)){
                                finalMobData.getResults().add(data);
                            }
                        }
                    }
                }
            }
        }
        return finalMobData;
    }

    // UPGRADES FUNCTIONS

    /**
     * Adds a new upgrade to the cage
     * @param heldItem upgrade to add.
     */
    public void addUpgrade(ItemStack heldItem) {
        for (int i = 0; i < this.items.getSlots(); i++) {
            // Check for empty slot
            if(this.items.extractItem(i,1,true).isEmpty()){
                ItemStack upgrade = heldItem.copy();
                upgrade.setCount(1);
                // Check if upgrade is valid
                if(this.items.isItemValid(i, upgrade)){
                    // Insert upgrade
                    this.items.insertItem(i, upgrade, false);
                }
            }
        }
    }

    /**
     * Returns if the cage can accept more upgrades.
     * @return if cage can accept more upgrades.
     */
    public boolean acceptsUpgrades() {
        return this.items.extractItem(ENVIRONMENT_SLOT+1, 1, true).isEmpty() ||
                this.items.extractItem(ENVIRONMENT_SLOT+2, 1, true).isEmpty() ||
                this.items.extractItem(ENVIRONMENT_SLOT+3, 1, true).isEmpty();
    }

    /**
     * Checks if the cage has a given amount or more of a specific upgrades.
     * @param upgradeItem the upgrade to check for
     * @param requiredCount the required minimal count
     * @return if the cage has a given amount or more of a specific upgrades
     */
    public boolean hasUpgrades(Item upgradeItem, int requiredCount){
        int currentCount = 0;
        for (int i = 0; i < this.items.getSlots(); i++) {
            if(this.items.extractItem(i,1,true).getItem().equals(upgradeItem)){
                currentCount++;
            }
        }
        return currentCount >= requiredCount;
    }

    // HARVESTING FUNCTIONS

    /**
     * Returns if the cage is waiting for harvest
     * @return if waiting for harvest
     */
    public boolean isWaitingForHarvest() {
        return this.waitingForHarvest;
    }

    /**
     * Called when player is harvesting the cage.
     */
    public void onPlayerHarvest(BlockState state) {
        if((!state.getValue(HOPPING)|| CagedMobs.SERVER_CONFIG.ifHoppingCagesDisabled()) && canPlayerHarvest()){
            this.currentGrowTicks = 0;
            this.waitingForHarvest = false;
            List<ItemStack> drops = createDropsList();
            for( ItemStack item : drops) {
                dropItem(item.copy());
            }
            this.setChanged();
            // Sync with client
            if(this.level != null){
                this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
            }
            // Check this block as a block to be saved upon exiting the chunk
            this.setChanged();
        }
    }

    /**
     * Attempt harvest.
     * For hopping cages and an inventory bellow harvest.
     * For not hopping cages, lock and wait for players interaction.
     * @param state the block state
     */
    private void attemptHarvest(BlockState state) {
        if(state.getValue(HOPPING) && !CagedMobs.SERVER_CONFIG.ifHoppingCagesDisabled()) {
            // Try to auto harvest
            if(this.autoHarvest()){
                this.currentGrowTicks = 0;
            }else{
                this.currentGrowTicks = this.getTotalGrowTicks();
            }
        }else {
            // Lock
            waitingForHarvest = true;
            this.currentGrowTicks = this.getTotalGrowTicks();
        }
        // Sync with client
        if(this.level != null){
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
        }
        // Check this block as a block to be saved upon exiting the chunk
        this.setChanged();
    }

    /**
     * Auto-harvests the cage, when there is a valid inventory bellow.
     * @return if the harvest happened
     */
    private boolean autoHarvest() {
        final IItemHandler inventory = getInv(this.level, this.worldPosition.below(), Direction.UP);
        if(inventory != EmptyHandler.INSTANCE && !this.level.isClientSide()){
            // For every item in drop list
            NonNullList<ItemStack> drops =this.createDropsList();
            for(final ItemStack item : drops){
                // For every slot in inv
                for(int slot = 0; slot < inventory.getSlots(); slot++){
                    // Simulate the insert
                    if(inventory.isItemValid(slot, item) && inventory.insertItem(slot,item,true).getCount() != item.getCount()){
                        // Actual insert
                        inventory.insertItem(slot, item, false);
                        break;
                    }
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Gets the ItemHandler from the block
     * @param world the world of the block
     * @param pos the position of the block
     * @param side the side of the block
     * @return the ItemHandler
     */
    private IItemHandler getInv(Level world, BlockPos pos, Direction side){
        final BlockEntity te = world.getBlockEntity(pos);
        // Capability system
        if(te != null){
            final LazyOptional<IItemHandler> invCap = te.getCapability(ForgeCapabilities.ITEM_HANDLER, side);
            return invCap.orElse(EmptyHandler.INSTANCE);
        }else{
            // When block doesn't use capability system
            final BlockState state = world.getBlockState(pos);
            if(state.getBlock() instanceof WorldlyContainerHolder){
                final WorldlyContainerHolder invProvider = (WorldlyContainerHolder) state.getBlock();
                final WorldlyContainer inv = invProvider.getContainer(state, world, pos);
                return new SidedInvWrapper(inv, side);
            }
        }
        return EmptyHandler.INSTANCE;
    }

    /**
     * Creates the drop list for the entity.
     * @return a list of items to drop.
     */
    private NonNullList<ItemStack> createDropsList(){
        NonNullList<ItemStack> drops = NonNullList.create();
        List<Item> blacklistedItems = RecipesHelper.getItemsFromConfigList();
        for(LootData loot : this.entity.getResults()) {
            // Skip item if it's blacklisted or whitelisted
            if(!CagedMobs.SERVER_CONFIG.isEntitiesListInWhitelistMode()){
                if(blacklistedItems.contains(loot.getItem().getItem())){
                    continue;
                }
            }else{
                if(!blacklistedItems.contains(loot.getItem().getItem())){
                    continue;
                }
            }
            // Choose a loot type for entity's color
            if(loot.getColor() != -1){
                if(loot.getColor() != this.color){
                    continue;
                }
            }
            // Skip if loot needs lightning upgrade, but it's not present in the cage.
            if(!this.hasUpgrades(CagedItems.LIGHTNING_UPGRADE.get(), 1) && loot.isLighting()){
                continue;
            }
            // Skip if loot needs arrow upgrade, but it's not present in the cage.
            if(!this.hasUpgrades(CagedItems.ARROW_UPGRADE.get(), 1) && loot.isArrow()){
                continue;
            }
            if(this.level != null && this.level.random.nextFloat() <= loot.getChance()) {
                // Roll the amount of items
                int range = loot.getMaxAmount() - loot.getMinAmount() + 1;
                int amount = this.level.random.nextInt(range) + loot.getMinAmount();
                if(amount > 0) {
                    // Add copied item stack to the drop list
                    ItemStack stack = loot.getItem().copy();
                    // Replace the item if there is a cooking upgrade
                    if(this.hasUpgrades(CagedItems.COOKING_UPGRADE.get(), 1) && loot.isCooking()){
                        stack = loot.getCookedItem().copy();
                    }
                    stack.setCount(amount);
                    drops.add(stack);
                }
            }
        }
        return drops;
    }

    /**
     * Check if the cage is ready to be harvested.
     * @return if the cage is ready to be harvested
     */
    private boolean canPlayerHarvest() {
        return this.hasEnvAndEntity() && this.getTotalGrowTicks() > 0 && this.getCurrentGrowTicks() >= this.getTotalGrowTicks();
    }

    /**
     * Checks if the cage has both the entity and the environment.
     * @return if the cage has both the entity and the environment
     */
    private boolean hasEnvAndEntity() {
        return this.hasEntity() && this.hasEnvironment();
    }

    /**
     * Returns the current growth percentage.
     * @return growth percentage
     */
    public float getGrowthPercentage() {
        if(this.totalGrowTicks != 0) {
            return (float) this.getCurrentGrowTicks() / this.getTotalGrowTicks();
        }else{
            return 0;
        }
    }

    /**
     * Returns the total grow ticks
     * @return total grow ticks
     */
    private int getTotalGrowTicks() {
        int basicTotalGrowTicks = Math.round(this.getEntity().getTotalGrowTicks()/this.environmentData.getGrowModifier());
        return this.totalGrowTicks = (int) Math.round(basicTotalGrowTicks/CagedMobs.SERVER_CONFIG.getSpeedOfCages());
    }

    /**
     * Returns the current grow ticks
     * @return current grow ticks
     */
    private int getCurrentGrowTicks() {
        return currentGrowTicks;
    }


    // COLOR FUNCTIONS

    /**
     * Returns the color nbt of the entity
     * @return color id
     */
    public int getColor() {
        return this.color;
    }

    // SAVING AND LOADING

    /**
     * Saves all the additional tags of the block entity.
     * @param tag the nbt tag to save to
     */
    @Override
    protected void saveAdditional(@NotNull CompoundTag tag){
        super.saveAdditional(tag);
        saveClientData(tag);
    }

    /**
     * Saves custom parameters of the entity  in correct order.
     * Used by the update tag to keep the network overhead small.
     * @param tag the nbt tag to save to
     */
    private void saveClientData(CompoundTag tag){
        // Item capability
        tag.put(ITEMS_TAG, items.serializeNBT());
        if(this.hasEnvironment()) {
            // If cage has entity, put entity info
            if(this.hasEntity()){
                // Put entity type
                SerializationHelper.serializeEntityTypeNBT(tag, this.entityType);
                // Put color
                tag.putInt("color",this.color);
                // Put ticks info
                tag.putInt("currentGrowTicks", this.currentGrowTicks);
                tag.putBoolean("waitingForHarvest", this.waitingForHarvest);
            }
        }
    }

    /**
     * Loads all the additional tags of the block entity.
     * @param tag the nbt tag to load from
     */
    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        loadClientData(tag);
    }

    /**
     * Loads custom parameters of the entity in correct order.
     * Used by the update tag to keep the network overhead small.
     * @param tag the nbt tag to load from
     */
    private void loadClientData(CompoundTag tag) {
        // Store old env and entity type
        ItemStack oldEnv = this.items.getStackInSlot(ENVIRONMENT_SLOT);
        EntityType<?> oldEntityType = this.entityType;
        // Item capability
        if (tag.contains(ITEMS_TAG)) {
            items.deserializeNBT(tag.getCompound(ITEMS_TAG));
        }
        // Read the env
        this.environmentData = MobCageBlockEntity.getEnvironmentDataFromItemStack(this.items.getStackInSlot(ENVIRONMENT_SLOT));
        // Read the mob data
        this.entityType = SerializationHelper.deserializeEntityTypeNBT(tag);
        this.entity = MobCageBlockEntity.getMobDataFromType(this.entityType);
        if(this.entityType == null){
            this.renderedEntity = null;
            this.cachedEntity = null;
        }
        // Read color
        this.color = tag.getInt("color");
        // Read ticks info
        this.waitingForHarvest = tag.getBoolean("waitingForHarvest");
        this.currentGrowTicks = tag.getInt("currentGrowTicks");
        if(hasEntity()){
            this.totalGrowTicks = Math.round( this.entity.getTotalGrowTicks()/this.environmentData.getGrowModifier());
        }
        // If env or entity changed, refresh model data
        if(!Objects.equals(oldEnv, this.items.getStackInSlot(ENVIRONMENT_SLOT)) || !Objects.equals(oldEntityType,this.entityType)){
            requestModelDataUpdate();
            // Sync with client
            if(this.level != null){
                this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    // CLIENT-SERVER SYNCHRONIZATION

    /**
     * Creates the update tag on server side, when a new chunk is loaded.
     * @return the update nbt tag to send
     */
    @Override
    public @NotNull CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveClientData(tag);
        return tag;
    }

    /**
     * Reads the update tag on client side, when a new chunk is loaded.
     * @param tag The {@link CompoundTag} sent from {@link BlockEntity#getUpdateTag()}
     */
    @Override
    public void handleUpdateTag(CompoundTag tag) {
        if (tag != null) {
            loadClientData(tag);
        }
    }

    /**
     * Creates an update packet when the block state has changed.
     * @return the update packet
     */
    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        // Uses getUpdateTag() under the hood
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * Loads the received data packet on client side, when the block state has changed.
     * @param net The NetworkManager the packet originated from
     * @param pkt The data packet
     */
    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag();
        // This will call loadClientData()
        if (tag != null) {
            handleUpdateTag(tag);
        }
    }
}
