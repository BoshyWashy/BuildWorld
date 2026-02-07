package com.boshywashy.buildworld.listeners;

import com.boshywashy.buildworld.BuildWorld;
import com.boshywashy.buildworld.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class WorldListener implements Listener {
    private final BuildWorld plugin;

    public WorldListener(BuildWorld plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onWorldLoad(org.bukkit.event.world.WorldLoadEvent event) {
        World world = event.getWorld();
        if (!plugin.getDatabaseManager().isBuildWorld(world.getName())) return;

        int diameter = plugin.getDatabaseManager().getDiameter(world.getName());
        world.getWorldBorder().setCenter(0, 0);
        world.getWorldBorder().setSize(diameter);
    }

    @EventHandler
    public void onWorldUnload(org.bukkit.event.world.WorldUnloadEvent event) {
        String worldName = event.getWorld().getName();
        if (plugin.getDatabaseManager().isBuildWorld(worldName)) {
            for (Player player : event.getWorld().getPlayers()) {
                plugin.getPermissionManager().removePermissions(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();

        if (!plugin.getDatabaseManager().isBuildWorld(world.getName())) return;

        // Check maintenance mode - no building allowed
        if (plugin.getConfig().getBoolean("Maintenance.Enabled", false)) {
            event.setCancelled(true);
            player.sendMessage(MessageUtils.colorize("&cBuildWorld is currently under maintenance!"));
            return;
        }

        if (player.hasPermission("buildworld.bypass") || player.hasPermission("buildworld.admin")) return;

        String worldName = world.getName();
        boolean isOwner = plugin.getDatabaseManager().isOwner(worldName, player.getUniqueId());
        boolean isMember = plugin.getDatabaseManager().isMember(worldName, player.getUniqueId());

        if (!isOwner && !isMember) {
            event.setCancelled(true);
            player.sendMessage(MessageUtils.colorize("%primary%You don't have permission to build in this world!"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();

        if (!plugin.getDatabaseManager().isBuildWorld(world.getName())) return;

        // Check maintenance mode - no building allowed
        if (plugin.getConfig().getBoolean("Maintenance.Enabled", false)) {
            event.setCancelled(true);
            player.sendMessage(MessageUtils.colorize("&cBuildWorld is currently under maintenance!"));
            return;
        }

        if (player.hasPermission("buildworld.bypass") || player.hasPermission("buildworld.admin")) return;

        String worldName = world.getName();
        boolean isOwner = plugin.getDatabaseManager().isOwner(worldName, player.getUniqueId());
        boolean isMember = plugin.getDatabaseManager().isMember(worldName, player.getUniqueId());

        if (!isOwner && !isMember) {
            event.setCancelled(true);
            player.sendMessage(MessageUtils.colorize("%primary%You don't have permission to build in this world!"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN &&
                event.getCause() != PlayerTeleportEvent.TeleportCause.COMMAND) return;

        World toWorld = event.getTo().getWorld();
        if (!plugin.getDatabaseManager().isBuildWorld(toWorld.getName())) return;

        Player player = event.getPlayer();
        String worldName = toWorld.getName();

        // Check maintenance mode
        if (plugin.getConfig().getBoolean("Maintenance.Enabled", false)) {
            if (!player.hasPermission("buildworld.admin")) {
                event.setCancelled(true);
                player.sendMessage(MessageUtils.colorize(plugin.getConfig().getString("Maintenance.KickMessage",
                        "&cThe BuildWorld system is currently under maintenance. Please try again later.")));
                return;
            }
        }

        if (player.hasPermission("buildworld.bypass") || player.hasPermission("buildworld.admin")) return;

        if (plugin.getDatabaseManager().isOpen(worldName)) return;

        if (plugin.getDatabaseManager().isOwner(worldName, player.getUniqueId()) ||
                plugin.getDatabaseManager().isMember(worldName, player.getUniqueId())) {
            return;
        }

        event.setCancelled(true);
        player.sendMessage(MessageUtils.colorize("&cThis world is closed to visitors!"));
    }
}