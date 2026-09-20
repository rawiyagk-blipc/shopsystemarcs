package com.arcedge.shop.config;

import com.arcedge.shop.ArcEdgeShop;
import com.arcedge.shop.registry.ItemRegistryService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages config.yml and allowedbuy.yml.
 * Enforces ONE synchronized price database for /shop, /worth, and /sell.
 * Includes automated profit-loop guards and cached pricing lookup.
 */
public final class ConfigManager {

    public record ItemPrice(double buy, double sell, boolean isManual) {}

    private final ArcEdgeShop plugin;
    private final ItemRegistryService registryService;
    private final Map<Material, ItemPrice> priceCache = new ConcurrentHashMap<>();
    private final List<Material> allowedBuyItems = Collections.synchronizedList(new ArrayList<>());

    private double defaultBuyPrice;
    private double defaultSellPrice;
    private double sellPriceRatio;
    private boolean preventProfitLoops;
    private boolean tooltipEnabled;
    private long cooldownMs;
    private String currencySymbol;
    private Component guiTitle;
    private int guiRows;
    private Material fillerMaterial;

    public ConfigManager(ArcEdgeShop plugin, ItemRegistryService registryService) {
        this.plugin = plugin;
        this.registryService = registryService;
    }

    public synchronized void loadAllConfigs() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        // 1. Pricing Settings
        this.defaultBuyPrice = config.getDouble("pricing.default-buy-price", 1000.0);
        this.defaultSellPrice = config.getDouble("pricing.default-sell-price", 100.0);
        this.sellPriceRatio = config.getDouble("pricing.sell-price-ratio", 0.10);
        this.preventProfitLoops = config.getBoolean("pricing.prevent-profit-loops", true);

        // 2. Load manual prices into synchronized cache
        priceCache.clear();
        ConfigurationSection priceSection = config.getConfigurationSection("prices");
        if (priceSection != null) {
            for (String key : priceSection.getKeys(false)) {
                Material mat = registryService.matchItem(key);
                if (mat != null) {
                    double buy = priceSection.getDouble(key + ".buy", defaultBuyPrice);
                    double sell = priceSection.getDouble(key + ".sell", defaultSellPrice);

                    // Profit-loop safeguard: Never allow sell >= buy
                    if (preventProfitLoops && sell >= buy) {
                        plugin.getLogger().warning("Profit loop detected for " + mat.name() +
                                ": Sell ($" + sell + ") >= Buy ($" + buy + "). Clamping sell to 80% of buy.");
                        sell = Math.floor(buy * 0.80);
                    }

                    priceCache.put(mat, new ItemPrice(buy, sell, true));
                }
            }
        }

        // 3. GUI & Security Settings
        MiniMessage mm = plugin.getMiniMessage();
        this.guiTitle = mm.deserialize(config.getString("gui.title", "<bold>ArcEdge Shop</bold>"));
        this.guiRows = Math.max(1, Math.min(6, config.getInt("gui.rows", 4)));
        String fillerName = config.getString("gui.filler-item", "GRAY_STAINED_GLASS_PANE");
        Material filler = registryService.matchItem(fillerName);
        this.fillerMaterial = (filler != null) ? filler : Material.GRAY_STAINED_GLASS_PANE;

        this.tooltipEnabled = config.getBoolean("tooltip.enabled", true);
        this.cooldownMs = config.getLong("security.cooldown-ms", 250);
        this.currencySymbol = config.getString("economy.currency-symbol", "$");

        // 4. Load separate allowedbuy.yml (ONLY controls /shop items)
        loadAllowedBuyConfig();

        plugin.getLogger().info("Loaded " + priceCache.size() + " custom prices and " +
                allowedBuyItems.size() + " allowed shop items.");
    }

    private void loadAllowedBuyConfig() {
        allowedBuyItems.clear();
        File file = new File(plugin.getDataFolder(), "allowedbuy.yml");
        if (!file.exists()) {
            plugin.saveResource("allowedbuy.yml", false);
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<String> items = yaml.getStringList("items");
        for (String itemName : items) {
            Material mat = registryService.matchItem(itemName);
            if (mat != null && !allowedBuyItems.contains(mat)) {
                allowedBuyItems.add(mat);
            } else if (mat == null) {
                plugin.getLogger().warning("Unknown material in allowedbuy.yml: " + itemName);
            }
        }
    }

    /**
     * Retrieves or dynamically generates the safe price for any material.
     * Centralized for both /worth and /sell to guarantee 100% synchronization.
     */
    @NotNull
    public ItemPrice getPrice(@NotNull Material material) {
        ItemPrice cached = priceCache.get(material);
        if (cached != null) {
            return cached;
        }

        // Auto-generated fallback price
        double buy = defaultBuyPrice;
        double sell = Math.floor(buy * sellPriceRatio);
        if (preventProfitLoops && sell >= buy) {
            sell = Math.floor(buy * 0.50);
        }

        ItemPrice generated = new ItemPrice(buy, sell, false);
        priceCache.put(material, generated);
        return generated;
    }

    @Nullable
    public Double getSellPrice(@NotNull Material material) {
        return getPrice(material).sell();
    }

    @Nullable
    public Double getBuyPrice(@NotNull Material material) {
        return getPrice(material).buy();
    }

    public boolean isAllowedToBuy(@NotNull Material material) {
        return allowedBuyItems.contains(material);
    }

    public List<Material> getAllowedBuyItems() {
        return Collections.unmodifiableList(allowedBuyItems);
    }

    public Component getGuiTitle() {
        return guiTitle;
    }

    public int getGuiRows() {
        return guiRows;
    }

    public Material getFillerMaterial() {
        return fillerMaterial;
    }

    public boolean isTooltipEnabled() {
        return tooltipEnabled;
    }

    public long getCooldownMs() {
        return cooldownMs;
    }

    public String getCurrencySymbol() {
        return currencySymbol;
    }

    public String getMessage(String path, String def) {
        return plugin.getConfig().getString("messages." + path, def);
    }
}