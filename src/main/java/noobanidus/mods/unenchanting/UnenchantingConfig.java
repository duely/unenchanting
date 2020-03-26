package noobanidus.mods.unenchanting;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import net.minecraft.item.Item;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.Tag;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;

import java.nio.file.Path;

public class UnenchantingConfig {
  private static final ForgeConfigSpec.Builder COMMON_BUILDER = new ForgeConfigSpec.Builder();
  public static ForgeConfigSpec CONFIG;

  public static ForgeConfigSpec.ConfigValue<Double> BASE_LEVEL_COST;
  public static ForgeConfigSpec.ConfigValue<Boolean> REDUCE_REPAIR_COST;

  static {
    BASE_LEVEL_COST = COMMON_BUILDER.define("the base level cost per enchantment removed", 1.5);
    REDUCE_REPAIR_COST = COMMON_BUILDER.define("whether or not removing an enchantment reduces the current repair cost", true);
    CONFIG = COMMON_BUILDER.build();
  }

  public static Tag<Item> BOOKS = new ItemTags.Wrapper(new ResourceLocation("forge", "books"));

  public static void loadConfig(ForgeConfigSpec spec, Path path) {
    CommentedFileConfig configData = CommentedFileConfig.builder(path).sync().autosave().writingMode(WritingMode.REPLACE).build();
    configData.load();
    spec.setConfig(configData);
  }
}
