package com.noobanidus.unenchanting;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLLoadCompleteEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod.EventBusSubscriber
@Mod(modid = Unenchanting.MODID, name = Unenchanting.MODNAME, version = Unenchanting.VERSION)
@SuppressWarnings("WeakerAccess")
public class Unenchanting {
    public static final String MODID = "unenchanting";
    public static final String MODNAME = "Unenchanting";
    public static final String VERSION = "GRADLE:VERSION";

    public final static Logger LOG = LogManager.getLogger(MODID);

    @Mod.EventHandler
    public void loadComplete(FMLLoadCompleteEvent event) {
        LOG.info("Unenchanting loaded.");
    }
}
