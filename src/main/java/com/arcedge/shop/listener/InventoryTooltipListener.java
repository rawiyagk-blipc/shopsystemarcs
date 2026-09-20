package com.arcedge.shop.listener;

import com.arcedge.shop.ArcEdgeShop;
import com.arcedge.shop.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Event-driven, cached tooltip decorator for player inventory items.
 * Decorates items with their /worth sell value WITHOUT running laggy 5-second repeating tasks
 * or scanning inventories every tick.
 */
public final class InventoryTooltipListener implements Listener {

    private final ArcEdgeShop plugin;
    private final NamespacedKey decoratedTagKey;

    public InventoryTooltipListener(ArcEdgeShop plugin) {
        this.plugin = plugin;
        this.decoratedTagKey = new NamespacedKey(plugin, "arcedge_worth_tagged");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        // Decorate top/bottom inventory items once upon opening
        for (ItemStack item : event.getInventory().getContents()) {
            decorateItem(item);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        decorateItem(event.getItem().getItemStack());
    }

    /**
     * Decorates item lore with sell price if not already cached.
     * Uses PersistentDataContainer to avoid repeatedly modifying lore.
     */
    public void decorateItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return;

        Material material = item.getType();
        ConfigManager config = plugin.getConfigManager();
        if (!config.isTooltipEnabled()) return;

        Double sellPrice = config.getSellPrice(material);
        if (sellPrice == null || sellPrice <= 0) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(decoratedTagKey, PersistentDataType.BYTE)) {
            return; // Already tagged with sell lore, skip to preserve CPU
        }

        // Mark as tagged
        pdc.set(decoratedTagKey, PersistentDataType.BYTE, (byte) 1);

        List<Component> lore = meta.lore();
        if (lore == null) {
            lore = new ArrayList<>();
        } else {
            lore = new ArrayList<>(lore);
        }

        MiniMessage mm = plugin.getMiniMessage();
        String unitFormat = config.getMessage("tooltip.unit-format",
                "<gray>Sell Value: <green>${price} each</green></gray>")
                .replace("{price}", plugin.getEconomyHook().format(sellPrice));

        lore.add(mm.deserialize(unitFormat).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
    }
}