package com.noobanidus.unenchanting;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.client.util.RecipeItemHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.config.Config;

@Config(modid = Unenchanting.MODID)
public class UnenchantingConfig {
    @Config.Comment("The base level cost per enchantment on the book to remove the first enchantment, rounded up to the nearest integer.")
    @Config.Name("Base XP Cost")
    public static double base_level_cost = 1.5;

    @Config.Comment("List of books that can be enchanted with StoredEnchantments. Format: minecraft:book,0. When 0, metadata can be ommitted. Use * to denote any metadata. This is for additional items not contained within the 'books' ore dictionary entry.")
    @Config.Name("Book Names")
    public static String[] books = new String[]{
            "minecraft:book,0"
    };

    @Config.Comment("Removing an enchantment from an item reduces its repair cost")
    @Config.Name("Reduce Repair Cost")
    public static boolean reduceRepairCost = true;

    private static Int2IntOpenHashMap booksList = null;

    public static Int2IntOpenHashMap getBooks() {
        if (booksList == null) {
            booksList = parseBooks();
        }

        return booksList;
    }

    private static Int2IntOpenHashMap parseBooks() {
        Int2IntOpenHashMap result = new Int2IntOpenHashMap();

        for (String book : books) {
            int meta = 0;
            String[] rl = book.split(":");
            if (book.contains(",")) {
                String sMeta = book.split(",")[1];
                try {
                    meta = Integer.parseInt(sMeta);
                } catch (NumberFormatException e) {
                    if (!sMeta.isEmpty() && sMeta.trim().equals("*")) {
                        meta = -1;
                    }
                }
                rl = book.split(",")[0].split(":");
            }
            ResourceLocation rel = new ResourceLocation(rl[0], rl[1]);

            Item item = Item.REGISTRY.getObject(rel);
            if (item == null) {
                Unenchanting.LOG.info("Invalid resource location for books: " + rel.toString());
            } else {
                result.put(RecipeItemHelper.pack(new ItemStack(item)), meta);
            }
        }

        return result;
    }
}
