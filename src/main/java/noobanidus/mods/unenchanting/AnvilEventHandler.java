package noobanidus.mods.unenchanting;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.IContainerListener;
import net.minecraft.inventory.container.RepairContainer;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.entity.player.AnvilRepairEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public class AnvilEventHandler {
  private static Map<ItemStack, ListNBT> cache = new HashMap<>();
  private static Map<ItemStack, ItemStack> outputCache = new HashMap<>();
  private static Object2IntOpenHashMap<ItemStack> costCache = new Object2IntOpenHashMap<>();
  private static Object2IntOpenHashMap<ItemStack> indexCache = new Object2IntOpenHashMap<>();
  private static Int2ObjectOpenHashMap<List<AnvilListener>> listenersMap = new Int2ObjectOpenHashMap<>();
  private static Ingredient book = Ingredient.fromTag(UnenchantingConfig.BOOKS);
  private static Ingredient enchantedBook = Ingredient.fromItems(Items.ENCHANTED_BOOK);

  public static void onAnvilUpdate(AnvilUpdateEvent event) {
    ItemStack left = event.getLeft();
    ItemStack right = event.getRight();
    if (!book.test(right)) {
      return;
    }

    if (enchantedBook.test(left) && enchantedBook.test(right) || enchantedBook.test(right)) {
      return;
    }

    ItemStack bookOutput;
    int cost;

    if (outputCache.containsKey(left) && costCache.containsKey(left)) {
      bookOutput = outputCache.get(left);
      cost = costCache.getInt(left);
    } else {
      ListNBT enchantments;

      if (cache.containsKey(left)) {
        enchantments = cache.get(left);
      } else {

        if (!left.hasTag()) {
          return;
        }

        CompoundNBT tag = left.getTag();

        if (tag == null) {
          return;
        }

        boolean book = false;

        if (left.getItem() == net.minecraft.item.Items.ENCHANTED_BOOK) {
          book = true;
        }

        if (!tag.contains(book ? "StoredEnchantments" : "Enchantments")) return;

        enchantments = tag.getList(book ? "StoredEnchantments" : "Enchantments", 10);
        cache.put(left, enchantments);
      }

      if (enchantments.size() == 0) return;

      CompoundNBT eDef = null;
      Enchantment definition = null;

      int usedIndex = -1;

      for (int i = 0; i < enchantments.size(); i++) {
        eDef = enchantments.getCompound(i);
        ResourceLocation rl = new ResourceLocation(eDef.getString("id"));
        definition = ForgeRegistries.ENCHANTMENTS.getValue(rl);
        if (definition == null || !definition.isAllowedOnBooks()) continue;

        usedIndex = i;
        break;
      }

      if (usedIndex == -1) {
        return;
      }

      Map<Enchantment, Integer> bookEnchants = new HashMap<>();
      bookOutput = new ItemStack(Items.ENCHANTED_BOOK, 1);
      bookEnchants.put(definition, (int) eDef.getShort("lvl"));
      EnchantmentHelper.setEnchantments(bookEnchants, bookOutput);
      outputCache.put(left, bookOutput);

      cost = (int) Math.max(1, UnenchantingConfig.BASE_LEVEL_COST.get() * enchantments.size());
      costCache.put(left, cost);

      indexCache.put(left, usedIndex);
    }

    event.setCanceled(false);
    event.setCost(cost);
    event.setOutput(bookOutput);
    event.setMaterialCost(1);
  }

  public static void onAnvilRepair(AnvilRepairEvent event) {
    ItemStack input = event.getItemInput();
    ItemStack books = event.getIngredientInput();
    ItemStack output = event.getItemResult();
    PlayerEntity player = event.getPlayer();

    if (enchantedBook.test(input) && enchantedBook.test(books)) {
      invalidate(player);
      return;
    }

    if (!books.isEmpty() && book.test(books) && enchantedBook.test(output)) {
      int index = indexCache.getOrDefault(input, -1);
      ListNBT enchantments = cache.getOrDefault(input, null);

      if (index == -1 || enchantments == null) {
        return;
      }

      ItemStack unenchanted = input.copy();
      enchantments.remove(index);

      boolean book = false;
      if (unenchanted.getItem() == Items.ENCHANTED_BOOK) book = true;

      CompoundNBT compound = unenchanted.getTag();
      assert compound != null;

      String tag = book ? "StoredEnchantments" : "Enchantments";

      if (UnenchantingConfig.REDUCE_REPAIR_COST.get()) {
        if (compound.contains("RepairCost")) {
          int repairCost = compound.getInt("RepairCost");
          if (repairCost == 1) {
            compound.remove("RepairCost");
          } else {
            compound.putInt("RepairCost", repairCost - 1);
          }
        }
      }

      if (enchantments.size() == 0) {
        if (book) {
          unenchanted = new ItemStack(net.minecraft.item.Items.BOOK, 1);
        } else {
          compound.remove(tag);
        }
      } else {
        compound.put(tag, enchantments);
      }

      if (player.openContainer instanceof RepairContainer) {
        RepairContainer anvil = (RepairContainer) player.openContainer;
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

  public static void invalidate(PlayerEntity player) {
    if (player.openContainer instanceof RepairContainer) {
      RepairContainer anvil = (RepairContainer) player.openContainer;
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
    private final RepairContainer anvil;
    private boolean valid = true;
    private final PlayerEntity player;

    private int restoreFired = 0;

    public AnvilListener(RepairContainer anvil, ItemStack restore, ItemStack books, PlayerEntity player) {
      this.anvil = anvil;
      this.restore = restore;
      this.books = books;
      this.player = player;
    }

    public static void onContainerOpen(PlayerContainerEvent.Open event) {
      clear();
    }

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

      if (enchantedBook.test(books)) {
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

    public void invalidate(RepairContainer anvil, PlayerEntity player) {
      if (this.anvil == anvil && this.player == player) {
        this.valid = false;
      }
    }
  }
}
