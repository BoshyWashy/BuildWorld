package com.boshywashy.buildworld.gui;

import com.boshywashy.buildworld.BuildWorld;
import com.boshywashy.buildworld.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class WorldMenuGUI implements Listener {
    private final BuildWorld plugin;
    private final Map<UUID, MenuData> playerMenuData = new HashMap<>();

    private static class MenuData {
        int currentPage = 1;
        boolean showAllWorlds = false;
        List<String> personalWorlds = new ArrayList<>();
        List<String> onlineWorlds = new ArrayList<>();
    }

    public WorldMenuGUI(BuildWorld plugin) {
        this.plugin = plugin;
    }

    public void openMenu(Player player) {
        openMenu(player, 1);
    }

    public void openMenu(Player player, int page) {
        UUID playerUUID = player.getUniqueId();
        MenuData data = playerMenuData.computeIfAbsent(playerUUID, k -> new MenuData());
        data.currentPage = page;

        calculateWorldLists(player, data);

        // Bold title
        Inventory inv = Bukkit.createInventory(null, 54, MessageUtils.colorize("&l%primary%BuildWorld Menu"));

        int slot = 0;
        for (int i = 0; i < 18 && i < data.personalWorlds.size(); i++) {
            String worldName = data.personalWorlds.get(i);
            addWorldItem(inv, slot++, worldName, playerUUID);
        }

        for (int i = 18; i < 27; i++) {
            ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = glass.getItemMeta();
            meta.setDisplayName(" ");
            glass.setItemMeta(meta);
            inv.setItem(i, glass);
        }

        int onlineWorldsPerPage = 18;
        int startIdx = (page - 1) * onlineWorldsPerPage;
        int endIdx = Math.min(startIdx + onlineWorldsPerPage, data.onlineWorlds.size());

        slot = 27;
        for (int i = startIdx; i < endIdx; i++) {
            String worldName = data.onlineWorlds.get(i);
            addWorldItem(inv, slot++, worldName, playerUUID);
        }

        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta compassMeta = compass.getItemMeta();
        compassMeta.setDisplayName(MessageUtils.colorize("%primary%Teleport to Spawn"));
        compass.setItemMeta(compassMeta);
        inv.setItem(45, compass);

        inv.setItem(46, createGlassPane(Material.GRAY_STAINED_GLASS_PANE, " "));
        inv.setItem(47, createGlassPane(Material.GRAY_STAINED_GLASS_PANE, " "));

        boolean hasNextPage = endIdx < data.onlineWorlds.size();
        Material nextPageMat = hasNextPage ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
        ItemStack nextPage = createGlassPane(nextPageMat, hasNextPage ?
                MessageUtils.colorize("%primary%Next Page") : MessageUtils.colorize("%secondary%No Next Page"));
        inv.setItem(48, nextPage);

        ItemStack hopper = new ItemStack(Material.HOPPER);
        ItemMeta hopperMeta = hopper.getItemMeta();
        hopperMeta.setDisplayName(MessageUtils.colorize("%primary%Filter: " +
                (data.showAllWorlds ? "%secondary%All Worlds" : "%secondary%Online Only")));
        List<String> hopperLore = new ArrayList<>();
        hopperLore.add(MessageUtils.colorize("%secondary%Click to toggle"));
        hopperLore.add(MessageUtils.colorize("%secondary%filter mode"));
        hopperMeta.setLore(hopperLore);
        hopper.setItemMeta(hopperMeta);
        inv.setItem(49, hopper);

        boolean hasPrevPage = page > 1;
        Material prevPageMat = hasPrevPage ? Material.RED_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
        ItemStack prevPage = createGlassPane(prevPageMat, hasPrevPage ?
                MessageUtils.colorize("%primary%Previous Page") : MessageUtils.colorize("%secondary%No Previous Page"));
        inv.setItem(50, prevPage);

        inv.setItem(51, createGlassPane(Material.GRAY_STAINED_GLASS_PANE, " "));
        inv.setItem(52, createGlassPane(Material.GRAY_STAINED_GLASS_PANE, " "));

        ItemStack barrier = new ItemStack(Material.BARRIER);
        ItemMeta barrierMeta = barrier.getItemMeta();
        barrierMeta.setDisplayName(MessageUtils.colorize("&cClose Menu"));
        barrier.setItemMeta(barrierMeta);
        inv.setItem(53, barrier);

        player.openInventory(inv);
    }

    private void calculateWorldLists(Player player, MenuData data) {
        UUID playerUUID = player.getUniqueId();

        data.personalWorlds = new ArrayList<>();
        List<String> owned = plugin.getDatabaseManager().getOwnedWorlds(playerUUID);
        List<String> memberOf = plugin.getDatabaseManager().getMemberWorlds(playerUUID);

        Set<String> personalSet = new LinkedHashSet<>();
        personalSet.addAll(owned);
        personalSet.addAll(memberOf);
        data.personalWorlds.addAll(personalSet);

        data.onlineWorlds = new ArrayList<>();

        if (data.showAllWorlds) {
            // ALL worlds in database (including closed ones)
            List<String> allWorlds = plugin.getDatabaseManager().getAllWorlds();
            List<WorldSortEntry> entries = new ArrayList<>();

            for (String worldName : allWorlds) {
                String nick = plugin.getDatabaseManager().getNickname(worldName);
                entries.add(new WorldSortEntry(worldName, nick));
            }

            entries.sort(Comparator.comparing(e -> e.sortKey.toLowerCase()));
            for (WorldSortEntry entry : entries) {
                data.onlineWorlds.add(entry.worldName);
            }
        } else {
            // Only worlds owned by online players (open only)
            Set<UUID> onlineOwners = new HashSet<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!plugin.getDatabaseManager().getOwnedWorlds(p.getUniqueId()).isEmpty()) {
                    onlineOwners.add(p.getUniqueId());
                }
            }

            List<WorldSortEntry> entries = new ArrayList<>();
            for (UUID ownerUUID : onlineOwners) {
                List<String> worlds = plugin.getDatabaseManager().getOwnedWorlds(ownerUUID);
                for (String worldName : worlds) {
                    if (plugin.getDatabaseManager().isOpen(worldName)) {
                        String nick = plugin.getDatabaseManager().getNickname(worldName);
                        entries.add(new WorldSortEntry(worldName, nick));
                    }
                }
            }

            entries.sort(Comparator.comparing(e -> e.sortKey.toLowerCase()));
            for (WorldSortEntry entry : entries) {
                data.onlineWorlds.add(entry.worldName);
            }
        }
    }

    private static class WorldSortEntry {
        final String worldName;
        final String sortKey;

        WorldSortEntry(String worldName, String nickname) {
            this.worldName = worldName;
            this.sortKey = nickname != null ? nickname : worldName;
        }
    }

    private ItemStack createGlassPane(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private void addWorldItem(Inventory inv, int slot, String worldName, UUID viewerUUID) {
        String ownerUUID = plugin.getDatabaseManager().getWorldOwner(worldName);
        if (ownerUUID == null) return;

        String ownerName = Bukkit.getOfflinePlayer(UUID.fromString(ownerUUID)).getName();
        if (ownerName == null) ownerName = "Unknown";

        String itemName = plugin.getDatabaseManager().getItem(worldName);
        Material mat;
        try {
            mat = Material.valueOf(itemName);
        } catch (IllegalArgumentException e) {
            mat = Material.GRASS_BLOCK;
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        String nickname = plugin.getDatabaseManager().getNickname(worldName);
        if (nickname == null) nickname = worldName;

        boolean isOpen = plugin.getDatabaseManager().isOpen(worldName);
        String displayName = isOpen ?
                MessageUtils.colorize("%primary%" + nickname) :
                MessageUtils.colorize("%secondary%" + nickname + " &7(Closed)");

        meta.setDisplayName(displayName);

        List<String> lore = new ArrayList<>();
        lore.add(MessageUtils.colorize("%secondary%Owner: " + ownerName));

        List<UUID> members = plugin.getDatabaseManager().getMembers(worldName);
        if (!members.isEmpty()) {
            StringBuilder memStr = new StringBuilder("%secondary%Members: ");
            int count = 0;
            for (UUID mem : members) {
                if (count >= 5) {
                    memStr.append("... ");
                    break;
                }
                String memName = Bukkit.getOfflinePlayer(mem).getName();
                if (memName != null) {
                    memStr.append(memName).append(", ");
                    count++;
                }
            }
            String memString = memStr.toString();
            if (memString.endsWith(", ")) {
                memString = memString.substring(0, memString.length() - 2);
            }
            lore.add(MessageUtils.colorize(memString));
        }

        int diameter = plugin.getDatabaseManager().getDiameter(worldName);
        lore.add(MessageUtils.colorize("%secondary%Diameter: " + diameter + " blocks"));

        if (plugin.getDatabaseManager().isOwner(worldName, viewerUUID)) {
            lore.add(MessageUtils.colorize("%primary%&lYou are the Owner"));
        } else if (plugin.getDatabaseManager().isMember(worldName, viewerUUID)) {
            lore.add(MessageUtils.colorize("%primary%&lYou are a Member"));
        }

        if (!isOpen) {
            lore.add(MessageUtils.colorize("&c&lWorld is Closed"));
        }

        lore.add("");
        lore.add(MessageUtils.colorize("%primary%Click to visit!"));

        meta.setLore(lore);
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().contains("BuildWorld Menu")) return;
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        String displayName = clicked.getItemMeta().getDisplayName();
        Player player = (Player) event.getWhoClicked();
        UUID playerUUID = player.getUniqueId();
        MenuData data = playerMenuData.get(playerUUID);

        if (data == null) return;

        Material type = clicked.getType();

        if (type == Material.COMPASS) {
            Location spawnLoc = plugin.getDatabaseManager().getSpawnLocation();
            if (spawnLoc != null) {
                player.teleport(spawnLoc);
                player.sendMessage(MessageUtils.colorize("%primary%Teleported to spawn!"));
            } else {
                player.sendMessage(MessageUtils.colorize("&cSpawn not set!"));
            }
            player.closeInventory();
            return;
        }

        if (type == Material.BARRIER) {
            player.closeInventory();
            return;
        }

        if (type == Material.LIME_STAINED_GLASS_PANE && displayName.contains("Next")) {
            openMenu(player, data.currentPage + 1);
            return;
        }

        if (type == Material.RED_STAINED_GLASS_PANE && displayName.contains("Previous")) {
            openMenu(player, data.currentPage - 1);
            return;
        }

        if (type == Material.HOPPER) {
            data.showAllWorlds = !data.showAllWorlds;
            data.currentPage = 1;
            openMenu(player, 1);
            player.sendMessage(MessageUtils.colorize("%primary%Filter set to: %secondary%" +
                    (data.showAllWorlds ? "All Worlds" : "Online Only")));
            return;
        }

        String nickname = ChatColor.stripColor(displayName);
        nickname = nickname.replaceAll("§[0-9a-fk-or]", "").trim();
        nickname = nickname.replace(" (Closed)", "").trim();

        String worldName = plugin.getDatabaseManager().getWorldByNickname(nickname);
        if (worldName == null) {
            for (String w : plugin.getDatabaseManager().getAllWorlds()) {
                if (plugin.getDatabaseManager().getNickname(w).equalsIgnoreCase(nickname)) {
                    worldName = w;
                    break;
                }
            }
        }

        if (worldName == null) {
            player.sendMessage(MessageUtils.colorize("&cWorld not found!"));
            return;
        }

        // Check if world is closed and player is not member/owner
        if (!plugin.getDatabaseManager().isOpen(worldName) &&
                !plugin.getDatabaseManager().isOwner(worldName, playerUUID) &&
                !plugin.getDatabaseManager().isMember(worldName, playerUUID)) {
            player.sendMessage(MessageUtils.colorize("&cThis world is closed!"));
            player.closeInventory();
            return;
        }

        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            Location spawnLoc = plugin.getDatabaseManager().getWorldSpawn(worldName);
            player.teleport(spawnLoc);
            player.sendMessage(MessageUtils.colorize("%primary%Visiting %secondary%" + nickname));
        } else {
            player.sendMessage(MessageUtils.colorize("&cWorld is not loaded!"));
        }
        player.closeInventory();
    }
}