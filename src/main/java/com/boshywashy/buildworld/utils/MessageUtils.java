package com.boshywashy.buildworld.utils;

import com.boshywashy.buildworld.BuildWorld;
import org.bukkit.ChatColor;

public class MessageUtils {
    public static String colorize(String message) {
        String primary = BuildWorld.getInstance().getConfigManager().getPrimaryColor();
        String secondary = BuildWorld.getInstance().getConfigManager().getSecondaryColor();

        return ChatColor.translateAlternateColorCodes('&',
                message.replace("%primary%", "&" + primary)
                        .replace("%secondary%", "&" + secondary));
    }
}