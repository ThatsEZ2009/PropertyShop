package me.kohen.propertyshop;

import org.bukkit.Chunk;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Tracks the set of chunks each player has clicked with the wand. */
public class SelectionManager {
    private final PropertyShop plugin;
    private final Map<UUID, Data> selections = new HashMap<>();

    public SelectionManager(PropertyShop plugin) { this.plugin = plugin; }

    private static class Data {
        String world;
        final LinkedHashSet<String> chunks = new LinkedHashSet<>();
    }

    /** Add the clicked chunk. Returns the new total, or -1 if the cap was hit. */
    public int add(Player p, Chunk c) {
        Data d = selections.computeIfAbsent(p.getUniqueId(), k -> new Data());
        // If they switch worlds, start the selection over.
        if (d.world != null && !d.world.equals(c.getWorld().getName())) d.chunks.clear();
        d.world = c.getWorld().getName();
        int cap = plugin.getConfig().getInt("max-selection-chunks", 256);
        if (d.chunks.size() >= cap && !d.chunks.contains(c.getX() + "," + c.getZ())) return -1;
        d.chunks.add(c.getX() + "," + c.getZ());
        return d.chunks.size();
    }

    /** Remove the clicked chunk. Returns the new total. */
    public int remove(Player p, Chunk c) {
        Data d = selections.get(p.getUniqueId());
        if (d == null) return 0;
        d.chunks.remove(c.getX() + "," + c.getZ());
        return d.chunks.size();
    }

    public boolean has(Player p) {
        Data d = selections.get(p.getUniqueId());
        return d != null && !d.chunks.isEmpty();
    }

    public int size(Player p) {
        Data d = selections.get(p.getUniqueId());
        return d == null ? 0 : d.chunks.size();
    }

    public String world(Player p) {
        Data d = selections.get(p.getUniqueId());
        return d == null ? null : d.world;
    }

    public List<String> chunks(Player p) {
        Data d = selections.get(p.getUniqueId());
        return d == null ? new ArrayList<>() : new ArrayList<>(d.chunks);
    }

    public void clear(Player p) { selections.remove(p.getUniqueId()); }
}
