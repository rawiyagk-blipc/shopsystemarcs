package com.arcedge.shop;

import com.arcedge.shop.command.ArcEdgeShopCommand;
import com.arcedge.shop.command.SellCommand;
import com.arcedge.shop.command.ShopCommand;
import com.arcedge.shop.command.WorthCommand;
import com.arcedge.shop.config.ConfigManager;
import com.arcedge.shop.economy.EconomyHook;
import com.arcedge.shop.listener.InventoryTooltipListener;
import com.arcedge.shop.listener.ShopListener;
import com.arcedge.shop.registry.ItemRegistryService;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;

/**
 * ArcEdgeShop - High-Performance Paper 26.1.2 - 26.3+ Shop Plugin.
 * Built with Java 25, event-driven design, zero per-tick inventory loops,
 * and dynamic Paper item registry auto-discovery.
 */
public final class ArcEdgeShop extends JavaPlugin {

    private static ArcEdgeShop instance;
    private ConfigManager configManager;
    private EconomyHook economyHook;
    private ItemRegistryService registryService;
    private MiniMessage miniMessage;

    @Override
    public void onEnable() {
        instance = this;
        this.miniMessage = MiniMessage.miniMessage();

        getLogger().info("Initializing ArcEdgeShop for Paper 26.x (Java 25 runtime)...");

        // 1. Initialize Item Registry Service (Dynamic RegistryAccess without hardcoded IDs)
        this.registryService = new ItemRegistryService(this);
        this.registryService.initRegistry();

        // 2. Load Configurations (config.yml and allowedbuy.yml)
        this.configManager = new ConfigManager(this, registryService);
        this.configManager.loadAllConfigs();

        // 3. Connect to Economy Provider (Vault or safe fallback)
        this.economyHook = new EconomyHook(this);
        this.economyHook.setupEconomy();

        // 4. Register Event Listeners (Lightweight, event-driven, anti-dupe)
        getServer().getPluginManager().registerEvents(new ShopListener(this), this);
        if (configManager.isTooltipEnabled()) {
            getServer().getPluginManager().registerEvents(new InventoryTooltipListener(this), this);
        }

        // 5. Register Commands
        registerCommands();

        getLogger().info("ArcEdgeShop enabled successfully! " +
                registryService.getRegisteredItemCount() + " server items indexed dynamically.");
    }

    @Override
    public void onDisable() {
        if (economyHook != null) {
            economyHook.shutdown();
        }
        getLogger().info("ArcEdgeShop safely disabled.");
    }

    private void registerCommands() {
        // Native Paper 1.20.6 / 1.21 / 26+ command registration via BasicCommand
        registerCommand("shop", "Open ArcEdge shop interface", List.of("eshop"), new ShopCommand(this));
        registerCommand("worth", "Inspect sell worth of item in hand or inventory", List.of("itemworth"), new WorthCommand(this));
        registerCommand("sell", "Sell items in hand or inventory to the shop", Collections.emptyList(), new SellCommand(this));
        registerCommand("arcedgeshop", "ArcEdgeShop administration and reload", List.of("aeshop"), new ArcEdgeShopCommand(this));
    }

    public static ArcEdgeShop getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public EconomyHook getEconomyHook() {
        return economyHook;
    }

    public ItemRegistryService getRegistryService() {
        return registryService;
    }

    public MiniMessage getMiniMessage() {
        return miniMessage;
    }
}