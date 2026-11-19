package dev.disklens.platform;

import dev.disklens.text.Line;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Sends {@link Line}s to players as BungeeCord chat components (clickable, works on Spigot, Paper, Purpur, Folia
 * 1.16.5+), and to everyone else as plain text without colour codes.
 */
public final class ChatOut {
    private ChatOut() {
    }

    private static ChatColor color(Line.Tone t) {
        switch (t) {
            case GRAY: return ChatColor.GRAY;
            case DARK_GRAY: return ChatColor.DARK_GRAY;
            case AQUA: return ChatColor.AQUA;
            case GOLD: return ChatColor.GOLD;
            case YELLOW: return ChatColor.YELLOW;
            case GREEN: return ChatColor.GREEN;
            case RED: return ChatColor.RED;
            default: return ChatColor.WHITE;
        }
    }

    @SuppressWarnings("deprecation") // HoverEvent(Action, BaseComponent[]) is the variant every version supports
    static BaseComponent[] components(Line line) {
        BaseComponent[] out = new BaseComponent[line.segments.size()];
        for (int i = 0; i < out.length; i++) {
            Line.Segment s = line.segments.get(i);
            TextComponent c = new TextComponent(s.text);
            c.setColor(color(s.tone));
            c.setBold(s.bold);
            c.setItalic(s.italic);
            if (s.runCommand != null) c.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, s.runCommand));
            if (s.copyText != null) c.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, s.copyText));
            if (s.hover != null) {
                c.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(s.hover).create()));
            }
            out[i] = c;
        }
        return out;
    }

    /** Must be called on a thread allowed to talk to this sender (see {@link Platform#forPlayer}). */
    public static void send(CommandSender to, List<Line> lines) {
        if (to instanceof Player) {
            Player p = (Player) to;
            for (Line l : lines) p.spigot().sendMessage(components(l));
        } else {
            for (Line l : lines) to.sendMessage(l.plain());
        }
    }

    public static void send(CommandSender to, Line line) {
        if (to instanceof Player) {
            ((Player) to).spigot().sendMessage(components(line));
        } else {
            to.sendMessage(line.plain());
        }
    }

    public static void actionBar(Player p, String text) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(text));
    }
}
