package dev.disklens.platform;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Scheduling abstraction over the Bukkit scheduler and Folia's region schedulers. */
public interface Platform {

    interface Task {
        void cancel();
    }

    String name();

    boolean folia();

    /** Runs off the main/region threads. */
    void async(Runnable r);

    /** Repeats off the main/region threads; first run after one period. */
    Task asyncRepeating(Runnable r, long periodMillis);

    /** Runs on the thread that owns the player (main thread, or the player's region on Folia). */
    void forPlayer(Player p, Runnable r);

    /** Cancels everything this plugin scheduled. */
    void cancelAll();

    final class Detect {
        private Detect() {
        }

        public static Platform create(Plugin plugin) {
            String server = Bukkit.getName() + " " + Bukkit.getVersion();
            try {
                Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            } catch (ClassNotFoundException e) {
                return new BukkitPlatform(plugin, server);
            }
            try {
                // compiled separately against the Folia API (Java 17), loaded only on Folia
                Class<?> c = Class.forName("dev.disklens.platform.FoliaPlatform");
                return (Platform) c.getConstructor(Plugin.class, String.class).newInstance(plugin, server);
            } catch (ReflectiveOperationException | LinkageError e) {
                throw new IllegalStateException("Folia detected but the Folia scheduler bridge failed to load", e);
            }
        }
    }
}
