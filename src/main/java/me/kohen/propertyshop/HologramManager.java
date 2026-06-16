package me.kohen.propertyshop;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Floating "FOR SALE / N chunks / price" text in the middle of each for-sale plot. */
public class HologramManager {
    private static final String TAG = "propshop_hg";
    private final PropertyShop plugin;
    private final Map<String, UUID> holos = new HashMap<>(); // lowercase name -> entity

    public HologramManager(PropertyShop plugin) { this.plugin = plugin; }

    /** Remove any stray holograms left in worlds (e.g. after a crash), then start fresh. */
    public void cleanupStray() {
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (e.getScoreboardTags().contains(TAG)) e.remove();
            }
        }
        holos.clear();
    }

    public void refreshAll() {
        Set<String> desired = new HashSet<>();
        for (Property prop : plugin.getManager().all()) {
            if (!prop.isOwned() && prop.hasPrice()) desired.add(prop.getName().toLowerCase());
        }
        for (String name : new ArrayList<>(holos.keySet())) {
            if (!desired.contains(name)) remove(name);
        }
        for (Property prop : plugin.getManager().all()) {
            if (!prop.isOwned() && prop.hasPrice()) ensure(prop);
        }
    }

    private void ensure(Property prop) {
        Location loc = center(prop);
        if (loc == null) return; // world or center chunk not loaded yet
        UUID id = holos.get(prop.getName().toLowerCase());
        Entity e = (id == null) ? null : Bukkit.getEntity(id);
        if (e instanceof TextDisplay td && !td.isDead()) {
            td.text(text(prop));
            td.teleport(loc);
        } else {
            World w = loc.getWorld();
            TextDisplay td = w.spawn(loc, TextDisplay.class);
            td.text(text(prop));
            td.setBillboard(Display.Billboard.CENTER);
            td.addScoreboardTag(TAG);
            holos.put(prop.getName().toLowerCase(), td.getUniqueId());
        }
    }

    private void remove(String key) {
        UUID id = holos.remove(key);
        if (id == null) return;
        Entity e = Bukkit.getEntity(id);
        if (e != null) e.remove();
    }

    public void removeAll() {
        for (UUID id : holos.values()) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        holos.clear();
    }

    private net.kyori.adventure.text.Component text(Property prop) {
        int n = prop.getChunks().size();
        String s = "&a&lFOR SALE\n&f" + n + " chunk" + (n == 1 ? "" : "s") + "\n&7" + prop.priceString();
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s);
    }

    private Location center(Property prop) {
        World w = Bukkit.getWorld(prop.getWorld());
        if (w == null || prop.getChunks().isEmpty()) return null;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (String key : prop.getChunks()) {
            String[] pa = key.split(",");
            try {
                int cx = Integer.parseInt(pa[0]), cz = Integer.parseInt(pa[1]);
                minX = Math.min(minX, cx); maxX = Math.max(maxX, cx);
                minZ = Math.min(minZ, cz); maxZ = Math.max(maxZ, cz);
            } catch (NumberFormatException ignored) {}
        }
        if (minX == Integer.MAX_VALUE) return null;
        int ccx = (minX + maxX) / 2, ccz = (minZ + maxZ) / 2;
        if (!w.isChunkLoaded(ccx, ccz)) return null;
        int bx = (ccx << 4) + 8, bz = (ccz << 4) + 8;
        int y = w.getHighestBlockYAt(bx, bz) + 2;
        return new Location(w, bx + 0.5, y, bz + 0.5);
    }
}
