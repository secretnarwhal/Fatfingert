package dev.fatfingert;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Set;

/**
 * Fatfingert - misinput insurance for your hotbar.
 *
 * Everything here only ever CANCELS one of your own inputs before it turns into
 * a packet. Nothing presses keys, moves items, or talks to the server for you.
 */
public final class Fatfingert {

    public static final String MOD_ID = "fatfingert";
    public static final String AUTHOR = "AlmondsMilk";

    private static final int OFFHAND_INDEX = 40;

    // Player inventory indices, in the order vanilla's moveItemStackTo walks them.
    private static final int[] HOTBAR_ROUTE = {0, 1, 2, 3, 4, 5, 6, 7, 8};
    private static final int[] OFFHAND_ROUTE = {OFFHAND_INDEX};
    private static final int[] MAIN_THEN_HOTBAR_ROUTE = playerRoute(false);
    private static final int[] HOTBAR_THEN_MAIN_ROUTE = playerRoute(true);

    public static final Set<String> VALID_SLOT_KEYS = Set.of(
            "offhand",
            "hotbar0", "hotbar1", "hotbar2", "hotbar3", "hotbar4",
            "hotbar5", "hotbar6", "hotbar7", "hotbar8"
    );

    private static FatfingertConfig config;
    private static long lastMessageMs = 0;

    private Fatfingert() {}

    public static void loadConfig() {
        config = FatfingertConfig.load();
    }

    public static FatfingertConfig config() {
        if (config == null) loadConfig();
        return config;
    }

    public static boolean isEnabled() {
        return config().enabled;
    }

    public static boolean toggle() {
        config().enabled = !config().enabled;
        config().save();
        return config().enabled;
    }

    public static boolean toggleLockContents() {
        FatfingertConfig.Preset p = activePreset();
        p.blockEmptying = !p.blockEmptying;
        config().save();
        return p.blockEmptying;
    }

    public static boolean toggleStrictShift() {
        config().strictShiftClick = !config().strictShiftClick;
        config().save();
        return config().strictShiftClick;
    }

    public static boolean toggleInventoryButton() {
        config().showInventoryButton = !config().showInventoryButton;
        config().save();
        return config().showInventoryButton;
    }

    public static FatfingertConfig.Preset activePreset() {
        FatfingertConfig cfg = config();
        FatfingertConfig.Preset p = cfg.presets.get(cfg.activePreset);
        return p != null ? p : new FatfingertConfig.Preset();
    }

    public static List<String> ruleFor(String slotKey) {
        List<String> rule = activePreset().rules.get(slotKey);
        return (rule == null || rule.isEmpty()) ? null : rule;
    }

    public static boolean hasRule(String slotKey) {
        return ruleFor(slotKey) != null;
    }

    /** Number of slots the active preset currently guards. */
    public static int guardedSlotCount() {
        int n = 0;
        for (String key : VALID_SLOT_KEYS) {
            if (hasRule(key)) n++;
        }
        return n;
    }

    public static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    public static String normalizeId(String id) {
        Identifier parsed = Identifier.tryParse(id.trim().toLowerCase());
        return parsed == null ? null : parsed.toString();
    }

    /**
     * Can {@code incoming} take the place of {@code current} in a slot? The gate
     * is about what goes IN: a wrong item is always free to leave, and emptying a
     * slot is only refused when Lock Contents is holding an allowed item there.
     */
    public static boolean isAllowed(String slotKey, ItemStack current, ItemStack incoming) {
        List<String> rule = ruleFor(slotKey);
        if (rule == null) return true;
        if (!incoming.isEmpty()) return rule.contains(itemId(incoming));
        return !isLocked(slotKey, current);
    }

    /** Lock Contents only ever holds on to an item the slot is reserved for. */
    public static boolean isLocked(String slotKey, ItemStack current) {
        if (current.isEmpty() || !activePreset().blockEmptying) return false;
        List<String> rule = ruleFor(slotKey);
        return rule != null && rule.contains(itemId(current));
    }

    public static String handSwapBlockReason(Player player) {
        String mainKey = "hotbar" + player.getInventory().getSelectedSlot();
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();

        if (!isAllowed("offhand", off, main)) {
            return reservedMessage("offhand", main);
        }
        if (!isAllowed(mainKey, main, off)) {
            return reservedMessage(mainKey, off);
        }
        return null;
    }

    public static String dropBlockReason(Player player) {
        String mainKey = "hotbar" + player.getInventory().getSelectedSlot();
        return isLocked(mainKey, player.getMainHandItem()) ? lockedMessage(mainKey) : null;
    }

    public static String clickBlockReason(Player player, int slotId, int button, ClickType actionType) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) return null;

        ItemStack cursor = menu.getCarried();

        Slot slot = null;
        String clickedKey = null;
        ItemStack clickedStack = ItemStack.EMPTY;

        if (slotId >= 0 && slotId < menu.slots.size()) {
            slot = menu.getSlot(slotId);
            clickedStack = slot.getItem();
            if (slot.container instanceof Inventory) {
                clickedKey = slotKeyForPlayerIndex(slot.getContainerSlot());
            }
        }

        switch (actionType) {
            case PICKUP -> {
                // An empty cursor lifts the stack out; a loaded one puts itself in.
                if (clickedKey != null && !isAllowed(clickedKey, clickedStack, cursor)) {
                    return reservedMessage(clickedKey, cursor);
                }
            }
            case PICKUP_ALL -> {
                return gatherBlockReason(player, menu, slot, cursor);
            }
            case QUICK_CRAFT -> {
                if (clickedKey != null && !cursor.isEmpty() && !isAllowed(clickedKey, clickedStack, cursor)) {
                    return reservedMessage(clickedKey, cursor);
                }
            }
            case SWAP -> {
                String otherKey = null;
                ItemStack otherStack = ItemStack.EMPTY;
                if (button == OFFHAND_INDEX) {
                    otherKey = "offhand";
                    otherStack = player.getInventory().getItem(OFFHAND_INDEX);
                } else if (button >= 0 && button <= 8) {
                    otherKey = "hotbar" + button;
                    otherStack = player.getInventory().getItem(button);
                }
                // Swapping a slot with itself changes nothing.
                if (otherKey != null && otherKey.equals(clickedKey)) return null;
                if (otherKey != null && !isAllowed(otherKey, otherStack, clickedStack)) {
                    return reservedMessage(otherKey, clickedStack);
                }
                if (clickedKey != null && !isAllowed(clickedKey, clickedStack, otherStack)) {
                    return reservedMessage(clickedKey, otherStack);
                }
            }
            case THROW -> {
                if (clickedKey != null && isLocked(clickedKey, clickedStack)) {
                    return lockedMessage(clickedKey);
                }
            }
            case QUICK_MOVE -> {
                if (clickedKey != null && isLocked(clickedKey, clickedStack)) {
                    return lockedMessage(clickedKey);
                }
                if (config().strictShiftClick && slot != null && !clickedStack.isEmpty()) {
                    return shiftClickBlockReason(player, menu, slotId, slot, clickedStack);
                }
            }
            default -> {
            }
        }
        return null;
    }

    /**
     * Double-click gathering only ever pulls matching stacks OUT of slots, so the
     * one thing that can refuse it is Lock Contents.
     */
    private static String gatherBlockReason(Player player, AbstractContainerMenu menu, Slot clicked, ItemStack cursor) {
        if (!activePreset().blockEmptying || clicked == null || cursor.isEmpty()) return null;
        // Vanilla only gathers when the clicked slot is empty and the cursor has room.
        if ((clicked.hasItem() && clicked.mayPickup(player)) || cursor.getCount() >= cursor.getMaxStackSize()) {
            return null;
        }
        for (Slot slot : menu.slots) {
            if (!(slot.container instanceof Inventory)) continue;
            String key = slotKeyForPlayerIndex(slot.getContainerSlot());
            ItemStack there = slot.getItem();
            if (key != null && ItemStack.isSameItemSameComponents(cursor, there) && isLocked(key, there)) {
                return lockedMessage(key);
            }
        }
        return null;
    }

    /**
     * Strict Shift. Where a shift-click goes is up to the open menu, so rather
     * than refusing whenever some guarded slot is empty, this follows the route
     * the menu's quickMoveStack takes and only blocks when the stack would end up
     * INSIDE a guarded slot that doesn't accept it. Shifting something out - off
     * the hotbar, or into a chest - is never refused.
     */
    private static String shiftClickBlockReason(Player player, AbstractContainerMenu menu,
                                                 int slotId, Slot slot, ItemStack stack) {
        Inventory inv = player.getInventory();

        if (menu instanceof InventoryMenu) {
            // 0 craft result, 1-4 craft grid, 5-8 armor, 9-35 main, 36-44 hotbar, 45 offhand.
            if (slotId == 0) return reachableBlockReason(inv, stack);
            if (slotId < 9) return landingBlockReason(inv, stack, MAIN_THEN_HOTBAR_ROUTE);

            EquipmentSlot equip = player.getEquipmentSlotForItem(stack);
            if (equip.getType() == EquipmentSlot.Type.HUMANOID_ARMOR && player.getItemBySlot(equip).isEmpty()) {
                return null;
            }
            if (equip == EquipmentSlot.OFFHAND && inv.getItem(OFFHAND_INDEX).isEmpty()) {
                return landingBlockReason(inv, stack, OFFHAND_ROUTE);
            }
            if (slotId < 36) return landingBlockReason(inv, stack, HOTBAR_ROUTE);
            if (slotId < 45) return null;
            return landingBlockReason(inv, stack, MAIN_THEN_HOTBAR_ROUTE);
        }

        // Chest-like menus trade strictly between the container and the player.
        boolean storage = menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu
                || menu instanceof HopperMenu || menu instanceof DispenserMenu || menu instanceof CrafterMenu;

        if (slot.container instanceof Inventory) {
            // The hotbar only ever sends things away. The main inventory goes into
            // the container, or - in furnaces, crafting tables and the like - may
            // fall back to the hotbar.
            if (slot.getContainerSlot() <= 8 || storage) return null;
            return landingBlockReason(inv, stack, HOTBAR_ROUTE);
        }

        // Out of the container, into the player's inventory. Other menus vary in
        // direction and may repeat (crafting results), so assume any open spot.
        return storage
                ? landingBlockReason(inv, stack, HOTBAR_THEN_MAIN_ROUTE)
                : reachableBlockReason(inv, stack);
    }

    /**
     * Replays moveItemStackTo over {@code route} without touching anything: top up
     * matching stacks in order, then put the rest in the first empty slot.
     */
    private static String landingBlockReason(Inventory inv, ItemStack stack, int[] route) {
        int remaining = stack.getCount();
        if (stack.isStackable()) {
            for (int index : route) {
                ItemStack there = inv.getItem(index);
                if (there.isEmpty() || !ItemStack.isSameItemSameComponents(stack, there)) continue;
                int room = there.getMaxStackSize() - there.getCount();
                if (room <= 0) continue;
                String reason = intoBlockReason(index, there, stack);
                if (reason != null) return reason;
                remaining -= room;
                if (remaining <= 0) return null;
            }
        }
        for (int index : route) {
            if (inv.getItem(index).isEmpty()) return intoBlockReason(index, ItemStack.EMPTY, stack);
        }
        return null;
    }

    /** For moves whose landing spot we can't pin down: any hotbar slot with room counts. */
    private static String reachableBlockReason(Inventory inv, ItemStack stack) {
        for (int index : HOTBAR_ROUTE) {
            ItemStack there = inv.getItem(index);
            boolean fits = there.isEmpty() || (ItemStack.isSameItemSameComponents(stack, there)
                    && there.getCount() < there.getMaxStackSize());
            if (!fits) continue;
            String reason = intoBlockReason(index, there, stack);
            if (reason != null) return reason;
        }
        return null;
    }

    private static String intoBlockReason(int inventoryIndex, ItemStack current, ItemStack incoming) {
        String key = slotKeyForPlayerIndex(inventoryIndex);
        return key == null || isAllowed(key, current, incoming) ? null : reservedMessage(key, incoming);
    }

    /** Main inventory then hotbar - the order menus list the player's slots in - optionally reversed. */
    private static int[] playerRoute(boolean reversed) {
        int[] route = new int[36];
        for (int i = 0; i < 36; i++) {
            route[reversed ? 35 - i : i] = (i + 9) % 36;
        }
        return route;
    }

    public static String slotKeyForPlayerIndex(int index) {
        if (index >= 0 && index <= 8) return "hotbar" + index;
        if (index == OFFHAND_INDEX) return "offhand";
        return null;
    }

    public static String prettySlot(String slotKey) {
        if (slotKey.equals("offhand")) return "Offhand";
        if (slotKey.startsWith("hotbar")) {
            return "Slot " + (Integer.parseInt(slotKey.substring(6)) + 1);
        }
        return slotKey;
    }

    /** Turns a raw preset key like "crystal_pvp" into "Crystal PvP". */
    public static String prettyPreset(String presetKey) {
        if (presetKey == null || presetKey.isEmpty()) return "None";
        if (presetKey.equals("crystal_pvp")) return "Crystal PvP";
        StringBuilder out = new StringBuilder();
        for (String word : presetKey.split("_")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    /** Display name for an item id, falling back to the raw id if unknown. */
    public static String itemName(String id) {
        Identifier loc = Identifier.tryParse(id);
        if (loc != null && BuiltInRegistries.ITEM.containsKey(loc)) {
            Item item = BuiltInRegistries.ITEM.getValue(loc);
            return item.getName(item.getDefaultInstance()).getString();
        }
        return id;
    }

    /** An ItemStack for an item id, or EMPTY when the id is unknown. */
    public static ItemStack stackFor(String id) {
        Identifier loc = Identifier.tryParse(id);
        if (loc != null && BuiltInRegistries.ITEM.containsKey(loc)) {
            return new ItemStack(BuiltInRegistries.ITEM.getValue(loc));
        }
        return ItemStack.EMPTY;
    }

    private static String allowedSummary(String slotKey) {
        List<String> rule = ruleFor(slotKey);
        if (rule == null || rule.isEmpty()) return "its reserved items";
        String first = itemName(rule.get(0));
        return rule.size() == 1 ? first : first + " +" + (rule.size() - 1) + " more";
    }

    private static String lockedMessage(String slotKey) {
        return "⚠ " + prettySlot(slotKey) + " is locked";
    }

    private static String reservedMessage(String slotKey, ItemStack rejected) {
        if (rejected.isEmpty()) return lockedMessage(slotKey);
        return "⚠ " + prettySlot(slotKey) + " only takes " + allowedSummary(slotKey);
    }

    public static void notifyBlocked(Player player, String reason) {
        if (!config().showMessages || player == null) return;
        long now = System.currentTimeMillis();
        if (now - lastMessageMs < 400) return;
        lastMessageMs = now;

        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal(reason).withStyle(ChatFormatting.RED), true);
        }
    }
}
