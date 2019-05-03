package com.noobanidus.unenchanting;

import com.google.common.base.Predicate;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.util.RecipeItemHelper;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.NonNullList;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.entity.player.AnvilRepairEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.oredict.OreIngredient;

import javax.annotation.Nullable;
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
    private static Int2ObjectOpenHashMap<List<AnvilListener>> listenersMap = new Int2ObjectOpenHashMap<>();
    private static BookIngredient book = null;
    private static Ingredient enchantedBook = Ingredient.fromItem(Items.ENCHANTED_BOOK);

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        if (book == null) {
            book = new BookIngredient();
        }

        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (!book.apply(right)) {
            return;
        }

        if (enchantedBook.apply(left) && enchantedBook.apply(right) || enchantedBook.apply(right)) {
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

            cost = (int) Math.max(1, UnenchantingConfig.base_level_cost * enchantments.tagCount());
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

        if (enchantedBook.apply(input) && enchantedBook.apply(books)) {
            invalidate(player);
            return;
        }

        if (books != null && !books.isEmpty() && book.apply(books) && enchantedBook.apply(output)) {
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

            String tag = book ? "StoredEnchantments" : "ench";

            if (UnenchantingConfig.reduceRepairCost) {
                if (compound.hasKey("RepairCost")) {
                    int repairCost = compound.getInteger("RepairCost");
                    if (repairCost == 1) {
                        compound.removeTag("RepairCost");
                    } else {
                        compound.setInteger("RepairCost", repairCost - 1);
                    }
                }
            }

            if (enchantments.tagCount() == 0) {
                if (book) {
                    unenchanted = new ItemStack(Items.BOOK, 1);
                } else {
                    compound.removeTag(tag);
                }
            } else {
                compound.setTag(tag, enchantments);
            }

            if (player.openContainer instanceof ContainerRepair) {
                ContainerRepair anvil = (ContainerRepair) player.openContainer;
                AnvilListener listener = new AnvilListener(anvil, unenchanted, books, player);
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
            invalidate(player);
        }
    }

    public static void invalidate(EntityPlayer player) {
        if (player.openContainer instanceof ContainerRepair) {
            ContainerRepair anvil = (ContainerRepair) player.openContainer;
            List<AnvilListener> listeners = listenersMap.get(anvil.windowId);
            if (listeners != null && !listeners.isEmpty()) {
                for (AnvilListener listener : listeners) {
                    listener.invalidate(anvil, player);
                }
            }
        }
    }

    public static class AnvilListener implements IContainerListener {
        private final ItemStack restore;
        private final ItemStack books;
        private final ContainerRepair anvil;
        private boolean valid = true;
        private final EntityPlayer player;

        private int restoreFired = 0;

        public AnvilListener(ContainerRepair anvil, ItemStack restore, ItemStack books, EntityPlayer player) {
            this.anvil = anvil;
            this.restore = restore;
            this.books = books;
            this.player = player;
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

            if (enchantedBook.apply(books)) {
                this.valid = false;
                return;
            }

            if (slotInd == 0 && stack.isEmpty()) {
                Slot left = containerToSend.getSlot(slotInd);
                left.putStack(restore);
                restoreFired++;
            } else if (slotInd == 1 && stack.isEmpty() && books.getCount() > 1) {
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

        public void invalidate(ContainerRepair anvil, EntityPlayer player) {
            if (this.anvil == anvil && this.player == player) {
                this.valid = false;
            }
        }
    }

    public static class BookIngredient implements Predicate<ItemStack> {
        private final Int2IntOpenHashMap bookMap;
        private final OreIngredient oreBook;

        public BookIngredient() {
            this.bookMap = UnenchantingConfig.getBooks();
            this.oreBook = new OreIngredient("book");
        }

        @Override
        public boolean apply(@Nullable ItemStack input) {
            if (input == null) return false;

            if (oreBook.apply(input)) return true;

            int meta = input.getMetadata();
            int item = RecipeItemHelper.pack(input);

            return bookMap.get(item) == meta || bookMap.get(item) == -1;
        }
    }
}
