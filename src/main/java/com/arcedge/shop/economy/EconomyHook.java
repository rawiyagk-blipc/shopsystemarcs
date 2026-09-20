package com.arcedge.shop.economy;

import com.arcedge.shop.ArcEdgeShop;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Robust Vault Economy interface with atomic transaction handling.
 * Prevents double-spending, negative balances, and provides safe fallback
 * if Vault or an economy provider is absent during staging.
 */
public final class EconomyHook {

    private final ArcEdgeShop plugin;
    private Economy vaultEconomy;
    private boolean vaultAvailable = false;
    private final Map<UUID, Double> fallbackBalances = new ConcurrentHashMap<>();

    public EconomyHook(ArcEdgeShop plugin) {
        this.plugin = plugin;
    }

    public void setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Vault not found. Operating in internal safe fallback mode.");
            vaultAvailable = false;
            return;
        }

        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().warning("No registered Economy provider found for Vault. Using fallback mode.");
            vaultAvailable = false;
            return;
        }

        this.vaultEconomy = rsp.getProvider();
        this.vaultAvailable = (vaultEconomy != null);
        if (vaultAvailable) {
            plugin.getLogger().info("Hooked into Vault economy: " + vaultEconomy.getName());
        }
    }

    public double getBalance(OfflinePlayer player) {
        if (vaultAvailable && vaultEconomy != null) {
            return vaultEconomy.getBalance(player);
        }
        return fallbackBalances.getOrDefault(player.getUniqueId(), 10000.0);
    }

    public boolean hasBalance(OfflinePlayer player, double amount) {
        if (amount < 0) return false;
        return getBalance(player) >= amount;
    }

    /**
     * Atomically withdraws funds from a player.
     * Guaranteed to never overdraw or create race-condition deficits.
     */
    public synchronized boolean withdraw(OfflinePlayer player, double amount) {
        if (amount <= 0) return false;

        if (vaultAvailable && vaultEconomy != null) {
            if (!vaultEconomy.has(player, amount)) return false;
            EconomyResponse response = vaultEconomy.withdrawPlayer(player, amount);
            return response.transactionSuccess();
        }

        // Fallback atomic balance deduction
        double current = getBalance(player);
        if (current < amount) return false;
        fallbackBalances.put(player.getUniqueId(), current - amount);
        return true;
    }

    /**
     * Atomically deposits funds to a player.
     */
    public synchronized boolean deposit(OfflinePlayer player, double amount) {
        if (amount <= 0 || Double.isInfinite(amount) || Double.isNaN(amount)) return false;

        if (vaultAvailable && vaultEconomy != null) {
            EconomyResponse response = vaultEconomy.depositPlayer(player, amount);
            return response.transactionSuccess();
        }

        double current = getBalance(player);
        fallbackBalances.put(player.getUniqueId(), current + amount);
        return true;
    }

    public String format(double amount) {
        String sym = plugin.getConfigManager().getCurrencySymbol();
        return String.format("%s%,.2f", sym, amount);
    }

    public void shutdown() {
        vaultEconomy = null;
    }
}