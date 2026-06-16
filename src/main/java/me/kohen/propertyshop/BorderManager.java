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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Draws a per-player, client-side yellow/black border 1 block outside a property's
 * outer edge, on the top surface block only. Nothing real is placed, so it can't be
 * broken and never affects other players' builds. Cleared the moment the player leaves.
 */
public class BorderManager {
    private final PropertyShop plugin;
    private final Map<UUID, List<Location>> shown = new HashMap<>();
    private final Map<UUID, String> shownProp = new HashMap<>();

    public BorderManager(PropertyShop plugin) { this.plugin = plugin; }

    public String shownProperty(Player p) { return shownProp.get(p.getUniqueId()); }

    private Material mat(String path, Material def) {
        Material m = Material.matchMaterial(plugin.getConfig().getString(path, def.name()));
        return m == null ? def : m;
    }

    public void show(Player p, Property prop) {
        World w = Bukkit.getWorld(prop.getWorld());
        if (w == null) return;
        Set<String> cs = prop.getChunks();
        BlockData yellow = mat("border.yellow-block", Material.YELLOW_CONCRETE).createBlockData();
        BlockData black = mat("border.black-block", Material.BLACK_CONCRETE).createBlockData();

        List<Location> out = new ArrayList<>();
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

            if (north) for (int x = bx; x < bx + 16; x++) place(p, w, x, bz - 1, yellow, black, seen, out);
            if (south) for (int x = bx; x < bx + 16; x++) place(p, w, x, bz + 16, yellow, black, seen, out);
            if (west) for (int z = bz; z < bz + 16; z++) place(p, w, bx - 1, z, yellow, black, seen, out);
            if (east) for (int z = bz; z < bz + 16; z++) place(p, w, bx + 16, z, yellow, black, seen, out);
            if (north && west) place(p, w, bx - 1, bz - 1, yellow, black, seen, out);
            if (north && east) place(p, w, bx + 16, bz - 1, yellow, black, seen, out);
            if (south && west) place(p, w, bx - 1, bz + 16, yellow, black, seen, out);
            if (south && east) place(p, w, bx + 16, bz + 16, yellow, black, seen, out);
        }
        shown.put(p.getUniqueId(), out);
        shownProp.put(p.getUniqueId(), prop.getName());
    }

    private void place(Player p, World w, int x, int z, BlockData yellow, BlockData black,
                       Set<String> seen, List<Location> out) {
        String k = x + "," + z;
        if (!seen.add(k)) return;
        int y = w.getHighestBlockYAt(x, z);
        Location loc = new Location(w, x, y, z);
        BlockData data = (((x + z) & 1) == 0) ? yellow : black;
        p.sendBlockChange(loc, data);
        out.add(loc);
    }

    public void clear(Player p) {
        List<Location> locs = shown.remove(p.getUniqueId());
        shownProp.remove(p.getUniqueId());
        if (locs == null || !p.isOnline()) return;
        for (Location loc : locs) {
            p.sendBlockChange(loc, loc.getBlock().getBlockData()); // restore the real block view
        }
    }

    public void clearAll() {
        for (UUID id : new ArrayList<>(shown.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) clear(p);
        }
        shown.clear();
        shownProp.clear();
    }
}
