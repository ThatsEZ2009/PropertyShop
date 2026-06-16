package me.kohen.propertyshop;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * While an op holds the wand, nearby chunks are shaded with client-side concrete:
 * RED = claimed, BLUE = free, LIME = your current selection. Fake blocks (sent only
 * to that player), cleared when they put the wand away or walk off.
 */
public class WandMapManager {
    private final PropertyShop plugin;
    private final Map<UUID, Map<String, Cell>> shown = new HashMap<>();

    public WandMapManager(PropertyShop plugin) { this.plugin = plugin; }

    private enum Col { RED, BLUE, GREEN }
    private static class Fake { final Location loc; final BlockData data; Fake(Location l, BlockData d) { loc = l; data = d; } }
    private static class Cell { final Col col; final List<Fake> blocks; Cell(Col c, List<Fake> b) { col = c; blocks = b; } }

    public void update(Player p) {
        boolean active = plugin.isWand(p.getInventory().getItemInMainHand())
                && p.hasPermission("propertyshop.admin");
        if (!active) { clear(p); return; }

        World w = p.getWorld();
        String world = w.getName();
        Chunk here = p.getLocation().getChunk();
        int r = plugin.getConfig().getInt("wand-map.radius", 2);

        Map<String, Col> desired = new HashMap<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int ccx = here.getX() + dx, ccz = here.getZ() + dz;
                String key = ccx + "," + ccz;
                boolean claimed = plugin.getManager().ownerOfChunk(world, key) != null;
                desired.put(key, claimed ? Col.RED : Col.BLUE);
            }
        }
        for (String key : plugin.getSelection().chunks(p)) desired.put(key, Col.GREEN);

        Map<String, Cell> cur = shown.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
        Iterator<Map.Entry<String, Cell>> it = cur.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Cell> e = it.next();
            Col want = desired.get(e.getKey());
            if (want == null || want != e.getValue().col) { restore(p, e.getValue().blocks); it.remove(); }
        }
        for (Map.Entry<String, Col> e : desired.entrySet()) {
            if (!cur.containsKey(e.getKey())) cur.put(e.getKey(), fill(p, w, e.getKey(), e.getValue()));
        }
    }

    private Cell fill(Player p, World w, String key, Col col) {
        List<Fake> out = new ArrayList<>();
        String[] pa = key.split(",");
        int cx, cz;
        try { cx = Integer.parseInt(pa[0]); cz = Integer.parseInt(pa[1]); } catch (NumberFormatException e) { return new Cell(col, out); }
        Material m = col == Col.RED ? Material.RED_CONCRETE : col == Col.BLUE ? Material.BLUE_CONCRETE : Material.LIME_CONCRETE;
        BlockData data = m.createBlockData();
        int bx = cx << 4, bz = cz << 4;
        for (int x = bx; x < bx + 16; x++) {
            for (int z = bz; z < bz + 16; z++) {
                int y = w.getHighestBlockYAt(x, z);
                Location loc = new Location(w, x, y, z);
                p.sendBlockChange(loc, data);
                out.add(new Fake(loc, data));
            }
        }
        return new Cell(col, out);
    }

    public void resend(Player p) {
        Map<String, Cell> cur = shown.get(p.getUniqueId());
        if (cur == null) return;
        for (Cell c : cur.values()) for (Fake f : c.blocks) p.sendBlockChange(f.loc, f.data);
    }

    private void restore(Player p, List<Fake> blocks) {
        if (!p.isOnline()) return;
        for (Fake f : blocks) p.sendBlockChange(f.loc, f.loc.getBlock().getBlockData());
    }

    public void clear(Player p) {
        Map<String, Cell> cur = shown.remove(p.getUniqueId());
        if (cur == null || !p.isOnline()) return;
        for (Cell c : cur.values()) restore(p, c.blocks);
    }

    public void clearAll() {
        for (UUID id : new ArrayList<>(shown.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) clear(p);
        }
        shown.clear();
    }
}
