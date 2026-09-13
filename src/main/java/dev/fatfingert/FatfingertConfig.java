package dev.fatfingert;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Config file: .minecraft/config/fatfingert.json
 *
 * Slot keys: "offhand", "hotbar0" ... "hotbar8"
 * Rules map a slot key to a list of item ids that are ALLOWED in that slot.
 * A slot with no rule accepts anything (vanilla behavior).
 */
public class FatfingertConfig {

    public boolean enabled = true;

    /** Show an actionbar message when an input is blocked. */
    public boolean showMessages = true;

    /**
     * If true, a shift-click (quick-move) is blocked when vanilla would land the
     * item inside a reserved slot that doesn't allow it. Shift-clicks that move
     * items out - off the hotbar, or into a chest - are never blocked.
     */
    public boolean strictShiftClick = true;

    /** Show the Fatfingert button on the inventory screen. */
    public boolean showInventoryButton = true;

    public String activePreset = "crystal_pvp";

    /**
     * Set once the built-in presets have been created. Without this we would
     * recreate "none" and "crystal_pvp" on every load, so deleting them in the
     * GUI would never stick.
     */
    public boolean seededDefaults = false;

    public Map<String, Preset> presets = new LinkedHashMap<>();

    public static class Preset {
        /**
         * If true, you also cannot take an allowed item back OUT of a reserved
         * slot (swap it away, drop it, or pick it up) unless the replacement is
         * also allowed. A wrong item can always be taken out. Off by default so
         * you can still manage your inventory.
         */
        public boolean blockEmptying = false;

        /** slot key -> list of allowed item ids (e.g. "minecraft:totem_of_undying") */
        public Map<String, List<String>> rules = new LinkedHashMap<>();
    }

    // ------------------------------------------------------------------

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("fatfingert.json");
    }

    public static FatfingertConfig load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                FatfingertConfig cfg = GSON.fromJson(Files.readString(p), FatfingertConfig.class);
                if (cfg != null) {
                    cfg.ensureDefaults();
                    return cfg;
                }
            }
        } catch (Exception e) {
            System.err.println("[Fatfingert] Failed to read config, using defaults: " + e);
        }
        FatfingertConfig cfg = new FatfingertConfig();
        cfg.ensureDefaults();
        cfg.save();
        return cfg;
    }

    public void ensureDefaults() {
        if (presets == null) presets = new LinkedHashMap<>();

        if (!seededDefaults) {
            // A preset with no rules = vanilla behavior.
            presets.computeIfAbsent("none", k -> new Preset());

            // Crystal PvP: offhand is reserved for totems only.
            presets.computeIfAbsent("crystal_pvp", k -> {
                Preset p = new Preset();
                List<String> offhand = new ArrayList<>();
                offhand.add("minecraft:totem_of_undying");
                p.rules.put("offhand", offhand);
                return p;
            });
            seededDefaults = true;
        }

        // There must always be somewhere for the rules to live.
        if (presets.isEmpty()) presets.put("none", new Preset());

        if (activePreset == null || !presets.containsKey(activePreset)) {
            activePreset = presets.keySet().iterator().next();
        }
    }

    // ---- preset management -------------------------------------------

    /** A key based on {@code base} that no existing preset is using. */
    public String uniqueKey(String base) {
        String key = base;
        int n = 2;
        while (presets.containsKey(key)) key = base + "_" + (n++);
        return key;
    }

    /** Creates an empty preset, makes it active, and returns its key. */
    public String addPreset() {
        String key = uniqueKey("new_preset");
        presets.put(key, new Preset());
        activePreset = key;
        save();
        return key;
    }

    /** Steps the active preset forward or back through the list, wrapping around. */
    public String cyclePreset(int direction) {
        List<String> names = new ArrayList<>(presets.keySet());
        if (names.isEmpty()) return activePreset;
        int idx = names.indexOf(activePreset);
        activePreset = names.get(Math.floorMod(idx + direction, names.size()));
        save();
        return activePreset;
    }

    /** Removes a preset. Refuses to remove the last one. */
    public boolean removePreset(String key) {
        if (presets.size() <= 1 || !presets.containsKey(key)) return false;
        presets.remove(key);
        if (!presets.containsKey(activePreset)) {
            activePreset = presets.keySet().iterator().next();
        }
        save();
        return true;
    }

    /**
     * Renames a preset in place, keeping its position in the list. A blank name
     * is ignored and a colliding name is made unique rather than rejected.
     *
     * @return the key the preset ended up with
     */
    public String renamePreset(String oldKey, String rawName) {
        String cleaned = rawName == null ? "" : rawName.trim();
        if (cleaned.isEmpty() || !presets.containsKey(oldKey) || cleaned.equals(oldKey)) {
            return oldKey;
        }

        String newKey = cleaned;
        int n = 2;
        while (presets.containsKey(newKey)) newKey = cleaned + "_" + (n++);

        Map<String, Preset> rebuilt = new LinkedHashMap<>();
        for (Map.Entry<String, Preset> e : presets.entrySet()) {
            rebuilt.put(e.getKey().equals(oldKey) ? newKey : e.getKey(), e.getValue());
        }
        presets = rebuilt;
        if (oldKey.equals(activePreset)) activePreset = newKey;
        save();
        return newKey;
    }

    public void save() {
        try {
            Files.createDirectories(path().getParent());
            Files.writeString(path(), GSON.toJson(this));
        } catch (IOException e) {
            System.err.println("[Fatfingert] Failed to save config: " + e);
        }
    }
}
