package com.boshywashy.buildworld.managers;

import com.boshywashy.buildworld.BuildWorld;
import com.boshywashy.buildworld.utils.MessageUtils;
import com.boshywashy.buildworld.world.VoidWorldGenerator;
import org.bukkit.*;
import org.bukkit.entity.Player;

import java.util.Random;
import java.util.UUID;

public class WorldManager {
    private final BuildWorld plugin;

    public WorldManager(BuildWorld plugin) {
        this.plugin = plugin;
    }

    public void createVoidWorld(String worldName, Player creator) {
        WorldCreator wc = new WorldCreator(worldName);
        wc.generator(new VoidWorldGenerator());
        wc.generateStructures(false);

        World world = Bukkit.createWorld(wc);
        if (world == null) {
            creator.sendMessage(MessageUtils.colorize("&cFailed to create world!"));
            return;
        }

        // Setup world
        world.setSpawnLocation(0, 64, 0);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setTime(6000);

        // Set border
        int diameter = plugin.getConfig().getInt("World.DefaultDiameter", 100);
        world.getWorldBorder().setCenter(0, 0);
        world.getWorldBorder().setSize(diameter);

        // Create platform at Y=63 (1 block lower than before)
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                world.getBlockAt(x, 63, z).setType(Material.STONE);
            }
        }

        // Save to database
        plugin.getDatabaseManager().createWorld(worldName, creator.getUniqueId(), worldName);
        plugin.getDatabaseManager().setDiameter(worldName, diameter);

        // Setup WorldGuard
        setupWorldGuard(world);

        // Teleport creator to center of platform
        Location spawnLoc = new Location(world, 0.5, 64, 0.5);
        creator.teleport(spawnLoc);
        // No "world created" message here - the welcome message in PlayerListener will handle it
    }

    public void deleteWorld(String worldName, UUID deleterUUID) {
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            // Get server spawn for teleporting players
            Location serverSpawn = Bukkit.getWorlds().get(0).getSpawnLocation();

            // Teleport all players to server spawn first
            for (Player p : world.getPlayers()) {
                p.teleport(serverSpawn);
                // Only send message if they didn't delete the world
                if (!p.getUniqueId().equals(deleterUUID)) {
                    p.sendMessage(MessageUtils.colorize("&cThe world you were in has been deleted!"));
                }
            }

            // Unload world
            Bukkit.unloadWorld(world, false);
        }

        // Delete folder
        java.io.File worldFolder = new java.io.File(Bukkit.getWorldContainer(), worldName);
        deleteDirectory(worldFolder);

        plugin.getDatabaseManager().deleteWorld(worldName);
    }

    private void deleteDirectory(java.io.File directory) {
        if (directory.isDirectory()) {
            java.io.File[] files = directory.listFiles();
            if (files != null) {
                for (java.io.File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        directory.delete();
    }

    private void setupWorldGuard(World world) {
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) return;

        try {
            com.sk89q.worldguard.WorldGuard wg = com.sk89q.worldguard.WorldGuard.getInstance();
            com.sk89q.worldguard.protection.managers.RegionManager rm = wg.getPlatform().getRegionContainer()
                    .get(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(world));

            if (rm != null) {
                com.sk89q.worldguard.protection.regions.GlobalProtectedRegion global =
                        new com.sk89q.worldguard.protection.regions.GlobalProtectedRegion("__global__");

                // Set flags from config
                global.setFlag(com.sk89q.worldguard.protection.flags.Flags.PASSTHROUGH,
                        com.sk89q.worldguard.protection.flags.StateFlag.State.DENY);
                global.setFlag(com.sk89q.worldguard.protection.flags.Flags.BUILD,
                        com.sk89q.worldguard.protection.flags.StateFlag.State.DENY);

                rm.addRegion(global);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}