package dev.fatfingert;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;

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

    public static boolean isAllowed(String slotKey, ItemStack incoming) {
        List<String> rule = ruleFor(slotKey);
        if (rule == null) return true;
        if (incoming.isEmpty()) return !activePreset().blockEmptying;
        return rule.contains(itemId(incoming));
    }

    public static String handSwapBlockReason(Player player) {
        String mainKey = "hotbar" + player.getInventory().getSelectedSlot();
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();

        if (!isAllowed("offhand", main)) {
            return reservedMessage("offhand", main);
        }
        if (!isAllowed(mainKey, off)) {
            return reservedMessage(mainKey, off);
        }
        return null;
    }

    public static String dropBlockReason(Player player) {
        if (!activePreset().blockEmptying) return null;
        String mainKey = "hotbar" + player.getInventory().getSelectedSlot();
        if (hasRule(mainKey) && !player.getMainHandItem().isEmpty()) {
            return lockedMessage(mainKey);
        }
        return null;
    }

    public static String clickBlockReason(Player player, int slotId, int button, ContainerInput actionType) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) return null;

        ItemStack cursor = menu.getCarried();

        String clickedKey = null;
        ItemStack clickedStack = ItemStack.EMPTY;
        boolean clickedIsPlayerInv = false;

        if (slotId >= 0 && slotId < menu.slots.size()) {
            Slot slot = menu.getSlot(slotId);
            clickedStack = slot.getItem();
            if (slot.container instanceof Inventory) {
                clickedIsPlayerInv = true;
                clickedKey = slotKeyForPlayerIndex(slot.getContainerSlot());
            }
        }

        boolean blockEmptying = activePreset().blockEmptying;

        switch (actionType) {
            case PICKUP, PICKUP_ALL -> {
                if (clickedKey != null) {
                    if (!cursor.isEmpty() && !isAllowed(clickedKey, cursor)) {
                        return reservedMessage(clickedKey, cursor);
                    }
                    if (cursor.isEmpty() && !clickedStack.isEmpty() && blockEmptying && hasRule(clickedKey)) {
                        return lockedMessage(clickedKey);
                    }
                }
            }
            case QUICK_CRAFT -> {
                if (clickedKey != null && !cursor.isEmpty() && !isAllowed(clickedKey, cursor)) {
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
                if (otherKey != null && !isAllowed(otherKey, clickedStack)) {
                    return reservedMessage(otherKey, clickedStack);
                }
                if (clickedKey != null && !isAllowed(clickedKey, otherStack)) {
                    return reservedMessage(clickedKey, otherStack);
                }
            }
            case THROW -> {
                if (clickedKey != null && blockEmptying && hasRule(clickedKey) && !clickedStack.isEmpty()) {
                    return lockedMessage(clickedKey);
                }
            }
            case QUICK_MOVE -> {
                if (clickedKey != null && blockEmptying && hasRule(clickedKey) && !clickedStack.isEmpty()) {
                    return lockedMessage(clickedKey);
                }
                if (config().strictShiftClick && clickedKey == null && !clickedStack.isEmpty()) {
                    for (int i = 0; i <= 8; i++) {
                        String key = "hotbar" + i;
                        if (hasRule(key)
                                && !ruleFor(key).contains(itemId(clickedStack))
                                && player.getInventory().getItem(i).isEmpty()) {
                            return reservedMessage(key, clickedStack);
                        }
                    }
                    if (clickedIsPlayerInv
                            && clickedStack.getItem() instanceof ShieldItem
                            && hasRule("offhand")
                            && !ruleFor("offhand").contains(itemId(clickedStack))
                            && player.getInventory().getItem(OFFHAND_INDEX).isEmpty()) {
                        return reservedMessage("offhand", clickedStack);
                    }
                }
            }
            default -> {
            }
        }
        return null;
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
            Minecraft.getInstance().player.sendOverlayMessage(
                    Component.literal(reason).withStyle(ChatFormatting.RED));
        }
    }
}
