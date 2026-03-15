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

        Inventory inv = Bukkit.createInventory(null, 54, MessageUtils.colorize("&l%primary%BuildWorld Menu"));

        // Top 18 slots: personal worlds (owned + member) — always shown regardless of hidden status
        int slot = 0;
        for (int i = 0; i < 18 && i < data.personalWorlds.size(); i++) {
            addWorldItem(inv, slot++, data.personalWorlds.get(i), player);
        }

        // Separator row
        for (int i = 18; i < 27; i++) {
            inv.setItem(i, createGlassPane(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        // Lower 18 slots: online/all worlds (paginated)
        int onlineWorldsPerPage = 18;
        int startIdx = (page - 1) * onlineWorldsPerPage;
        int endIdx = Math.min(startIdx + onlineWorldsPerPage, data.onlineWorlds.size());

        slot = 27;
        for (int i = startIdx; i < endIdx; i++) {
            addWorldItem(inv, slot++, data.onlineWorlds.get(i), player);
        }

        // Bottom navigation row
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

    /**
     * Who can see a hidden world in the BOTTOM (public discovery) section:
     *   - Operators (see it with a grey "(Hidden)" tag)
     *   - The world's owner
     *   - Members of the world
     *
     * The TOP (personal) section always shows all worlds the viewer owns or is a member of,
     * regardless of hidden status — that section is never filtered.
     */
    private boolean canViewHiddenWorld(String worldName, UUID viewerUUID, boolean isOp) {
        if (isOp) return true;
        if (plugin.getDatabaseManager().isOwner(worldName, viewerUUID)) return true;
        if (plugin.getDatabaseManager().isMember(worldName, viewerUUID)) return true;
        return false;
    }

    private void calculateWorldLists(Player player, MenuData data) {
        UUID playerUUID = player.getUniqueId();
        boolean isOp = player.isOp();

        // ── Personal section (top 18 slots) ─────────────────────────────────
        // Always includes all worlds the viewer owns or is a member of,
        // regardless of hidden/open status.
        data.personalWorlds = new ArrayList<>();
        Set<String> personalSet = new LinkedHashSet<>();
        personalSet.addAll(plugin.getDatabaseManager().getOwnedWorlds(playerUUID));
        personalSet.addAll(plugin.getDatabaseManager().getMemberWorlds(playerUUID));
        data.personalWorlds.addAll(personalSet);

        // ── Public discovery section (bottom 18 slots) ───────────────────────
        data.onlineWorlds = new ArrayList<>();

        if (data.showAllWorlds) {
            // "All Worlds" filter: every world in the database
            List<WorldSortEntry> entries = new ArrayList<>();
            for (String worldName : plugin.getDatabaseManager().getAllWorlds()) {
                boolean hidden = plugin.getDatabaseManager().isHidden(worldName);

                // Skip hidden worlds unless the viewer is allowed to see them
                if (hidden && !canViewHiddenWorld(worldName, playerUUID, isOp)) continue;

                String nick = plugin.getDatabaseManager().getNickname(worldName);
                entries.add(new WorldSortEntry(worldName, nick));
            }
            entries.sort(Comparator.comparing(e -> e.sortKey.toLowerCase()));
            for (WorldSortEntry entry : entries) {
                data.onlineWorlds.add(entry.worldName);
            }

        } else {
            // "Online Only" filter: worlds whose owner is currently online, and that are open
            // (hidden worlds from online owners are still shown to eligible viewers)
            Set<UUID> onlineOwners = new HashSet<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!plugin.getDatabaseManager().getOwnedWorlds(p.getUniqueId()).isEmpty()) {
                    onlineOwners.add(p.getUniqueId());
                }
            }

            List<WorldSortEntry> entries = new ArrayList<>();
            for (UUID ownerUUID : onlineOwners) {
                for (String worldName : plugin.getDatabaseManager().getOwnedWorlds(ownerUUID)) {
                    boolean hidden = plugin.getDatabaseManager().isHidden(worldName);
                    boolean open = plugin.getDatabaseManager().isOpen(worldName);

                    if (hidden) {
                        // Hidden world: only show to eligible viewers (op / owner / member)
                        if (!canViewHiddenWorld(worldName, playerUUID, isOp)) continue;
                    } else {
                        // Normal world: only show if open
                        if (!open) continue;
                    }

                    String nick = plugin.getDatabaseManager().getNickname(worldName);
                    entries.add(new WorldSortEntry(worldName, nick));
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

    /**
     * Renders a world as an inventory item.
     *
     * Visibility rules enforced here match calculateWorldLists:
     * - Hidden worlds are only rendered for ops, the world's owner, or its members.
     * - Hidden worlds show a grey "(Hidden)" tag in the display name so eligible
     *   viewers know why it doesn't appear publicly.
     */
    private void addWorldItem(Inventory inv, int slot, String worldName, Player viewer) {
        String ownerUUID = plugin.getDatabaseManager().getWorldOwner(worldName);
        if (ownerUUID == null) return;

        UUID viewerUUID = viewer.getUniqueId();
        boolean isOp = viewer.isOp();
        boolean isHidden = plugin.getDatabaseManager().isHidden(worldName);

        // Safety guard: never render hidden worlds to viewers who shouldn't see them
        if (isHidden && !canViewHiddenWorld(worldName, viewerUUID, isOp)) return;

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

        // Build display name
        String displayName;
        if (isHidden) {
            // Eligible viewers see hidden worlds with a dark-grey "(Hidden)" tag
            displayName = MessageUtils.colorize("%primary%" + nickname + " &8(Hidden)");
        } else if (isOpen) {
            displayName = MessageUtils.colorize("%primary%" + nickname);
        } else {
            displayName = MessageUtils.colorize("%secondary%" + nickname + " &7(Closed)");
        }
        meta.setDisplayName(displayName);

        List<String> lore = new ArrayList<>();
        lore.add(MessageUtils.colorize("%secondary%Owner: " + ownerName));

        List<UUID> members = plugin.getDatabaseManager().getMembers(worldName);
        if (!members.isEmpty()) {
            StringBuilder memStr = new StringBuilder("%secondary%Members: ");
            int count = 0;
            for (UUID mem : members) {
                if (count >= 5) { memStr.append("... "); break; }
                String memName = Bukkit.getOfflinePlayer(mem).getName();
                if (memName != null) { memStr.append(memName).append(", "); count++; }
            }
            String memString = memStr.toString();
            if (memString.endsWith(", ")) memString = memString.substring(0, memString.length() - 2);
            lore.add(MessageUtils.colorize(memString));
        }

        int diameter = plugin.getDatabaseManager().getDiameter(worldName);
        lore.add(MessageUtils.colorize("%secondary%Diameter: " + diameter + " blocks"));

        if (plugin.getDatabaseManager().isOwner(worldName, viewerUUID)) {
            lore.add(MessageUtils.colorize("%primary%&lYou are the Owner"));
        } else if (plugin.getDatabaseManager().isMember(worldName, viewerUUID)) {
            lore.add(MessageUtils.colorize("%primary%&lYou are a Member"));
        }

        if (!isOpen && !isHidden) {
            lore.add(MessageUtils.colorize("&c&lWorld is Closed"));
        }
        if (isHidden) {
            lore.add(MessageUtils.colorize("&8&lWorld is Hidden"));
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

        // Resolve world from display name — strip colour codes and status tags
        String cleanName = ChatColor.stripColor(displayName);
        cleanName = cleanName.replaceAll("§[0-9a-fk-or]", "").trim();
        cleanName = cleanName.replace(" (Closed)", "").replace(" (Hidden)", "").trim();

        String worldName = plugin.getDatabaseManager().getWorldByNickname(cleanName);
        if (worldName == null) {
            for (String w : plugin.getDatabaseManager().getAllWorlds()) {
                if (plugin.getDatabaseManager().getNickname(w).equalsIgnoreCase(cleanName)) {
                    worldName = w;
                    break;
                }
            }
        }

        if (worldName == null) {
            player.sendMessage(MessageUtils.colorize("&cWorld not found!"));
            return;
        }

        boolean isHidden = plugin.getDatabaseManager().isHidden(worldName);
        boolean isOp = player.isOp();

        // Re-check eligibility at click time (menu data could be stale)
        if (isHidden && !canViewHiddenWorld(worldName, playerUUID, isOp)) {
            player.sendMessage(MessageUtils.colorize("&cThis world is hidden!"));
            player.closeInventory();
            return;
        }

        // Closed world check (hidden worlds bypass open/closed for eligible viewers)
        if (!isHidden && !plugin.getDatabaseManager().isOpen(worldName) &&
                !plugin.getDatabaseManager().isOwner(worldName, playerUUID) &&
                !plugin.getDatabaseManager().isMember(worldName, playerUUID)) {
            player.sendMessage(MessageUtils.colorize("&cThis world is closed!"));
            player.closeInventory();
            return;
        }

        // Maintenance mode — only admins bypass
        if (plugin.getConfig().getBoolean("Maintenance.Enabled", false) && !player.hasPermission("buildworld.admin")) {
            player.sendMessage(MessageUtils.colorize(plugin.getConfig().getString("Maintenance.KickMessage",
                    "&cThe BuildWorld system is currently under maintenance.")));
            player.closeInventory();
            return;
        }

        // Ensure world is loaded before teleporting
        World world = plugin.getWorldManager().ensureWorldLoaded(worldName);
        if (world != null) {
            Location spawnLoc = plugin.getDatabaseManager().getWorldSpawn(worldName);
            player.teleport(spawnLoc);
            String nickname = plugin.getDatabaseManager().getNickname(worldName);
            player.sendMessage(MessageUtils.colorize("%primary%Visiting %secondary%" + nickname));
        } else {
            player.sendMessage(MessageUtils.colorize("&cWorld could not be loaded!"));
        }
        player.closeInventory();
    }
}