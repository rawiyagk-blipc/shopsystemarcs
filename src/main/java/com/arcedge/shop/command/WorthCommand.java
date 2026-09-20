package com.arcedge.shop.command;

import com.arcedge.shop.ArcEdgeShop;
import com.arcedge.shop.config.ConfigManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class WorthCommand implements BasicCommand {

    private final ArcEdgeShop plugin;

    public WorthCommand(ArcEdgeShop plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be executed by players.");
            return;
        }

        MiniMessage mm = plugin.getMiniMessage();
        ConfigManager config = plugin.getConfigManager();

        if (!player.hasPermission("arcedgeshop.worth")) {
            player.sendMessage(mm.deserialize(config.getMessage("no-permission", "<red>No permission.</red>")));
            return;
        }

        // Optional /worth inventory argument
        if (args.length > 0 && args[0].equalsIgnoreCase("all")) {
            evaluateTotalInventory(player);
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            player.sendMessage(mm.deserialize(config.getMessage("prefix", "") +
                    config.getMessage("worth-no-item", "<red>You are not holding an item.</red>")));
            return;
        }

        Material material = hand.getType();
        Double unitSellPrice = config.getSellPrice(material);

        if (unitSellPrice == null || unitSellPrice <= 0) {
            player.sendMessage(mm.deserialize(config.getMessage("prefix", "") +
                    config.getMessage("worth-not-sellable", "<red>This item has no sell price configured.</red>")));
            return;
        }

        int amount = hand.getAmount();
        double stackValue = unitSellPrice * amount;

        String formatted = config.getMessage("worth-display",
                "<gray>{item} <white>x{amount}</white></gray>\n<gray>Sell Price: <green>{unit_price} each</green></gray>\n<gray>Stack Value: <gold>{stack_value}</gold></gray>")
                .replace("{item}", material.name())
                .replace("{amount}", String.valueOf(amount))
                .replace("{unit_price}", plugin.getEconomyHook().format(unitSellPrice))
                .replace("{stack_value}", plugin.getEconomyHook().format(stackValue));

        player.sendMessage(mm.deserialize(formatted));
    }

    @Override
    public Collection<String> suggest(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        if (args.length <= 1) {
            return List.of("all");
        }
        return Collections.emptyList();
    }

    @Override
    public boolean canUse(@NotNull CommandSender sender) {
        return sender.hasPermission("arcedgeshop.worth");
    }

    @Override
    public @Nullable String permission() {
        return "arcedgeshop.worth";
    }

    private void evaluateTotalInventory(Player player) {
        ConfigManager config = plugin.getConfigManager();
        double totalWorth = 0;
        int totalItems = 0;

        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType().isAir()) continue;
            Double unit = config.getSellPrice(stack.getType());
            if (unit != null && unit > 0) {
                totalWorth += unit * stack.getAmount();
                totalItems += stack.getAmount();
            }
        }

        MiniMessage mm = plugin.getMiniMessage();
        player.sendMessage(mm.deserialize(config.getMessage("prefix", "") +
                "<gray>Total Inventory Sell Value: <gold>" + plugin.getEconomyHook().format(totalWorth) +
                "</gold> (" + totalItems + " sellable items)</gray>"));
    }
}