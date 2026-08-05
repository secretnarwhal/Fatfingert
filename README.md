# Fatfingert

**Misinput insurance for your hotbar.**

A client-side Fabric mod for **Minecraft 26.1.2** that reserves inventory slots (offhand and hotbar) for specific items, so a fat-fingered key press or click can't wreck your loadout mid-fight.

It contains **zero automation**. Every mixin in this mod only ever *cancels* an input the player already generated — a key press, a click — before it turns into a packet. Nothing here presses keys, moves items, or talks to the server on the player's behalf, so it does not function as a cheat and should not trip anticheat that watches for injected inputs.

Made by **AlmondsMilk**.

---

## Table of contents

- [Architecture](#architecture)
- [The rule model](#the-rule-model)
- [Where the guarding actually happens](#where-the-guarding-actually-happens)
- [The config file](#the-config-file)
- [Preset management](#preset-management)
- [The UI layer](#the-ui-layer)
- [The keycap rendering primitive](#the-keycap-rendering-primitive)
- [Mod Menu integration](#mod-menu-integration)
- [Build system](#build-system)
- [Project layout](#project-layout)
- [In-game usage](#in-game-usage)
- [Known limits](#known-limits-by-design)
- [Extending it](#extending-it)

---

## Architecture

Fatfingert is a `client`-only Fabric mod — it has no server-side entrypoint and `fabric.mod.json` declares `"environment": "client"`, so a server running it (or a client without it) sees nothing unusual. All state lives in a single JSON config file; there is no networking, no server component, and no persistent world data.

The mod is built around three layers:

1. **Rule evaluation** (`Fatfingert.java`) — pure functions that answer "is this item allowed here?" against the active preset. No side effects, no rendering, no Minecraft-input awareness.
2. **Input interception** (the three `mixin/` classes) — the only place the mod touches vanilla behavior. Each mixin injects at the earliest point vanilla would otherwise act on a player-generated input, checks the rule layer, and cancels if it disagrees.
3. **Configuration UI** (`gui/`) — a custom-drawn screen tree that reads and writes `FatfingertConfig` directly. Nothing in the UI layer touches gameplay; it only edits the same config object the rule layer reads.

This separation matters for auditability: everything that can affect gameplay routes through `Fatfingert.clickBlockReason()`, `handSwapBlockReason()`, or `dropBlockReason()`, all three of which return either `null` (do nothing) or a `String` (block and show this message). None of them have a code path that *acts* — only ones that *veto*.

## The rule model

A **preset** is a named set of rules. A **rule** maps a slot key to a list of allowed item ids:

```java
public static class Preset {
    public boolean blockEmptying = false;
    public Map<String, List<String>> rules = new LinkedHashMap<>();
}
```

Slot keys are `"offhand"` and `"hotbar0"` through `"hotbar8"` (`hotbar0` is the leftmost hotbar slot, matching `Inventory.getSelectedSlot()`'s indexing). A slot with **no entry** in `rules` is unguarded — vanilla behavior, anything goes. A slot with an **empty list** is treated the same as no entry (`Fatfingert.ruleFor()` normalizes `[]` to `null`) so a UI bug that leaves a dangling empty list can't accidentally brick a slot shut.

Only one preset is active at a time (`FatfingertConfig.activePreset`), and every rule lookup goes through `Fatfingert.activePreset()`, which falls back to an empty `Preset` if the active key somehow doesn't resolve — so a corrupt or mid-edit config degrades to "nothing is guarded" rather than throwing.

```java
public static boolean isAllowed(String slotKey, ItemStack incoming) {
    List<String> rule = ruleFor(slotKey);
    if (rule == null) return true;                        // unguarded
    if (incoming.isEmpty()) return !activePreset().blockEmptying;
    return rule.contains(itemId(incoming));
}
```

Item identity is the registry id string (`"minecraft:totem_of_undying"`), not the `Item` reference, so rules survive across game restarts and are hand-editable in the JSON.

### `blockEmptying`

Per-preset, off by default. When on, a guarded slot also can't be *emptied* — no dropping, no swapping the item out, no shift-clicking it away — unless the replacement is itself an allowed item. This is a second, independent gate; `isAllowed()` handles "can this item enter", while `dropBlockReason()` and the `THROW`/`QUICK_MOVE` branches of `clickBlockReason()` handle "can this slot be emptied".

## Where the guarding actually happens

Three mixins, each targeting the narrowest vanilla method that still sees every relevant input:

### `MinecraftClientMixin` → `Minecraft.handleKeybinds()` (`@At("HEAD")`)

Runs once a client tick, before vanilla consumes any queued key clicks. It **peeks** at whether the pending F-key (swap-to-offhand) or Q-key (drop) press would violate a rule, and if so, drains the key's click queue (`consumeClick()` in a `while` loop, since a key can queue multiple presses per tick) without letting vanilla see it. The key is confirmed pressed *before* the notification fires, so a background repeat-key spam doesn't spam the block message — `notifyBlocked` is separately rate-limited to one message per 400ms regardless.

### `ClientPlayerInteractionManagerMixin` → `MultiPlayerGameMode.handleContainerInput()` (`@At("HEAD")`, cancellable)

The single funnel for every inventory-screen interaction: clicks, shift-clicks (`QUICK_MOVE`), number-key hotbar swaps (`SWAP`), drag-splitting (`QUICK_CRAFT`), F-swap while hovering a slot, and throws (`THROW`/`PICKUP_ALL` variants). `Fatfingert.clickBlockReason()` switches on the vanilla `ContainerInput` action type and inspects whichever slots that action touches; if it returns non-null, `ci.cancel()` stops vanilla from ever sending the packet.

This is also where **strict shift-click** lives. Vanilla's own shift-click destination logic is not exposed to mixins in a reusable form, so rather than re-implementing Minecraft's slot-fill algorithm, strict mode approximates it: if the source item isn't already known to be disallowed via its slot, but *some* guarded hotbar slot is both empty and doesn't allow it, the shift-click is blocked on the assumption vanilla might route the item there. This deliberately over-blocks in ambiguous cases — see [Known limits](#known-limits-by-design).

### `InventoryScreenMixin` → `InventoryScreen.init()` (`@At("TAIL")`)

Purely cosmetic. Adds the `ConfigLauncherButton` (the button drawn as the logo mark) to the vanilla inventory screen next to the recipe book button. No gameplay logic here at all.

All three mixins check `Fatfingert.isEnabled()` first and bail immediately if the master switch is off, so disabling the mod at runtime doesn't require restarting or reloading mixins — the injected code becomes a no-op.

## The config file

`config/fatfingert.json`, Gson pretty-printed, loaded lazily on first access via `Fatfingert.config()` and held in a static field for the client session. There's no live file-watching — the UI and the mixins share the same in-memory object, and every mutation calls `.save()` immediately, so the file is always a snapshot of the last edit rather than something that needs periodic flushing.

```json
{
  "enabled": true,
  "showMessages": true,
  "strictShiftClick": true,
  "activePreset": "crystal_pvp",
  "seededDefaults": true,
  "presets": {
    "none": { "blockEmptying": false, "rules": {} },
    "crystal_pvp": {
      "blockEmptying": false,
      "rules": { "offhand": ["minecraft:totem_of_undying"] }
    }
  }
}
```

`seededDefaults` (see below) exists purely to make the two built-in presets deletable — without it, `ensureDefaults()` would silently resurrect them on every load.

If the file is missing, corrupt, or fails to parse, `load()` catches the exception, logs it, and falls back to a fresh default config — it does not crash the client on a bad config file.

## Preset management

Presets are stored as a `LinkedHashMap<String, Preset>` keyed by an internal slug (`"crystal_pvp"`), with a separate display name derived on the fly by `Fatfingert.prettyPreset()` (`"crystal_pvp"` → `"Crystal PvP"`, everything else title-cased on underscores). The UI only ever shows the pretty form; the slug is what's persisted and is also visible in the rename button's tooltip for anyone editing the JSON by hand.

Three operations, all on `FatfingertConfig`:

- **`addPreset()`** — creates an empty preset under a unique key (`uniqueKey("new_preset")`, appending `_2`, `_3`, ... on collision), makes it active, and returns the key. The caller (`FatfingertConfigScreen.addPreset()`) immediately opens the rename box on it.
- **`removePreset(key)`** — refuses if it's the last remaining preset (`presets.size() <= 1`) or the key doesn't exist. If the removed preset was active, falls back to whatever preset happens to be first in iteration order.
- **`renamePreset(oldKey, rawName)`** — trims the input, ignores blank names, and de-duplicates collisions the same way `addPreset` does rather than rejecting them outright. Rebuilds the whole `LinkedHashMap` to preserve the preset's position in the list (Java's `Map` has no in-place key-rename), and flips `activePreset` to the new key if the renamed preset was active.

### Why `seededDefaults` exists

The original `ensureDefaults()` (inherited from SlotGuard) used `presets.computeIfAbsent("none", ...)` and the same for `"crystal_pvp"` on *every* config load — which is fine when presets can't be deleted, but once deletion existed, deleting either built-in preset would have had it silently reappear on the next game launch. `seededDefaults` is a one-time latch: the built-ins are created once, ever, and after that `ensureDefaults()` only guarantees the map isn't empty (falling back to a bare `"none"` preset if somehow all presets were removed — which the UI itself prevents, but hand-edited JSON could still produce).

### The rename UI's focus problem

This is the one non-obvious bug in the whole project, and it's worth documenting because it'll bite anyone adding another inline text field to a Minecraft `Screen`.

Vanilla's `ContainerEventHandler.mouseClicked()` (the default method every `Screen` inherits) does this after dispatching a click to whichever widget handled it:

```java
if (listener.mouseClicked(event, doubleClick) && listener.shouldTakeFocusAfterInteraction()) {
    this.setFocused(listener);
    ...
}
```

`shouldTakeFocusAfterInteraction()` defaults to `true` and neither `AbstractWidget` nor `EditBox` overrides it. So: clicking the preset name button runs `startRename()`, which calls `rebuildWidgets()`, which discards that button and creates an `EditBox` in its place — but then control returns to vanilla's `mouseClicked`, which calls `setFocused()` on the **now-discarded button reference**, stealing focus right back off the brand-new `EditBox`. The rename box would appear but never actually accept keystrokes, and `tick()`'s "commit on focus loss" check would fire immediately, closing the rename before the player could type anything.

The fix is a one-tick deferral: `startRename()` sets `focusNameBox = true` instead of calling `setFocused()` directly, and `tick()` claims focus on the *next* tick, after vanilla's post-click focus assignment has already happened and lost:

```java
@Override
public void tick() {
    super.tick();
    if (renaming && nameBox != null) {
        if (focusNameBox) {
            setFocused(nameBox);
            focusNameBox = false;
        } else if (!nameBox.isFocused()) {
            commitRename();   // clicked away — save what's typed
        }
    }
}
```

Renaming also commits on Enter, cancels on Escape (`keyPressed` override checking `GLFW_KEY_ENTER`/`GLFW_KEY_ESCAPE` before falling through to `super`), and commits automatically if the screen is closed mid-edit (`onClose()` calls `commitRename()` first).

## The UI layer

Every screen in `gui/` is drawn from primitives — no textures, no `.png`9-slices, nothing that could go missing or clash with a resource pack. `FatTheme` is the shared palette and drawing toolkit; `gui/widget/` holds the handful of `AbstractWidget` subclasses everything is built from.

### Why `extractBackground` vs `extractRenderState`

Minecraft 26.1's `Screen` splits rendering into two passes, and getting them backwards silently draws the card on top of its own buttons:

- **`extractBackground(...)`** runs once, before any widget renders. This is where the card, its header/footer bands, and the recessed "well" panels (preset row, keycap deck, allow-list) are painted — they need to be *behind* every widget.
- **`extractRenderState(...)`** is where `Screen`'s own implementation iterates `renderables` and calls each widget's `extractWidgetRenderState`. Text labels that sit *between* widgets (section headers, the "SLOTS" accent label, the footer credit) are drawn by overriding this method, calling `super.extractRenderState()` in the middle so labels can be layered both under and over the widget pass as needed.

This was confirmed against the actual bytecode of `Screen.extractRenderState()` and `Screen.extractBackground()` rather than assumed — `extractBackground` is a separate call Minecraft makes earlier in its own render sequence, not a step inside `extractRenderState`.

### Responsive layout, not fixed coordinates

`FatfingertConfigScreen.layout()` recomputes every coordinate from `this.width`/`this.height` on each `init()` (window resize triggers a fresh `init()` in vanilla). Two adaptive behaviors matter:

- **Keycap shrinking.** The row of 10 slot keys has a minimum comfortable width (30px each) but Minecraft allows GUI scale down to a 320×240 logical viewport. Rather than letting the row clip off the card, `capW` is computed as `min(30, max(16, availableWidth / 10))` — the caps shrink together, and item icons stop being drawn on caps narrower than 20px (an icon wouldn't be legible below that anyway).
- **Compact mode.** Below 300px of logical height, `layout()` switches to smaller header/footer bands and tighter gaps between sections (a `compact` boolean gates every spacing constant). This was tuned by rendering the exact layout math to a standalone offline preview (see below) at 320×240 and iterating until nothing overflowed the card — including a single-pixel overflow that only showed up once every other gap had been tightened.

### Offline layout verification

Because iterating against a live Minecraft client is slow (asset downloads, JVM startup, manual navigation to the screen), the layout math and `FatTheme` drawing primitives were mirrored 1:1 into a standalone `java.awt.Graphics2D` harness during development, rendering the screens to PNG at several logical resolutions (640×360, 960×540, 320×240) without touching Minecraft at all. This caught real layout bugs — the keycap row exceeding the card width at minimum GUI scale, the header subtitle running under the Armed toggle, a footer hint overflowing the item picker — before ever launching the game. It's not part of the mod or the build; it was a throwaway development tool, not checked into this repo.

## The keycap rendering primitive

`FatTheme.keycap()` draws a single key: a front "wall" (the side of the prism, in a deep shade) topped by a lit face, with the face's vertical position determined by whether the cap is *pressed*:

```java
public static void keycap(GuiGraphicsExtractor g, int x, int y, int w, int capH, int depth,
                          boolean pressed, Tone tone, boolean glow) {
    int baseline = y + capH + depth;
    int capTop = pressed ? y + depth - 1 : y;   // pressed caps sink toward baseline
    ...
}
```

A pressed cap and an unpressed cap occupy the *same footprint* (`w` × `capH + depth`) — only where the lit face sits within that footprint changes. This is what makes the keycap row stable: toggling a slot between guarded and unguarded doesn't reflow anything around it, the cap just visually sinks or rises in place.

Three tones (`GREEN`/`RED`/`SLATE`), each a `(light, mid, deep)` triple used for the face gradient and wall shading. The logo mark (`FatTheme.logoMark()`) is simply two adjacent `keycap()` calls — one pressed+green, one unpressed+red — and every use of the mark in the mod (the header, the footer credit, the inventory-screen launcher button, the standalone `icon.png`) is that same function at a different scale, not a separate asset.

`icon.png` itself is generated, not hand-drawn — a throwaway `java.awt.Graphics2D` script (`LogoGen.java`, not part of the build) renders the same two-keycap composition at 128×128 with a rounded backing plate, using the identical geometry ratios as the in-game primitive so the packaging icon and the in-game logo are visibly the same mark rather than two independent illustrations.

## Mod Menu integration

```java
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return FatfingertConfigScreen::new;
    }
}
```

Registered as a separate entrypoint in `fabric.mod.json`:

```json
"entrypoints": {
    "client": ["dev.fatfingert.FatfingertClient"],
    "modmenu": ["dev.fatfingert.compat.ModMenuIntegration"]
}
```

Fabric Loader only instantiates entrypoint classes that a *present* mod actually asks for by entrypoint key — Mod Menu looks up the `"modmenu"` key and constructs `ModMenuIntegration` itself; if Mod Menu isn't installed, nothing ever asks for that key and the class is simply never loaded. This is why `com.terraformersmc:modmenu` can be a `compileOnly` Gradle dependency (needed so `ModMenuIntegration` compiles against the real `ModMenuApi`/`ConfigScreenFactory` interfaces) without being bundled into the jar or required at runtime — confirmed by inspecting the built jar, which contains `ModMenuIntegration.class` but no Mod Menu classes.

Mod Menu **18.0.0** specifically, not a 15.x release — 15.x targets Minecraft 1.21.x; 18.0.0 is the first line built against 26.1.2, confirmed via Modrinth's version API before pinning it in `gradle.properties`.

## Build system

Fabric Loom 1.17.12, targeting Minecraft **26.1.2** on **Java 25** (the toolchain Mojang ships for that Minecraft version — this is not a stylistic choice, `net.fabricmc:fabric-loader:0.19.3` and the 26.1.2 mappings require it). `build.gradle` pins the compiler to `--release 25` explicitly rather than relying on `sourceCompatibility` alone, since Loom's remapping step is sensitive to bytecode version mismatches.

```properties
minecraft_version=26.1.2
loader_version=0.19.3
fabric_version=0.154.0+26.1.2
modmenu_version=18.0.0
```

`org.gradle.java.installations.paths` in `gradle.properties` points Gradle's toolchain resolver at a local JDK 25 install rather than relying on auto-detection or a download, since JDK 25 is new enough that some Gradle versions won't auto-provision it.

One build-config detail worth flagging for anyone porting this pattern to another MC version: `modCompileOnly` (Loom's remapping-aware compile-only configuration) **does not exist** in this Loom/MC version combination — mods for Minecraft 1.26+ ship with Mojang's official mappings directly (no separate "intermediary" remapping step for the mod's own compiled classes), so a plain Gradle `compileOnly` is correct and sufficient for the Mod Menu dependency. Using `modCompileOnly` here fails at configuration time with `Could not find method modCompileOnly()`.

```bash
./gradlew build          # -> build/libs/fatfingert-<version>.jar
```

## Project layout

```
src/main/java/dev/fatfingert/
├── Fatfingert.java              # rule evaluation — the only "is this allowed" logic
├── FatfingertConfig.java        # config schema, load/save, preset CRUD
├── FatfingertClient.java        # client entrypoint, registers the toggle keybind
├── compat/
│   └── ModMenuIntegration.java  # Mod Menu entrypoint (compileOnly dependency)
├── mixin/
│   ├── MinecraftClientMixin.java               # F-key / Q-key interception
│   ├── ClientPlayerInteractionManagerMixin.java # inventory-click interception
│   └── InventoryScreenMixin.java                # adds the launcher button (cosmetic)
└── gui/
    ├── FatTheme.java                # palette + drawing primitives + keycap renderer
    ├── FatfingertConfigScreen.java  # main panel: presets, toggles, slot row, allow-list
    ├── ItemPickerScreen.java        # searchable item grid for adding rules
    └── widget/
        ├── FatButton.java           # bordered button, 4 visual styles
        ├── ToggleChip.java          # labelled row + sliding on/off pill
        ├── KeycapButton.java        # one slot in the keycap row
        └── ConfigLauncherButton.java # the logo-shaped button on the inventory screen

src/main/resources/
├── fabric.mod.json              # mod metadata, entrypoints, dependencies
├── fatfingert.mixins.json       # mixin package + injection config
└── assets/fatfingert/
    ├── icon.png                 # generated packaging icon (see LogoGen note above)
    └── lang/en_us.json
```

## In-game usage

Open your inventory and click the **keycap-shaped button** next to the recipe book, or open **Mod Menu → Fatfingert → Configure**.

- **Armed** (top-right of the header) is the master switch. Everything else in the mod short-circuits to a no-op while this is off.
- **Preset row:** `‹` / `›` cycle presets. **Click the preset name** to rename it inline — Enter or clicking away saves, Escape cancels. `+` creates a new preset and drops straight into naming it. `−` deletes the active preset; if it holds any rules the button arms (turns red, shows `✓`) and needs a second click to confirm, so a stray click can't destroy a loadout. The last remaining preset can't be deleted.
- **Lock Contents** / **Strict Shift** are per-preset and global toggles respectively (hover either for the exact behavior).
- **The keycap row** is your inventory: `1`–`9` are hotbar slots, `OH` is the offhand. A guarded slot sits pressed and green, showing the item it's reserved for; an unguarded slot stands tall in slate. Click a key to select it (click again to deselect), then **+ Add Item** opens the picker.
- **Item picker:** search by name or id; click an item to allow it, click an already-allowed item (ringed in green) to remove it again.
- Individual items in the allow-list have a **✕** to remove just that one.

There's also an unbound **"Toggle Fatfingert"** keybind under *Options → Controls → Misc*.

Config lives at `config/fatfingert.json` and is safe to hand-edit — slot keys are `offhand`, `hotbar0`...`hotbar8` (`hotbar0` = leftmost), item ids are full registry ids (`minecraft:shield`).

## Known limits (by design)

- **Strict shift-click over-blocks in ambiguous cases.** The mod doesn't have access to vanilla's actual shift-click destination algorithm, so it approximates: if *any* guarded hotbar slot is empty and wouldn't accept the shifted item, the shift-click is blocked, even in cases where vanilla would have actually routed the item somewhere else entirely. This trades some false positives for the guarantee that a guarded slot can never be silently filled by a shift-click the player didn't intend.
- **Creative-mode inventory is untouched.** The click-interception mixin targets `MultiPlayerGameMode.handleContainerInput`, which creative's own screen doesn't route through the same way.
- **It never moves items for you.** A guarded offhand doesn't equip a totem into it — it only stops something *else* from ending up there instead. All the mod ever does is veto.

## Extending it

- **New guardable slots:** would require widening `VALID_SLOT_KEYS`, `slotKeyForPlayerIndex()`, and the `SLOT_ORDER` array in `FatfingertConfigScreen` — armor slots aren't currently modeled since they don't have the same "wrong item lands here mid-fight" failure mode hotbar/offhand do.
- **New per-preset behaviors:** add a field to `FatfingertConfig.Preset`, a check in the relevant mixin or in `Fatfingert.clickBlockReason()`, and a `ToggleChip` in `FatfingertConfigScreen.rebuildWidgets()` — the existing `blockEmptying`/`strictShiftClick` pair is the template.
- **New widgets:** subclass `AbstractWidget`, implement `extractWidgetRenderState` using `FatTheme`'s primitives (`panel`, `panelGradient`, `roundRect`, `outlineGlow`, `scrollbar`) rather than introducing new drawing conventions, so new UI stays visually consistent with the rest of the mod without needing new textures.
