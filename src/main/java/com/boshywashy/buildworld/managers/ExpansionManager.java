package com.boshywashy.buildworld.managers;

import com.boshywashy.buildworld.BuildWorld;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

public class ExpansionManager {
    private final BuildWorld plugin;
    private BukkitTask task;

    public ExpansionManager(BuildWorld plugin) {
        this.plugin = plugin;
    }

    public void startExpansionTask() {
        if (task != null) task.cancel();

        int interval = plugin.getConfigManager().getCheckInterval() * 20;
        int gain = plugin.getConfigManager().getBlockGainPerMin();

        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (UUID uuid : plugin.getDatabaseManager().getAllOnlineOwners()) {
                plugin.getDatabaseManager().addExpansionBlocks(uuid, gain);
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    // Optional: notify player
                }
            }
        }, interval, interval);
    }

    public void stopExpansionTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}