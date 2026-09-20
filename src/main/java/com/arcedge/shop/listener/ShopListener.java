package com.arcedge.shop.listener;

import com.arcedge.shop.ArcEdgeShop;
import com.arcedge.shop.config.ConfigManager;
import com.arcedge.shop.gui.ShopGUI;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hardened Anti-Dupe & Exploit Guard for ArcEdge Shop GUI.
 * Validates real server-side ItemStack data and blocks all known client inventory glitches.
 */
public final class ShopListener implements Listener {

    private final ArcEdgeShop plugin;
    private final Map<UUID, Long> clickCooldowns = new ConcurrentHashMap<>();
    private final Set<UUID> activeTransactions = ConcurrentHashMap.newKeySet();

    public ShopListener(ArcEdgeShop plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Check if top inventory belongs to ArcEdge Shop GUI
        if (!(event.getView().getTopInventory().getHolder() instanceof ShopGUI)) {
            return;
        }

        // 1. Immediately cancel ANY click inside or interacting with the shop view
        event.setCancelled(true);

        // 2. Block hotbar number-key swapping and shift-clicking into shop
        if (event.getClick() == ClickType.NUMBER_KEY ||
            event.getClick() == ClickType.DOUBLE_CLICK ||
            event.isShiftClick()) {
            return;
        }

        // 3. Ignore clicks outside or on the player's bottom inventory
        if (event.getClickedInventory() == null ||
            event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        Material material = clicked.getType();
        ConfigManager config = plugin.getConfigManager();

        // Ensure clicked item is genuinely allowed in allowedbuy.yml
        if (!config.isAllowedToBuy(material)) {
            return;
        }

        // 4. Atomic Cooldown Lock: Prevent rapid macro/packet spamming dupe attempts
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = clickCooldowns.get(uuid);
        if (last != null && (now - last) < config.getCooldownMs()) {
            return;
        }
        clickCooldowns.put(uuid, now);

        // 5. Transaction Concurrency Lock: Ensure player can't trigger 2 simultaneous purchases
        if (!activeTransactions.add(uuid)) {
            return; // Transaction already executing on worker
        }

        try {
            processPurchase(player, material, event.isRightClick());
        } finally {
            activeTransactions.remove(uuid);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        // Prevent all drag-and-drop item duplication exploits in shop GUI
        if (event.getView().getTopInventory().getHolder() instanceof ShopGUI) {
            event.setCancelled(true);
        }
    }

    private void processPurchase(Player player, Material material, boolean isRightClick) {
        ConfigManager config = plugin.getConfigManager();
        ConfigManager.ItemPrice priceInfo = config.getPrice(material);

        int amount = isRightClick ? Math.min(64, material.getMaxStackSize()) : 1;
        double totalPrice = priceInfo.buy() * amount;

        MiniMessage mm = plugin.getMiniMessage();

        // 1. Check space BEFORE withdrawing money
        if (!hasInventorySpace(player, material, amount)) {
            player.sendMessage(mm.deserialize(config.getMessage("prefix", "") +
                    config.getMessage("buy-inventory-full", "<red>Your inventory is full!</red>")));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        // 2. Check funds
        double balance = plugin.getEconomyHook().getBalance(player);
        if (balance < totalPrice) {
            String msg = config.getMessage("buy-insufficient-funds", "<red>Insufficient funds.</red>")
                    .replace("{price}", plugin.getEconomyHook().format(totalPrice))
                    .replace("{balance}", plugin.getEconomyHook().format(balance));
            player.sendMessage(mm.deserialize(config.getMessage("prefix", "") + msg));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        // 3. Atomic Economy Transaction: Deduct funds first
        boolean paid = plugin.getEconomyHook().withdraw(player, totalPrice);
        if (!paid) {
            player.sendMessage(mm.deserialize(config.getMessage("prefix", "") +
                    "<red>Transaction failed: could not process economy withdrawal.</red>"));
            return;
        }

        // 4. Safely give items to player
        ItemStack itemToGive = new ItemStack(material, amount);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(itemToGive);

        if (!leftover.isEmpty()) {
            // Emergency fallback: If inventory unexpectedly refused, drop remaining at feet
            for (ItemStack rem : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rem);
            }
        }

        // 5. Notify & Sound
        String successMsg = config.getMessage("buy-success", "<green>Purchased {amount}x {item} for {price}!</green>")
                .replace("{amount}", String.valueOf(amount))
                .replace("{item}", material.name())
                .replace("{price}", plugin.getEconomyHook().format(totalPrice));
        player.sendMessage(mm.deserialize(config.getMessage("prefix", "") + successMsg));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
    }

    private boolean hasInventorySpace(Player player, Material material, int amount) {
        Inventory inv = player.getInventory();
        int maxStack = material.getMaxStackSize();
        int remaining = amount;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.getType().isAir()) {
                remaining -= maxStack;
            } else if (stack.getType() == material && stack.getAmount() < maxStack) {
                remaining -= (maxStack - stack.getAmount());
            }
            if (remaining <= 0) return true;
        }
        return false;
    }
}