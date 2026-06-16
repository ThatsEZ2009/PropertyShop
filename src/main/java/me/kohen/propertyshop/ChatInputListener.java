package me.kohen.propertyshop;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.UUID;

/** Captures the next chat line when a player is setting a plot title/description. */
public class ChatInputListener implements Listener {
    private final PropertyShop plugin;

    public ChatInputListener(PropertyShop plugin) { this.plugin = plugin; }

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        String[] pend = plugin.textInput.remove(id);
        if (pend == null) return;
        e.setCancelled(true); // don't broadcast the typed title/description
        String msg = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> plugin.applyTextInput(p, pend, msg));
    }
}
