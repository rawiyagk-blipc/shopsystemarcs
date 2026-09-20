package com.arcedge.shop.command;

import com.arcedge.shop.ArcEdgeShop;
import com.arcedge.shop.config.ConfigManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Executes /sell hand and /sell all.
 * Always synchronized with /worth using the exact same price resolver.
 * Atomic: verifies and removes items first, and only then deposits money.
 */
public final class SellCommand implements BasicCommand {

    private final ArcEdgeShop plugin;
    private final Set<UUID> sellingPlayers = ConcurrentHashMap.newKeySet();

    public SellCommand(ArcEdgeShop plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command is player-only.");
            return;
        }

        ConfigManager config = plugin.getConfigManager();
        MiniMessage mm = plugin.getMiniMessage();

        if (!player.hasPermission("arcedgeshop.sell")) {
            player.sendMessage(mm.deserialize(config.getMessage("no-permission", "<red>No permission.</red>")));
            return;
        }

        // Concurrency Lock
        UUID uuid = player.getUniqueId();
        if (!sellingPlayers.add(uuid)) {
            player.sendMessage(mm.deserialize(config.getMessage("prefix", "") + "<red>A sell transaction is already in progress.</red>"));
            return;
        }

        try {
            boolean sellAll = (args.length > 0 && args[0].equalsIgnoreCase("all"));
            if (sellAll) {
                sellAllItems(player);
            } else {
                sellHandItem(player);
            }
        } finally {
            sellingPlayers.remove(uuid);
        }
    }

    private void sellHandItem(Player player) {
        ConfigManager config = plugin.getConfigManager();
        MiniMessage mm = plugin.getMiniMessage();

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            player.sendMessage(mm.deserialize(config.getMessage("prefix", "") +
                    config.getMessage("sell-no-items", "<red>You have no sellable items in your hand.</red>")));
            return;
        }

        Material material = hand.getType();
        Double unitPrice = config.getSellPrice(material);
        if (unitPrice == null || unitPrice <= 0) {
            player.sendMessage(mm.deserialize(config.getMessage("prefix", "") +
                    config.getMessage("worth-not-sellable", "<red>This item cannot be sold to the server.</red>")));
            return;
        }

        int amount = hand.getAmount();
        double totalPayout = unitPrice * amount;

        // ATOMIC TRANSACTION:
        // 1. Remove items first
        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));

        // 2. Deposit money
        boolean paid = plugin.getEconomyHook().deposit(player, totalPayout);
        if (!paid) {
            // Rollback on unexpected failure
            player.getInventory().setItemInMainHand(new ItemStack(material, amount));
            player.sendMessage(mm.deserialize(config.getMessage("prefix", "") +
                    "<red>Economy deposit failed. Your items have been returned.</red>"));
            return;
        }

        // 3. Confirm
        String msg = config.getMessage("sell-success", "<green>Sold {amount}x {item} for {total}!</green>")
                .replace("{amount}", String.valueOf(amount))
                .replace("{item}", material.name())
                .replace("{total}", plugin.getEconomyHook().format(totalPayout));

        player.sendMessage(mm.deserialize(config.getMessage("prefix", "") + msg));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
    }

    private void sellAllItems(Player player) {
        ConfigManager config = plugin.getConfigManager();
        MiniMessage mm = plugin.getMiniMessage();

        double totalPayout = 0;
        int totalSold = 0;
        ItemStack[] contents = player.getInventory().getContents();

        for (int i = 0; i < 36; i++) { // Only main inventory + hotbar (exclude armor/offhand)
            ItemStack stack = contents[i];
            if (stack == null || stack.getType().isAir()) continue;

            Material mat = stack.getType();
            Double unitPrice = config.getSellPrice(mat);
            if (unitPrice != null && unitPrice > 0) {
                int count = stack.getAmount();
                totalPayout += unitPrice * count;
                totalSold += count;
                player.getInventory().setItem(i, new ItemStack(Material.AIR));
            }
        }

        if (totalSold == 0) {
            player.sendMessage(mm.deserialize(config.getMessage("prefix", "") +
                    "<red>You have no sellable items in your inventory.</red>"));
            return;
        }

        boolean paid = plugin.getEconomyHook().deposit(player, totalPayout);
        if (!paid) {
            player.sendMessage(mm.deserialize(config.getMessage("prefix", "") +
                    "<red>Economy deposit error occurred. Please contact an administrator.</red>"));
            return;
        }

        player.sendMessage(mm.deserialize(config.getMessage("prefix", "") +
                "<green>Successfully sold <white>" + totalSold + " items</white> for <gold>" +
                plugin.getEconomyHook().format(totalPayout) + "</gold>!</green>"));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
    }

    @Override
    public Collection<String> suggest(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        if (args.length <= 1) {
            return List.of("hand", "all");
        }
        return Collections.emptyList();
    }

    @Override
    public boolean canUse(@NotNull CommandSender sender) {
        return sender.hasPermission("arcedgeshop.sell");
    }

    @Override
    public @Nullable String permission() {
        return "arcedgeshop.sell";
    }
}