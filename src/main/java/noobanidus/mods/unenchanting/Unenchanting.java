package noobanidus.mods.unenchanting;

import net.minecraft.data.DataGenerator;
import net.minecraft.data.ItemTagsProvider;
import net.minecraft.item.Items;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.GatherDataEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("unenchanting")
@SuppressWarnings("WeakerAccess")
public class Unenchanting {
  public static final String MODID = "unenchanting";

  public Unenchanting () {
    ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, UnenchantingConfig.CONFIG);
    UnenchantingConfig.loadConfig(UnenchantingConfig.CONFIG, FMLPaths.CONFIGDIR.get().resolve(MODID + "-common.toml"));

    IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
    modBus.addListener(this::gatherData);

    MinecraftForge.EVENT_BUS.addListener(AnvilEventHandler::onAnvilRepair);
    MinecraftForge.EVENT_BUS.addListener(AnvilEventHandler::onAnvilUpdate);
    MinecraftForge.EVENT_BUS.addListener(AnvilEventHandler.AnvilListener::onContainerClose);
    MinecraftForge.EVENT_BUS.addListener(AnvilEventHandler.AnvilListener::onContainerOpen);
  }

  public void gatherData (GatherDataEvent event) {
    DataGenerator gen = event.getGenerator();
    if (event.includeClient()) {
    }
    if (event.includeServer()) {
      gen.addProvider(new ItemTagProvider(gen));
    }
  }

  public static class ItemTagProvider extends ItemTagsProvider {
    public ItemTagProvider(DataGenerator gen) {
      super(gen);
    }

    @Override
    protected void registerTags() {
      getBuilder(UnenchantingConfig.BOOKS).add(Items.BOOK).build(UnenchantingConfig.BOOKS.getId());
    }

    @Override
    public String getName() {
      return "Unenchanting book tags";
    }
  }
}
