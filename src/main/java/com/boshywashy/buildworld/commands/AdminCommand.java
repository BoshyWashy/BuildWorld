package com.boshywashy.buildworld.commands;

import com.boshywashy.buildworld.BuildWorld;
import com.boshywashy.buildworld.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class AdminCommand implements CommandExecutor {
    private final BuildWorld plugin;
    private final List<String> colors = Arrays.asList("BLACK", "DARK_BLUE", "DARK_GREEN", "DARK_AQUA",
            "DARK_RED", "DARK_PURPLE", "GOLD", "GRAY", "DARK_GRAY", "BLUE", "GREEN", "AQUA",
            "RED", "LIGHT_PURPLE", "YELLOW", "WHITE");

    private final java.util.Map<String, String> colorCodes = new java.util.HashMap<String, String>() {{
        put("BLACK", "0");
        put("DARK_BLUE", "1");
        put("DARK_GREEN", "2");
        put("DARK_AQUA", "3");
        put("DARK_RED", "4");
        put("DARK_PURPLE", "5");
        put("GOLD", "6");
        put("GRAY", "7");
        put("DARK_GRAY", "8");
        put("BLUE", "9");
        put("GREEN", "a");
        put("AQUA", "b");
        put("RED", "c");
        put("LIGHT_PURPLE", "d");
        put("YELLOW", "e");
        put("WHITE", "f");
    }};

    public AdminCommand(BuildWorld plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("buildworld.admin")) {
            sender.sendMessage(MessageUtils.colorize("&cNo permission!"));
            return true;
        }

        if (args.length == 0) {
            sendAdminHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "setmaxworld":
                handleSetMaxWorld(sender, args);
                break;
            case "setworlddiameter":
                handleSetWorldDiameter(sender, args);
                break;
            case "memberedit":
                handleMemberEdit(sender, args);
                break;
            case "colour":
            case "color":
                handleColor(sender, args);
                break;
            case "tutorialadd":
                handleTutorialAdd(sender, args);
                break;
            case "tutorialremove":
                handleTutorialRemove(sender, args);
                break;
            case "tutorialedit":
                handleTutorialEdit(sender, args);
                break;
            case "giveblocksforexpansion":
                handleGiveBlocks(sender, args);
                break;
            case "removeblocksforexpansion":
                handleRemoveBlocks(sender, args);
                break;
            case "onlineworldexpansion":
                handleOnlineExpansion(sender, args);
                break;
            case "blockgaintime":
                handleBlockGainTime(sender, args);
                break;
            case "checkinterval":
                handleCheckInterval(sender, args);
                break;
            case "giveworldcredits":
                handleGiveWorldCredits(sender, args);
                break;
            case "deleteworld":
                handleDeleteWorld(sender, args);
                break;
            case "maintenance":
                handleMaintenance(sender, args);
                break;
            default:
                sendAdminHelp(sender);
                break;
        }

        return true;
    }

    private void handleSetMaxWorld(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.colorize("&cUsage: /bwa SetMaxWorld <number>"));
            return;
        }

        try {
            int max = Integer.parseInt(args[1]);
            plugin.getConfig().set("World.MaxWorldsPerPlayer", max);
            plugin.saveConfig();
            sender.sendMessage(MessageUtils.colorize("%primary%Max worlds set to: %secondary%" + max));
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtils.colorize("&cInvalid number!"));
        }
    }

    private void handleSetWorldDiameter(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.colorize("&cUsage: /bwa SetWorldDiameter <blocks>"));
            return;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("Player only!");
            return;
        }

        Player player = (Player) sender;
        String worldName = player.getWorld().getName();

        if (!plugin.getDatabaseManager().isBuildWorld(worldName)) {
            sender.sendMessage(MessageUtils.colorize("&cYou must be in a build world!"));
            return;
        }

        try {
            int diameter = Integer.parseInt(args[1]);
            plugin.getDatabaseManager().setDiameter(worldName, diameter);
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                world.getWorldBorder().setSize(diameter);
            }
            sender.sendMessage(MessageUtils.colorize("%primary%Diameter set to: %secondary%" + diameter));
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtils.colorize("&cInvalid number!"));
        }
    }

    private void handleMemberEdit(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(MessageUtils.colorize("&cUsage: /bwa MemberEdit <member|owner> <add|remove> <player>"));
            return;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("Player only!");
            return;
        }

        Player player = (Player) sender;
        String worldName = player.getWorld().getName();

        if (!plugin.getDatabaseManager().isBuildWorld(worldName)) {
            sender.sendMessage(MessageUtils.colorize("&cYou must be in a build world!"));
            return;
        }

        String type = args[1].toLowerCase();
        String action = args[2].toLowerCase();
        String targetName = args[3];

        @SuppressWarnings("deprecation")
        UUID targetUUID = Bukkit.getOfflinePlayer(targetName).getUniqueId();

        if (action.equals("add")) {
            String role = type.equals("owner") ? "OWNER" : "MEMBER";
            plugin.getDatabaseManager().addMember(worldName, targetUUID, role);
            sender.sendMessage(MessageUtils.colorize("%primary%Added %secondary%" + targetName +
                    " %primary%as %secondary%" + role));
        } else if (action.equals("remove")) {
            plugin.getDatabaseManager().removeMember(worldName, targetUUID);
            sender.sendMessage(MessageUtils.colorize("%primary%Removed %secondary%" + targetName));
        }
    }

    private void handleColor(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtils.colorize("&cUsage: /bwa colour <primary|secondary> <color>"));
            return;
        }

        String type = args[1].toLowerCase();
        String colorName = args[2].toUpperCase();

        if (!colors.contains(colorName)) {
            sender.sendMessage(MessageUtils.colorize("&cInvalid color! Valid: " + String.join(", ", colors)));
            return;
        }

        String code = colorCodes.get(colorName);
        if (code == null) {
            code = "f";
        }

        if (type.equals("primary")) {
            plugin.getConfig().set("Colors.Primary", code);
        } else {
            plugin.getConfig().set("Colors.Secondary", code);
        }
        plugin.saveConfig();
        plugin.getConfigManager().reload();

        sender.sendMessage(MessageUtils.colorize("%primary%" + type + " color set to: %secondary%" + colorName + " (code: " + code + ")"));
    }

    private void handleTutorialAdd(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtils.colorize("&cUsage: /bwa TutorialAdd <subcommand> <message>"));
            return;
        }

        String sub = args[1].toLowerCase();
        StringBuilder message = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            message.append(args[i]).append(" ");
        }

        plugin.getConfig().set("Tutorials." + sub, message.toString().trim());
        plugin.saveConfig();
        sender.sendMessage(MessageUtils.colorize("%primary%Tutorial added for: %secondary%" + sub));
    }

    private void handleTutorialRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.colorize("&cUsage: /bwa TutorialRemove <subcommand>"));
            return;
        }

        String sub = args[1].toLowerCase();
        if (sub.equals("commands")) {
            sender.sendMessage(MessageUtils.colorize("&cCannot remove default commands help!"));
            return;
        }

        plugin.getConfig().set("Tutorials." + sub, null);
        plugin.saveConfig();
        sender.sendMessage(MessageUtils.colorize("%primary%Tutorial removed for: %secondary%" + sub));
    }

    private void handleTutorialEdit(CommandSender sender, String[] args) {
        handleTutorialAdd(sender, args);
    }

    private void handleGiveBlocks(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtils.colorize("&cUsage: /bwa GiveBlocksForExpansion <player> <amount>"));
            return;
        }

        @SuppressWarnings("deprecation")
        UUID targetUUID = Bukkit.getOfflinePlayer(args[1]).getUniqueId();

        try {
            int amount = Integer.parseInt(args[2]);
            plugin.getDatabaseManager().addExpansionBlocks(targetUUID, amount);
            sender.sendMessage(MessageUtils.colorize("%primary%Gave %secondary%" + args[1] + " " + amount +
                    " %primary%expansion blocks"));
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtils.colorize("&cInvalid number!"));
        }
    }

    private void handleRemoveBlocks(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtils.colorize("&cUsage: /bwa RemoveBlocksForExpansion <player> <amount>"));
            return;
        }

        @SuppressWarnings("deprecation")
        UUID targetUUID = Bukkit.getOfflinePlayer(args[1]).getUniqueId();

        try {
            int amount = Integer.parseInt(args[2]);
            int current = plugin.getDatabaseManager().getExpansionBlocks(targetUUID);

            if (amount > current) {
                sender.sendMessage(MessageUtils.colorize("&cPlayer only has " + current + " blocks! Removing all..."));
                amount = current;
            }

            plugin.getDatabaseManager().addExpansionBlocks(targetUUID, -amount);
            sender.sendMessage(MessageUtils.colorize("%primary%Removed %secondary%" + amount +
                    " %primary%blocks from %secondary%" + args[1]));
            sender.sendMessage(MessageUtils.colorize("%primary%New balance: %secondary%" +
                    plugin.getDatabaseManager().getExpansionBlocks(targetUUID)));
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtils.colorize("&cInvalid number!"));
        }
    }

    private void handleOnlineExpansion(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.colorize("&cUsage: /bwa OnlineWorldExpansion <true|false>"));
            return;
        }

        boolean enabled = Boolean.parseBoolean(args[1]);
        plugin.getConfig().set("OnlineWorldExpansion.Enabled", enabled);
        plugin.saveConfig();

        if (enabled) {
            plugin.getExpansionManager().startExpansionTask();
        } else {
            plugin.getExpansionManager().stopExpansionTask();
        }

        sender.sendMessage(MessageUtils.colorize("%primary%Online expansion set to: %secondary%" + enabled));
    }

    private void handleBlockGainTime(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.colorize("&cUsage: /bwa BlockGainTime <blocks>"));
            return;
        }

        try {
            int blocks = Integer.parseInt(args[1]);
            plugin.getConfig().set("OnlineWorldExpansion.BlockGainPerMin", blocks);
            plugin.saveConfig();
            sender.sendMessage(MessageUtils.colorize("%primary%Block gain per minute set to: %secondary%" + blocks));
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtils.colorize("&cInvalid number!"));
        }
    }

    private void handleCheckInterval(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.colorize("&cUsage: /bwa CheckInterval <seconds>"));
            return;
        }

        try {
            int seconds = Integer.parseInt(args[1]);
            if (seconds < 10) {
                sender.sendMessage(MessageUtils.colorize("&cInterval must be at least 10 seconds!"));
                return;
            }
            plugin.getConfig().set("OnlineWorldExpansion.CheckInterval", seconds);
            plugin.saveConfig();

            if (plugin.getConfigManager().isOnlineExpansionEnabled()) {
                plugin.getExpansionManager().stopExpansionTask();
                plugin.getExpansionManager().startExpansionTask();
            }

            sender.sendMessage(MessageUtils.colorize("%primary%Check interval set to: %secondary%" + seconds + " seconds"));
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtils.colorize("&cInvalid number!"));
        }
    }

    private void handleGiveWorldCredits(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageUtils.colorize("&cUsage: /bwa GiveWorldCredits <player> <amount>"));
            return;
        }

        @SuppressWarnings("deprecation")
        UUID targetUUID = Bukkit.getOfflinePlayer(args[1]).getUniqueId();

        try {
            int amount = Integer.parseInt(args[2]);
            plugin.getDatabaseManager().addExtraWorldCredits(targetUUID, amount);
            int newTotal = plugin.getDatabaseManager().getExtraWorldCredits(targetUUID);
            sender.sendMessage(MessageUtils.colorize("%primary%Gave %secondary%" + args[1] + " " + amount +
                    " %primary%extra world credits"));
            sender.sendMessage(MessageUtils.colorize("%primary%They can now create %secondary%" + newTotal +
                    " %primary%extra worlds"));
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtils.colorize("&cInvalid number!"));
        }
    }

    private void handleDeleteWorld(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.colorize("&cUsage: /bwa DeleteWorld <world>"));
            return;
        }

        String worldName = resolveWorldName(args[1]);
        if (worldName == null) {
            sender.sendMessage(MessageUtils.colorize("&cWorld not found!"));
            return;
        }

        if (!plugin.getDatabaseManager().isBuildWorld(worldName)) {
            sender.sendMessage(MessageUtils.colorize("&cThis is not a build world!"));
            return;
        }

        plugin.getWorldManager().deleteWorld(worldName, sender instanceof Player ? ((Player) sender).getUniqueId() : null);
        sender.sendMessage(MessageUtils.colorize("%primary%World %secondary%" + worldName + " %primary%has been deleted!"));
    }

    private void handleMaintenance(CommandSender sender, String[] args) {
        if (args.length < 2) {
            boolean current = plugin.getConfig().getBoolean("Maintenance.Enabled", false);
            sender.sendMessage(MessageUtils.colorize("%primary%Maintenance mode is currently: %secondary%" + (current ? "ENABLED" : "DISABLED")));
            sender.sendMessage(MessageUtils.colorize("&cUsage: /bwa Maintenance <true|false>"));
            return;
        }

        boolean enabled = Boolean.parseBoolean(args[1]);
        plugin.getConfig().set("Maintenance.Enabled", enabled);
        plugin.saveConfig();

        if (enabled) {
            // Kick all players from build worlds and prevent building
            String kickMessage = plugin.getConfig().getString("Maintenance.KickMessage", "&cThe BuildWorld system is currently under maintenance. Please try again later.");
            for (World world : Bukkit.getWorlds()) {
                if (plugin.getDatabaseManager().isBuildWorld(world.getName())) {
                    Location serverSpawn = plugin.getDatabaseManager().getSpawnLocation();
                    if (serverSpawn == null) {
                        serverSpawn = Bukkit.getWorlds().get(0).getSpawnLocation();
                    }

                    for (Player p : world.getPlayers()) {
                        p.teleport(serverSpawn);
                        p.sendMessage(MessageUtils.colorize(kickMessage));
                    }
                }
            }
            Bukkit.broadcastMessage(MessageUtils.colorize("&c&l[BuildWorld] &cMaintenance mode has been ENABLED. All players have been removed from build worlds."));
        } else {
            Bukkit.broadcastMessage(MessageUtils.colorize("&a&l[BuildWorld] &aMaintenance mode has been DISABLED. BuildWorld is now fully operational."));
        }

        sender.sendMessage(MessageUtils.colorize("%primary%Maintenance mode set to: %secondary%" + enabled));
    }

    private void sendAdminHelp(CommandSender sender) {
        sender.sendMessage(MessageUtils.colorize("%primary%=== Admin Commands ==="));
        sender.sendMessage(MessageUtils.colorize("%secondary%/bwa SetMaxWorld <num>"));
        sender.sendMessage(MessageUtils.colorize("%secondary%/bwa SetWorldDiameter <blocks>"));
        sender.sendMessage(MessageUtils.colorize("%secondary%/bwa MemberEdit <type> <add|remove> <player>"));
        sender.sendMessage(MessageUtils.colorize("%secondary%/bwa colour <primary|secondary> <color>"));
        sender.sendMessage(MessageUtils.colorize("%secondary%/bwa TutorialAdd <sub> <msg>"));
        sender.sendMessage(MessageUtils.colorize("%secondary%/bwa TutorialRemove <sub>"));
        sender.sendMessage(MessageUtils.colorize("%secondary%/bwa TutorialEdit <sub> <msg>"));
        sender.sendMessage(MessageUtils.colorize("%secondary%/bwa GiveBlocksForExpansion <player> <amount>"));
        sender.sendMessage(MessageUtils.colorize("%secondary%/bwa RemoveBlocksForExpansion <player> <amount>"));
        sender.sendMessage(MessageUtils.colorize("%secondary%/bwa OnlineWorldExpansion <true|false>"));
        sender.sendMessage(MessageUtils.colorize("%secondary%/bwa BlockGainTime <blocks>"));
        sender.sendMessage(MessageUtils.colorize("%secondary%/bwa CheckInterval <seconds>"));
        sender.sendMessage(MessageUtils.colorize("%secondary%/bwa GiveWorldCredits <player> <amount>"));
        sender.sendMessage(MessageUtils.colorize("%secondary%/bwa DeleteWorld <world>"));
        sender.sendMessage(MessageUtils.colorize("%secondary%/bwa Maintenance <true|false>"));
    }

    private String resolveWorldName(String input) {
        String byNickname = plugin.getDatabaseManager().getWorldByNickname(input);
        if (byNickname != null) {
            return byNickname;
        }
        if (plugin.getDatabaseManager().isBuildWorld(input)) {
            return input;
        }
        return null;
    }
}