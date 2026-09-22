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

    /**
     * The built-in preset that guards nothing. It can't be edited, renamed or
     * deleted, so switching to it is always the way to stand the mod down.
     */
    public static final String NONE = "none";

    /**
     * Show an actionbar warning when an input is blocked. Named apart from 3.0's
     * {@code showMessages}, which defaulted to on, so every existing config picks
     * up the new default of off.
     */
    public boolean showWarnings = false;

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
     * Set once the Crystal PvP preset has been created. Without this we would
     * recreate it on every load, so deleting it in the GUI would never stick.
     * None needs no latch: it can't be deleted.
     */
    public boolean seededDefaults = false;

    public Map<String, Preset> presets = new LinkedHashMap<>();

    /**
     * The master switch from 3.0, read once so a config that had it off lands on
     * None rather than suddenly guarding again. Null - and so left out of the
     * file - from then on.
     */
    private Boolean enabled;

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
                    // Writes back any migration, so it only ever runs once.
                    cfg.save();
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

        // None always exists, always comes first, and never holds rules. It used
        // to be an ordinary preset, so any rules someone gave it move out into a
        // preset of their own instead of being thrown away.
        Preset oldNone = presets.get(NONE);
        boolean hadRules = oldNone != null && oldNone.rules != null && !oldNone.rules.isEmpty();
        String salvaged = hadRules ? uniqueKey("custom") : null;

        Map<String, Preset> ordered = new LinkedHashMap<>();
        ordered.put(NONE, new Preset());
        for (Map.Entry<String, Preset> e : presets.entrySet()) {
            if (!e.getKey().equals(NONE)) {
                ordered.put(e.getKey(), e.getValue());
            } else if (hadRules) {
                ordered.put(salvaged, e.getValue());
            }
        }
        presets = ordered;
        if (hadRules && NONE.equals(activePreset)) activePreset = salvaged;

        if (enabled != null) {
            if (!enabled) activePreset = NONE;
            enabled = null;
        }

        if (activePreset == null || !presets.containsKey(activePreset)) {
            activePreset = presets.keySet().iterator().next();
        }
    }

    public static boolean isNone(String key) {
        return NONE.equals(key);
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

    /** Makes {@code key} the active preset, if it exists. */
    public void selectPreset(String key) {
        if (!presets.containsKey(key) || key.equals(activePreset)) return;
        activePreset = key;
        save();
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

    /** Removes a preset. Refuses to remove None, or the last one. */
    public boolean removePreset(String key) {
        if (isNone(key) || presets.size() <= 1 || !presets.containsKey(key)) return false;
        presets.remove(key);
        if (!presets.containsKey(activePreset)) {
            activePreset = presets.keySet().iterator().next();
        }
        save();
        return true;
    }

    /**
     * Renames a preset in place, keeping its position in the list. A blank name
     * is ignored and a colliding name is made unique rather than rejected. None
     * can't be renamed, and no other preset can take its name.
     *
     * @return the key the preset ended up with
     */
    public String renamePreset(String oldKey, String rawName) {
        String cleaned = rawName == null ? "" : rawName.trim();
        if (cleaned.isEmpty() || isNone(oldKey) || !presets.containsKey(oldKey) || cleaned.equals(oldKey)) {
            return oldKey;
        }

        String newKey = cleaned;
        int n = 2;
        while (presets.containsKey(newKey) || newKey.equalsIgnoreCase(NONE)) newKey = cleaned + "_" + (n++);

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
