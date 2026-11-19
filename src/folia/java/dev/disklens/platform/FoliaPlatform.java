package dev.disklens.platform;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;

/** Folia: async scheduler for work, entity scheduler for player output. Loaded reflectively, only on Folia. */
public final class FoliaPlatform implements Platform {
    private final Plugin plugin;
    private final String name;

    public FoliaPlatform(Plugin plugin, String name) {
        this.plugin = plugin;
        this.name = name;
    }

    @Override
    public String name() {
        return name + " (Folia schedulers)";
    }

    @Override
    public boolean folia() {
        return true;
    }

    @Override
    public void async(Runnable r) {
        Bukkit.getAsyncScheduler().runNow(plugin, t -> r.run());
    }

    @Override
    public Task asyncRepeating(Runnable r, long periodMillis) {
        long p = Math.max(1, periodMillis);
        ScheduledTask t = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, x -> r.run(), p, p, TimeUnit.MILLISECONDS);
        return t::cancel;
    }

    @Override
    public void forPlayer(Player p, Runnable r) {
        p.getScheduler().run(plugin, t -> r.run(), null);
    }

    @Override
    public void cancelAll() {
        Bukkit.getAsyncScheduler().cancelTasks(plugin);
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
    }
}
