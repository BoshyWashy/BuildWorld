package com.boshywashy.buildworld.managers;

import com.boshywashy.buildworld.BuildWorld;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PermissionManager {
    private final BuildWorld plugin;
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();

    public PermissionManager(BuildWorld plugin) {
        this.plugin = plugin;
    }

    public void applyWorldPermissions(Player player, String worldName) {
        // Remove old attachment if it exists and belongs to us
        removePermissions(player);

        // Create new attachment
        PermissionAttachment attachment = player.addAttachment(plugin);
        attachments.put(player.getUniqueId(), attachment);

        boolean isOwner = plugin.getDatabaseManager().isOwner(worldName, player.getUniqueId());
        boolean isMember = plugin.getDatabaseManager().isMember(worldName, player.getUniqueId());

        // WorldGuard bypass - only if WorldGuard is present
        if ((isOwner || isMember) && Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            attachment.setPermission("worldguard.region.bypass." + worldName, true);
        }

        // Custom permissions from config
        if (isOwner) {
            List<String> perms = plugin.getConfig().getStringList("Permissions.Owner");
            for (String perm : perms) {
                if (perm != null && !perm.isEmpty()) {
                    attachment.setPermission(perm, true);
                }
            }
            attachment.setPermission("buildworld.owner." + worldName, true);
        } else if (isMember) {
            List<String> perms = plugin.getConfig().getStringList("Permissions.Member");
            for (String perm : perms) {
                if (perm != null && !perm.isEmpty()) {
                    attachment.setPermission(perm, true);
                }
            }
            attachment.setPermission("buildworld.member." + worldName, true);
        }

        player.recalculatePermissions();
    }

    public void removePermissions(Player player) {
        PermissionAttachment attachment = attachments.remove(player.getUniqueId());
        if (attachment != null) {
            try {
                player.removeAttachment(attachment);
            } catch (IllegalArgumentException e) {
                // Attachment was already removed or doesn't belong to us, ignore
                plugin.getLogger().fine("Could not remove attachment for " + player.getName() + ": " + e.getMessage());
            }
        }
        player.recalculatePermissions();
    }

    /**
     * Clears all tracked attachments. Use with caution.
     */
    public void clearAllAttachments() {
        attachments.clear();
    }
}