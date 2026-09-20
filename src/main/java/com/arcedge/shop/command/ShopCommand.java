package com.arcedge.shop.command;

import com.arcedge.shop.ArcEdgeShop;
import com.arcedge.shop.gui.ShopGUI;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public final class ShopCommand implements BasicCommand {

    private final ArcEdgeShop plugin;

    public ShopCommand(ArcEdgeShop plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be run by players in-game.");
            return;
        }

        if (!player.hasPermission("arcedgeshop.use")) {
            MiniMessage mm = plugin.getMiniMessage();
            player.sendMessage(mm.deserialize(plugin.getConfigManager().getMessage("no-permission", "<red>No permission.</red>")));
            return;
        }

        // Open clean, secure ArcEdge shop GUI
        new ShopGUI(plugin, player).open();
    }

    @Override
    public Collection<String> suggest(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        return Collections.emptyList();
    }

    @Override
    public boolean canUse(@NotNull CommandSender sender) {
        return sender.hasPermission("arcedgeshop.use");
    }

    @Override
    public @Nullable String permission() {
        return "arcedgeshop.use";
    }
}