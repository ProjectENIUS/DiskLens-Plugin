package dev.disklens.platform;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** Spigot / Paper / Purpur / other non-Folia servers. */
final class BukkitPlatform implements Platform {
    private final Plugin plugin;
    private final String name;

    BukkitPlatform(Plugin plugin, String name) {
        this.plugin = plugin;
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean folia() {
        return false;
    }

    @Override
    public void async(Runnable r) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, r);
    }

    @Override
    public Task asyncRepeating(Runnable r, long periodMillis) {
        long ticks = Math.max(1, periodMillis / 50);
        final BukkitTask t = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, r, ticks, ticks);
        return new Task() {
            @Override
            public void cancel() {
                t.cancel();
            }
        };
    }

    @Override
    public void forPlayer(Player p, Runnable r) {
        if (Bukkit.isPrimaryThread()) {
            r.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, r);
        }
    }

    @Override
    public void cancelAll() {
        Bukkit.getScheduler().cancelTasks(plugin);
    }
}
