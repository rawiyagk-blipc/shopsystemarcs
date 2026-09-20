package com.arcedge.shop.gui;

import com.arcedge.shop.ArcEdgeShop;
import com.arcedge.shop.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom InventoryHolder representing an active ArcEdge shop session.
 * Used by event listeners to strictly identify shop GUIs and prevent click-theft.
 */
public final class ShopGUI implements InventoryHolder {

    private final ArcEdgeShop plugin;
    private final Player player;
    private final Inventory inventory;

    public ShopGUI(ArcEdgeShop plugin, Player player) {
        this.plugin = plugin;
        this.player = player;

        ConfigManager config = plugin.getConfigManager();
        int size = config.getGuiRows() * 9;
        this.inventory = Bukkit.createInventory(this, size, config.getGuiTitle());
        populate();
    }

    private void populate() {
        ConfigManager config = plugin.getConfigManager();
        List<Material> allowedItems = config.getAllowedBuyItems();

        // 1. Fill background filler if enabled
        ItemStack filler = new ItemStack(config.getFillerMaterial());
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.displayName(Component.empty());
            filler.setItemMeta(fillerMeta);
        }

        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }

        // 2. Center allowed items (default 10 everyday items) in middle rows
        int[] displaySlots = { 10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25 };
        MiniMessage mm = plugin.getMiniMessage();

        for (int i = 0; i < allowedItems.size() && i < displaySlots.length; i++) {
            Material material = allowedItems.get(i);
            ConfigManager.ItemPrice price = config.getPrice(material);

            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());
                lore.add(mm.deserialize("<gray>Buy Price: <gold>" + plugin.getEconomyHook().format(price.buy()) + "</gold></gray>")
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(mm.deserialize("<gray>Sell Value: <green>" + plugin.getEconomyHook().format(price.sell()) + "</green></gray>")
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.empty());
                lore.add(mm.deserialize("<yellow>▶ Left-Click: Buy 1</yellow>")
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(mm.deserialize("<yellow>▶ Right-Click: Buy Stack</yellow>")
                        .decoration(TextDecoration.ITALIC, false));
                meta.lore(lore);
                item.setItemMeta(meta);
            }

            inventory.setItem(displaySlots[i], item);
        }

        // 3. Bottom status item showing player's current balance
        int statusSlot = inventory.getSize() - 5; // Bottom center
        ItemStack status = new ItemStack(Material.GOLD_INGOT);
        ItemMeta statusMeta = status.getItemMeta();
        if (statusMeta != null) {
            statusMeta.displayName(mm.deserialize("<gradient:#f6d365:#fda085><bold>Your Wallet</bold></gradient>")
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = List.of(
                    Component.empty(),
                    mm.deserialize("<gray>Balance: <gold>" +
                            plugin.getEconomyHook().format(plugin.getEconomyHook().getBalance(player)) + "</gold></gray>")
                            .decoration(TextDecoration.ITALIC, false),
                    mm.deserialize("<dark_gray>ArcEdge Atomic Protection Enabled</dark_gray>")
                            .decoration(TextDecoration.ITALIC, false)
            );
            statusMeta.lore(lore);
            status.setItemMeta(statusMeta);
        }
        inventory.setItem(statusSlot, status);
    }

    public void open() {
        player.openInventory(inventory);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}