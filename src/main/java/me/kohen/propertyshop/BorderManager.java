package me.kohen.propertyshop;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-player, client-side rings. Owner plots = yellow/black just OUTSIDE the edge (shown
 * when inside). For-sale plots = green just INSIDE the edge, shown to everyone in range.
 * Fake blocks are re-sent periodically so Bedrock/Geyser clients that load a chunk late
 * still receive them (that's why the green ring wasn't showing on Bedrock).
 */
public class BorderManager {
    private final PropertyShop plugin;
    private final Map<UUID, Map<String, Shown>> shown = new HashMap<>();

    public BorderManager(PropertyShop plugin) { this.plugin = plugin; }

    private static class Fake {
        final Location loc; final BlockData data;
        Fake(Location loc, BlockData data) { this.loc = loc; this.data = data; }
    }
    private static class Shown {
        final boolean forSale; final List<Fake> blocks;
        Shown(boolean forSale, List<Fake> blocks) { this.forSale = forSale; this.blocks = blocks; }
    }

    private Material mat(String path, Material def) {
        Material m = Material.matchMaterial(plugin.getConfig().getString(path, def.name()));
        return m == null ? def : m;
    }

    public void reconcile(Player p, Map<String, Boolean> desired) {
        Map<String, Shown> cur = shown.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
        Iterator<Map.Entry<String, Shown>> it = cur.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Shown> e = it.next();
            Boolean want = desired.get(e.getKey());
            if (want == null || want != e.getValue().forSale) {
                restore(p, e.getValue().blocks);
                it.remove();
            }
        }
        for (Map.Entry<String, Boolean> e : desired.entrySet()) {
            if (cur.containsKey(e.getKey())) continue;
            Property prop = plugin.getManager().get(e.getKey());
            if (prop == null) continue;
            List<Fake> blocks = draw(p, prop, e.getValue());
            cur.put(e.getKey(), new Shown(e.getValue(), blocks));
        }
    }

    /** Re-push currently shown blocks so late-loading (Bedrock) clients receive them. */
    public void resend(Player p) {
        Map<String, Shown> cur = shown.get(p.getUniqueId());
        if (cur == null) return;
        for (Shown s : cur.values())
            for (Fake f : s.blocks) p.sendBlockChange(f.loc, f.data);
    }

    private List<Fake> draw(Player p, Property prop, boolean forSale) {
        List<Fake> out = new ArrayList<>();
        World w = Bukkit.getWorld(prop.getWorld());
        if (w == null) return out;
        BlockData a = (forSale ? mat("border.for-sale.block-a", Material.LIME_CONCRETE)
                : mat("border.owned.block-a", Material.YELLOW_CONCRETE)).createBlockData();
        BlockData b = (forSale ? mat("border.for-sale.block-b", Material.GREEN_CONCRETE)
                : mat("border.owned.block-b", Material.BLACK_CONCRETE)).createBlockData();

        Set<String> cs = prop.getChunks();
        Set<String> seen = new HashSet<>();
        for (String key : cs) {
            String[] pa = key.split(",");
            int cx, cz;
            try { cx = Integer.parseInt(pa[0]); cz = Integer.parseInt(pa[1]); } catch (NumberFormatException e) { continue; }
            int bx = cx << 4, bz = cz << 4;
            boolean north = !cs.contains(cx + "," + (cz - 1));
            boolean south = !cs.contains(cx + "," + (cz + 1));
            boolean west = !cs.contains((cx - 1) + "," + cz);
            boolean east = !cs.contains((cx + 1) + "," + cz);

            if (forSale) { // just INSIDE the plot edge
                if (north) for (int x = bx; x < bx + 16; x++) place(p, w, x, bz, a, b, seen, out);
                if (south) for (int x = bx; x < bx + 16; x++) place(p, w, x, bz + 15, a, b, seen, out);
                if (west) for (int z = bz; z < bz + 16; z++) place(p, w, bx, z, a, b, seen, out);
                if (east) for (int z = bz; z < bz + 16; z++) place(p, w, bx + 15, z, a, b, seen, out);
            } else { // just OUTSIDE the plot edge
                if (north) for (int x = bx; x < bx + 16; x++) place(p, w, x, bz - 1, a, b, seen, out);
                if (south) for (int x = bx; x < bx + 16; x++) place(p, w, x, bz + 16, a, b, seen, out);
                if (west) for (int z = bz; z < bz + 16; z++) place(p, w, bx - 1, z, a, b, seen, out);
                if (east) for (int z = bz; z < bz + 16; z++) place(p, w, bx + 16, z, a, b, seen, out);
                if (north && west) place(p, w, bx - 1, bz - 1, a, b, seen, out);
                if (north && east) place(p, w, bx + 16, bz - 1, a, b, seen, out);
                if (south && west) place(p, w, bx - 1, bz + 16, a, b, seen, out);
                if (south && east) place(p, w, bx + 16, bz + 16, a, b, seen, out);
            }
        }
        return out;
    }

    private void place(Player p, World w, int x, int z, BlockData a, BlockData b, Set<String> seen, List<Fake> out) {
        String k = x + "," + z;
        if (!seen.add(k)) return;
        int y = w.getHighestBlockYAt(x, z);
        Location loc = new Location(w, x, y, z);
        BlockData data = (((x + z) & 1) == 0) ? a : b;
        p.sendBlockChange(loc, data);
        out.add(new Fake(loc, data));
    }

    private void restore(Player p, List<Fake> blocks) {
        if (!p.isOnline()) return;
        for (Fake f : blocks) p.sendBlockChange(f.loc, f.loc.getBlock().getBlockData());
    }

    public void forget(UUID id) { shown.remove(id); }

    public void clearAll() {
        for (Map.Entry<UUID, Map<String, Shown>> e : shown.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) continue;
            for (Shown s : e.getValue().values()) restore(p, s.blocks);
        }
        shown.clear();
    }
}
