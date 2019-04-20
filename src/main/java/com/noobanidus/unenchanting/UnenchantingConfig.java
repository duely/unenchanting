package com.noobanidus.unenchanting;

import net.minecraftforge.common.config.Config;

@Config(modid = Unenchanting.MODID)
public class UnenchantingConfig {
    @Config.Comment("The base level cost per enchantment on the book to remove the first enchantment")
    @Config.Name("Base XP Cost")
    public static int BASE_LEVEL_COST = 2;
}
