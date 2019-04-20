package com.noobanidus.unenchanting;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemBook;
import net.minecraft.item.ItemEnchantedBook;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.NonNullList;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.entity.player.AnvilRepairEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.oredict.OreIngredient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = Unenchanting.MODID)
@SuppressWarnings("unused")
public class AnvilEventHandler {
    private static Map<ItemStack, NBTTagList> cache = new HashMap<>();
    private static Map<ItemStack, ItemStack> outputCache = new HashMap<>();
    private static Object2IntOpenHashMap<ItemStack> costCache = new Object2IntOpenHashMap<>();
    private static Object2IntOpenHashMap<ItemStack> indexCache = new Object2IntOpenHashMap<>();
    private static OreIngredient book = null;

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        if (book == null) {
            book = new OreIngredient("book");
        }

        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (!book.apply(right) && !(right.getItem() instanceof ItemBook)) {
            return;
        }

        ItemStack bookOutput;
        int cost;

        if (outputCache.containsKey(left) && costCache.containsKey(left)) {
            bookOutput = outputCache.get(left);
            cost = costCache.getInt(left);
        } else {
            NBTTagList enchantments;

            if (cache.containsKey(left)) {
                enchantments = cache.get(left);
            } else {

                if (!left.hasTagCompound()) {
                    return;
                }

                NBTTagCompound tag = left.getTagCompound();

                if (tag == null) {
                    return;
                }

                boolean book = false;

                if (left.getItem() == Items.ENCHANTED_BOOK) {
                    book = true;
                }

                if (!tag.hasKey(book ? "StoredEnchantments" : "ench")) return;

                enchantments = tag.getTagList(book ? "StoredEnchantments" : "ench", 10);
                cache.put(left, enchantments);
            }

            if (enchantments.tagCount() == 0) return;

            NBTTagCompound eDef = null;
            Enchantment definition = null;

            int usedIndex = -1;

            for (int i = 0; i < enchantments.tagCount(); i++) {
                eDef = enchantments.getCompoundTagAt(i);
                definition = Enchantment.getEnchantmentByID(eDef.getShort("id"));
                if (definition == null || !definition.isAllowedOnBooks()) continue;

                usedIndex = i;
                break;
            }

            if (usedIndex == -1) {
                return;
            }

            Map<Enchantment, Integer> bookEnchants = new HashMap<>();
            bookOutput = new ItemStack(Items.ENCHANTED_BOOK, 1, 0);
            bookEnchants.put(definition, (int) eDef.getShort("lvl"));
            EnchantmentHelper.setEnchantments(bookEnchants, bookOutput);
            outputCache.put(left, bookOutput);

            cost = UnenchantingConfig.BASE_LEVEL_COST * enchantments.tagCount();
            costCache.put(left, cost);

            indexCache.put(left, usedIndex);
        }

        event.setCanceled(false);
        event.setCost(cost);
        event.setOutput(bookOutput);
        event.setMaterialCost(1);
    }

    @SubscribeEvent
    public static void onAnvilRepair(AnvilRepairEvent event) {
        ItemStack input = event.getItemInput();
        ItemStack books = event.getIngredientInput();
        ItemStack output = event.getItemResult();

        if ((book.apply(books) || books.getItem() instanceof ItemBook) && output.getItem() == Items.ENCHANTED_BOOK) {
            int index = indexCache.getOrDefault(input, -1);
            NBTTagList enchantments = cache.getOrDefault(input, null);

            if (index == -1 || enchantments == null) {
                return;
            }

            ItemStack unenchanted = input.copy();
            enchantments.removeTag(index);

            boolean book = false;
            if (unenchanted.getItem() == Items.ENCHANTED_BOOK) book = true;

            NBTTagCompound compound = unenchanted.getTagCompound();
            assert compound != null;

            if (enchantments.tagCount() == 0) {
                if (book) {
                    unenchanted = new ItemStack(Items.BOOK, 1);
                } else {
                    compound.removeTag("ench");
                }
            } else {
                compound.setTag("ench", enchantments);
            }

            EntityPlayer player = event.getEntityPlayer();
            if (player.openContainer instanceof ContainerRepair) {
                ContainerRepair anvil = (ContainerRepair) player.openContainer;
                anvil.addListener(new AnvilListener(unenchanted, books));
            } else {
                // Not sure this can ever happen?
                boolean drop = !player.inventory.addItemStackToInventory(unenchanted);
                if (drop) {
                    player.dropItem(unenchanted, true, false);
                }
            }
        }
    }

    public static class AnvilListener implements IContainerListener {
        private ItemStack restore;
        private ItemStack books;

        private int restoreFired = 0;

        public AnvilListener(ItemStack restore, ItemStack books) {
            this.restore = restore;
            this.books = books;
            books.shrink(1);
        }

        @SubscribeEvent
        public static void onContainerOpen(PlayerContainerEvent.Open event) {
            clear();
        }

        @SubscribeEvent
        public static void onContainerClose(PlayerContainerEvent.Close event) {
            clear();
        }

        private static void clear() {
            cache.clear();
            outputCache.clear();
            costCache.clear();
            indexCache.clear();
        }

        @Override
        public void sendAllContents(Container containerToSend, NonNullList<ItemStack> itemsList) {
        }

        @Override
        public void sendSlotContents(Container containerToSend, int slotInd, ItemStack stack) {
            if (containerToSend.getSlot(2).getHasStack() && containerToSend.getSlot(2).getStack().getItem() instanceof ItemEnchantedBook) {
                if (slotInd == 0 && stack.isEmpty()) {
                    Slot left = containerToSend.getSlot(slotInd);
                    left.putStack(restore);
                    restoreFired++;
                    if (restoreFired == 2)
                        containerToSend.removeListener(this);
                } else if (slotInd == 1 && stack.isEmpty()) {
                    Slot right = containerToSend.getSlot(slotInd);
                    right.putStack(books);
                    restoreFired++;
                    if (restoreFired == 2)
                        containerToSend.removeListener(this);
                }
            }
        }

        @Override
        public void sendWindowProperty(Container containerIn, int varToUpdate, int newValue) {

        }

        @Override
        public void sendAllWindowProperties(Container containerIn, IInventory inventory) {

        }
    }
}
