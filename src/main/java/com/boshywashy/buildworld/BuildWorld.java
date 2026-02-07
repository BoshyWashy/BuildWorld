package com.boshywashy.buildworld;

import com.boshywashy.buildworld.commands.AdminCommand;
import com.boshywashy.buildworld.commands.BuildWorldCommand;
import com.boshywashy.buildworld.commands.TabCompleter;
import com.boshywashy.buildworld.database.DatabaseManager;
import com.boshywashy.buildworld.gui.WorldMenuGUI;
import com.boshywashy.buildworld.listeners.PlayerListener;
import com.boshywashy.buildworld.listeners.WorldListener;
import com.boshywashy.buildworld.managers.*;
import com.boshywashy.buildworld.world.VoidWorldGenerator;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class BuildWorld extends JavaPlugin {
    private static BuildWorld instance;
    private DatabaseManager databaseManager;
    private ConfigManager configManager;
    private WorldManager worldManager;
    private PermissionManager permissionManager;
    private ExpansionManager expansionManager;
    private WorldMenuGUI worldMenuGUI;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Initialize managers
        this.configManager = new ConfigManager(this);
        this.databaseManager = new DatabaseManager(this);
        this.worldManager = new WorldManager(this);
        this.permissionManager = new PermissionManager(this);
        this.expansionManager = new ExpansionManager(this);
        this.worldMenuGUI = new WorldMenuGUI(this);

        // Load all worlds from database
        loadWorlds();

        // Register commands
        getCommand("buildworld").setExecutor(new BuildWorldCommand(this));
        getCommand("buildworld").setTabCompleter(new TabCompleter(this));
        getCommand("buildworldadmin").setExecutor(new AdminCommand(this));
        getCommand("buildworldadmin").setTabCompleter(new TabCompleter(this));

        // Register listeners
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new WorldListener(this), this);
        getServer().getPluginManager().registerEvents(worldMenuGUI, this);

        // Start expansion task if enabled
        if (configManager.isOnlineExpansionEnabled()) {
            expansionManager.startExpansionTask();
        }

        getLogger().info("BuildWorld has been enabled!");
    }

    private void loadWorlds() {
        List<String> worlds = databaseManager.getAllWorlds();
        for (String worldName : worlds) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                // World not loaded, load it
                WorldCreator wc = new WorldCreator(worldName);
                wc.generator(new VoidWorldGenerator());
                wc.generateStructures(false);
                world = Bukkit.createWorld(wc);

                if (world != null) {
                    // Restore settings
                    int diameter = databaseManager.getDiameter(worldName);
                    world.getWorldBorder().setCenter(0, 0);
                    world.getWorldBorder().setSize(diameter);
                    world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
                    world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
                    world.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
                    world.setTime(6000);
                    getLogger().info("Loaded world: " + worldName);
                }
            }
        }
    }

    @Override
    public void onDisable() {
        if (expansionManager != null) {
            expansionManager.stopExpansionTask();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("BuildWorld has been disabled!");
    }

    public static BuildWorld getInstance() {
        return instance;
    }

    // Getters
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public ConfigManager getConfigManager() { return configManager; }
    public WorldManager getWorldManager() { return worldManager; }
    public PermissionManager getPermissionManager() { return permissionManager; }
    public ExpansionManager getExpansionManager() { return expansionManager; }
    public WorldMenuGUI getWorldMenuGUI() { return worldMenuGUI; }
}