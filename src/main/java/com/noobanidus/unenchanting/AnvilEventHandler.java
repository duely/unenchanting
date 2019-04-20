package com.noobanidus.unenchanting;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemBook;
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

import java.util.*;

@Mod.EventBusSubscriber(modid = Unenchanting.MODID)
@SuppressWarnings("unused")
public class AnvilEventHandler {
    private static Map<ItemStack, NBTTagList> cache = new HashMap<>();
    private static Map<ItemStack, ItemStack> outputCache = new HashMap<>();
    private static Object2IntOpenHashMap<ItemStack> costCache = new Object2IntOpenHashMap<>();
    private static Object2IntOpenHashMap<ItemStack> indexCache = new Object2IntOpenHashMap<>();
    private static Int2ObjectOpenHashMap<List<AnvilListener>> listenersMap = new Int2ObjectOpenHashMap<>();
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
        EntityPlayer player = event.getEntityPlayer();

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

            if (player.openContainer instanceof ContainerRepair) {
                ContainerRepair anvil = (ContainerRepair) player.openContainer;
                AnvilListener listener = new AnvilListener(anvil, unenchanted, books);
                List<AnvilListener> listeners = listenersMap.computeIfAbsent(anvil.windowId, ArrayList::new);
                anvil.addListener(listener);
                listeners.add(listener);
            } else {
                // If the anvil breaks
                boolean drop = !player.inventory.addItemStackToInventory(unenchanted);
                if (drop) {
                    player.dropItem(unenchanted, true, false);
                    player.dropItem(books, true, false);
                }
            }
        } else {
            if (player.openContainer instanceof ContainerRepair) {
                ContainerRepair anvil = (ContainerRepair) player.openContainer;
                List<AnvilListener> listeners = listenersMap.get(anvil.windowId);
                if (listeners != null && !listeners.isEmpty()) {
                    for (AnvilListener listener : listeners) {
                        listener.invalidate(anvil);
                    }
                }
            }
        }
    }

    public static class AnvilListener implements IContainerListener {
        private final ItemStack restore;
        private final ItemStack books;
        private final ContainerRepair anvil;
        private boolean valid = true;

        private int restoreFired = 0;

        public AnvilListener(ContainerRepair anvil, ItemStack restore, ItemStack books) {
            this.anvil = anvil;
            this.restore = restore;
            this.books = books;
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
            if (restoreFired == 2 || !valid) return;

            if (slotInd == 0 && stack.isEmpty()) {
                Slot left = containerToSend.getSlot(slotInd);
                left.putStack(restore);
                restoreFired++;
            } else if (slotInd == 1 && stack.isEmpty()) {
                Slot right = containerToSend.getSlot(slotInd);
                right.putStack(books);
                restoreFired++;
            }
        }

        @Override
        public void sendWindowProperty(Container containerIn, int varToUpdate, int newValue) {
        }

        @Override
        public void sendAllWindowProperties(Container containerIn, IInventory inventory) {
        }

        public void invalidate(ContainerRepair anvil) {
            if (this.anvil == anvil) {
                this.valid = false;
            }
        }
    }
}
