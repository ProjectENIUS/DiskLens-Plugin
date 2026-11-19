package dev.disklens;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Registers the configurable short aliases (e.g. /du) directly in the server command map, and removes them
 * again on disable so PlugMan unload/load leaves nothing behind.
 */
final class CommandAliases {
    private final List<Command> registered = new ArrayList<Command>();
    private CommandMap map;

    private static CommandMap commandMap() {
        try {
            Method m = Bukkit.getServer().getClass().getMethod("getCommandMap");
            return (CommandMap) m.invoke(Bukkit.getServer());
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /** @return aliases that could not take their plain name (another plugin owns it) */
    List<String> register(List<String> aliases, final TabExecutor exec, final String permission, Logger log) {
        List<String> taken = new ArrayList<String>();
        if (aliases.isEmpty()) return taken;
        map = commandMap();
        if (map == null) {
            log.warning("Command map not accessible, aliases " + aliases + " are not registered");
            return taken;
        }
        Map<String, Command> known = known(map);
        if (known != null) {
            try {
                removeWhere(known, new Predicate() {
                    @Override
                    public boolean matches(Command c) {
                        return isStaleDiskLens(c);
                    }
                }, log);
            } catch (RuntimeException e) {
                log.warning("Could not clean up old aliases: " + e);
            }
        }
        for (String raw : aliases) {
            final String alias = raw.trim().toLowerCase(java.util.Locale.ROOT);
            if (alias.isEmpty() || alias.equals("disklens")) continue;
            Command c = new Command(alias, "DiskLens alias", "/" + alias, Collections.<String>emptyList()) {
                @Override
                public boolean execute(CommandSender sender, String label, String[] args) {
                    return exec.onCommand(sender, this, label, args);
                }

                @Override
                public List<String> tabComplete(CommandSender sender, String label, String[] args) {
                    List<String> r = exec.onTabComplete(sender, this, label, args);
                    return r == null ? Collections.<String>emptyList() : r;
                }
            };
            c.setPermission(permission);
            if (!map.register("disklens", c)) taken.add(alias);
            registered.add(c);
        }
        return taken;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Command> known(CommandMap map) {
        try {
            Method m = map.getClass().getMethod("getKnownCommands");
            return (Map<String, Command>) m.invoke(map);
        } catch (ReflectiveOperationException ignored) {
            // older Spigot: protected field on SimpleCommandMap
        }
        for (Class<?> c = map.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField("knownCommands");
                f.setAccessible(true);
                return (Map<String, Command>) f.get(map);
            } catch (NoSuchFieldException ignored) {
                // keep looking up the hierarchy
            } catch (IllegalAccessException e) {
                return null;
            }
        }
        return null;
    }

    /** Removes map entries whose command matches; by key, since Paper's brigadier-backed map has no iterator remove. */
    private static void removeWhere(Map<String, Command> known, Predicate test, Logger log) {
        List<String> keys = new ArrayList<String>();
        for (Map.Entry<String, Command> e : new ArrayList<Map.Entry<String, Command>>(known.entrySet())) {
            if (test.matches(e.getValue())) keys.add(e.getKey());
        }
        for (String k : keys) {
            try {
                known.remove(k);
            } catch (RuntimeException ex) {
                if (log != null) log.warning("Could not remove command /" + k + ": " + ex);
            }
        }
    }

    private interface Predicate {
        boolean matches(Command c);
    }

    /** Leftover aliases of a previous DiskLens instance (e.g. after a failed unload) would block the name. */
    private static boolean isStaleDiskLens(Command c) {
        return c != null && c.getClass().getName().startsWith("dev.disklens.") && "DiskLens alias".equals(c.getDescription());
    }

    void unregister(Logger log) {
        if (map == null || registered.isEmpty()) return;
        try {
            Map<String, Command> known = known(map);
            if (known != null) {
                removeWhere(known, new Predicate() {
                    @Override
                    public boolean matches(Command c) {
                        return registered.contains(c);
                    }
                }, log);
            }
            for (Command c : registered) c.unregister(map);
        } catch (RuntimeException e) {
            if (log != null) log.warning("Could not unregister aliases: " + e);
        } finally {
            registered.clear();
            map = null;
        }
    }
}
