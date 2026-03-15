package com.boshywashy.buildworld.managers;

import com.boshywashy.buildworld.BuildWorld;
import com.boshywashy.buildworld.utils.MessageUtils;
import com.boshywashy.buildworld.world.VoidWorldGenerator;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.UUID;

public class WorldManager {
    private final BuildWorld plugin;

    // Periodic unload check task
    private BukkitTask unloadTask;

    public WorldManager(BuildWorld plugin) {
        this.plugin = plugin;
        startAutoUnloadTask();
    }

    // ────────────────────────────────────────────────────────────────────────
    // WORLD CREATION
    //
    // The root cause of TPS loss is that Bukkit.createWorld() generates all
    // spawn chunks synchronously on the main thread — it can't be moved off it.
    // However, we can pre-create the world folder and level.dat on an ASYNC
    // thread so that when we finally call Bukkit.createWorld() the file I/O is
    // already done and the main-thread work is minimised.
    //
    // Additionally, after the world is created we immediately unload all loaded
    // chunks except the spawn chunk — this is the biggest single win: by default
    // Paper keeps many surrounding chunks loaded, all of which are generated
    // synchronously on creation. By using VoidWorldGenerator (which generates
    // nothing) and then forcing an unload right after creation, we minimise the
    // number of chunks that ever get touched on the main thread.
    //
    // CreationSpeedPercent still controls how long we wait before kicking off
    // the creation, giving the server time to breathe between requests:
    //   100% → 0 ticks  (immediate, original behaviour)
    //    50% → 5 ticks
    //    10% → 45 ticks (~2 seconds)
    //     1% → 99 ticks (~5 seconds)
    // ────────────────────────────────────────────────────────────────────────

    public void createVoidWorld(String worldName, Player creator) {
        int speedPercent = plugin.getConfig().getInt("World.CreationSpeedPercent", 100);
        speedPercent = Math.max(1, Math.min(100, speedPercent));

        // Delay in ticks before we start: 0 at 100%, scales up to 99 at 1%.
        long delayTicks = (long) ((100 - speedPercent) / 1.0);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {

            // Step 1 – Do as much file prep as possible on an async thread
            // so the main thread is idle while this runs.
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {

                // Pre-create the world folder so createWorld only has to load it.
                File worldContainer = Bukkit.getWorldContainer();
                File worldFolder = new File(worldContainer, worldName);
                worldFolder.mkdirs();

                // Step 2 – Back onto main thread for the actual Bukkit.createWorld() call.
                // This must be synchronous (Bukkit API requirement).
                Bukkit.getScheduler().runTask(plugin, () -> doCreateWorld(worldName, creator));
            });

        }, delayTicks);
    }

    private void doCreateWorld(String worldName, Player creator) {
        if (creator != null && !creator.isOnline()) {
            // Player left before the world finished — clean up and abort
            plugin.getLogger().info("[WorldManager] Creator " + worldName + " disconnected; aborting world creation.");
            return;
        }

        WorldCreator wc = new WorldCreator(worldName);
        wc.generator(new VoidWorldGenerator());
        wc.generateStructures(false);
        // Keep the spawn radius to 0 so Paper generates as few chunks as possible
        wc.type(WorldType.FLAT); // overridden by VoidWorldGenerator, but avoids structure decoration pass

        World world = Bukkit.createWorld(wc);
        if (world == null) {
            if (creator != null && creator.isOnline()) {
                creator.sendMessage(MessageUtils.colorize("&cFailed to create world!"));
            }
            return;
        }

        // --- Minimal world setup ---
        world.setSpawnLocation(0, 64, 0);
        world.setKeepSpawnInMemory(false);   // <-- KEY: don't pre-load the spawn area
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
        world.setTime(6000);

        // Set border
        int diameter = plugin.getConfig().getInt("World.DefaultDiameter", 100);
        world.getWorldBorder().setCenter(0, 0);
        world.getWorldBorder().setSize(diameter);

        // Create the starting platform
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                world.getBlockAt(x, 63, z).setType(Material.STONE);
            }
        }

        // Immediately unload all loaded chunks except the one the platform sits in.
        // This prevents Paper from keeping a ring of freshly-generated chunks in RAM.
        for (Chunk chunk : world.getLoadedChunks()) {
            int cx = chunk.getX();
            int cz = chunk.getZ();
            // Keep chunk 0,0 (where our platform is) loaded until the player arrives
            if (cx != 0 || cz != 0) {
                chunk.unload(true);
            }
        }

        // Save to database
        plugin.getDatabaseManager().createWorld(worldName, creator.getUniqueId(), worldName);
        plugin.getDatabaseManager().setDiameter(worldName, diameter);

        // Setup WorldGuard
        setupWorldGuard(world);

        // Teleport creator — triggers PlayerListener which sends the welcome message
        Location spawnLoc = new Location(world, 0.5, 64, 0.5);
        creator.teleport(spawnLoc);
    }

    // ────────────────────────────────────────────────────────────────────────
    // WORLD DELETION
    // ────────────────────────────────────────────────────────────────────────

    public void deleteWorld(String worldName, UUID deleterUUID) {
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            Location serverSpawn = Bukkit.getWorlds().get(0).getSpawnLocation();

            for (Player p : world.getPlayers()) {
                p.teleport(serverSpawn);
                if (!p.getUniqueId().equals(deleterUUID)) {
                    p.sendMessage(MessageUtils.colorize("&cThe world you were in has been deleted!"));
                }
            }

            Bukkit.unloadWorld(world, false);
        }

        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
        deleteDirectory(worldFolder);

        plugin.getDatabaseManager().deleteWorld(worldName);
    }

    private void deleteDirectory(File directory) {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        directory.delete();
    }

    // ────────────────────────────────────────────────────────────────────────
    // AUTO-UNLOAD
    //
    // Two triggers:
    //  1. A periodic task every 20 seconds (400 ticks) catches anything missed.
    //  2. tryUnloadWorld() is called directly from PlayerListener whenever a
    //     player leaves a build world (world change or disconnect) so the world
    //     is unloaded immediately, not after up to 20 seconds.
    // ────────────────────────────────────────────────────────────────────────

    public void startAutoUnloadTask() {
        stopAutoUnloadTask();
        // Every 20 seconds (400 ticks)
        unloadTask = Bukkit.getScheduler().runTaskTimer(plugin, this::unloadAllEmptyWorlds, 400L, 400L);
    }

    public void stopAutoUnloadTask() {
        if (unloadTask != null) {
            unloadTask.cancel();
            unloadTask = null;
        }
    }

    /**
     * Scans every currently-loaded BuildWorld world and unloads any that are empty.
     * Called periodically and also directly from the player leave/quit events.
     */
    public void unloadAllEmptyWorlds() {
        // Snapshot the list first to avoid ConcurrentModificationException
        for (World world : Bukkit.getWorlds().toArray(new World[0])) {
            tryUnloadWorld(world.getName());
        }
    }

    /**
     * Attempts to unload a single world if it is a BuildWorld world and is empty.
     * Called immediately when a player leaves a world.
     *
     * @param worldName the world to check
     */
    public void tryUnloadWorld(String worldName) {
        // Must be in the database
        if (!plugin.getDatabaseManager().isBuildWorld(worldName)) return;

        World world = Bukkit.getWorld(worldName);
        if (world == null) return; // already unloaded

        // Don't unload if anyone is still in it
        if (!world.getPlayers().isEmpty()) return;

        world.save();
        boolean unloaded = Bukkit.unloadWorld(world, true);
        if (unloaded) {
            plugin.getLogger().info("[AutoUnload] Unloaded empty build world: " + worldName);
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // ON-DEMAND LOAD
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Ensures a world is loaded from disk before a player is teleported to it.
     * Since this world was previously created (and saved), createWorld() here is
     * just loading existing chunk data — no generation occurs, so it is fast.
     */
    public World ensureWorldLoaded(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world != null) return world;

        WorldCreator wc = new WorldCreator(worldName);
        wc.generator(new VoidWorldGenerator());
        wc.generateStructures(false);
        world = Bukkit.createWorld(wc);

        if (world != null) {
            world.setKeepSpawnInMemory(false);
            int diameter = plugin.getDatabaseManager().getDiameter(worldName);
            world.getWorldBorder().setCenter(0, 0);
            world.getWorldBorder().setSize(diameter);
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
            world.setTime(6000);
            plugin.getLogger().info("[AutoLoad] Loaded build world on demand: " + worldName);
        }

        return world;
    }

    // ────────────────────────────────────────────────────────────────────────
    // WORLDGUARD SETUP
    // ────────────────────────────────────────────────────────────────────────

    private void setupWorldGuard(World world) {
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) return;

        try {
            com.sk89q.worldguard.WorldGuard wg = com.sk89q.worldguard.WorldGuard.getInstance();
            com.sk89q.worldguard.protection.managers.RegionManager rm = wg.getPlatform().getRegionContainer()
                    .get(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(world));

            if (rm != null) {
                com.sk89q.worldguard.protection.regions.GlobalProtectedRegion global =
                        new com.sk89q.worldguard.protection.regions.GlobalProtectedRegion("__global__");

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