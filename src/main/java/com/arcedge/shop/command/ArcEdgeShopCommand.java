package com.arcedge.shop.command;

import com.arcedge.shop.ArcEdgeShop;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class ArcEdgeShopCommand implements BasicCommand {

    private final ArcEdgeShop plugin;

    public ArcEdgeShopCommand(ArcEdgeShop plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();
        MiniMessage mm = plugin.getMiniMessage();

        if (!sender.hasPermission("arcedgeshop.admin")) {
            sender.sendMessage(mm.deserialize(plugin.getConfigManager().getMessage("no-permission", "<red>No permission.</red>")));
            return;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            plugin.getConfigManager().loadAllConfigs();
            plugin.getRegistryService().initRegistry();
            sender.sendMessage(mm.deserialize(plugin.getConfigManager().getMessage("prefix", "") +
                    plugin.getConfigManager().getMessage("reload-success", "<green>Configuration reloaded successfully!</green>")));
            return;
        }

        sender.sendMessage(mm.deserialize("<gold>--- ArcEdgeShop v1.0.0 ---</gold>\n<gray>Use: /arcedgeshop reload</gray>"));
    }

    @Override
    public Collection<String> suggest(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        if (args.length <= 1) {
            return List.of("reload");
        }
        return Collections.emptyList();
    }

    @Override
    public boolean canUse(@NotNull CommandSender sender) {
        return sender.hasPermission("arcedgeshop.admin");
    }

    @Override
    public @Nullable String permission() {
        return "arcedgeshop.admin";
    }
}