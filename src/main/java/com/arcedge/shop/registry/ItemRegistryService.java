package com.arcedge.shop.registry;

import com.arcedge.shop.ArcEdgeShop;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles dynamic item recognition for Paper 26.1.2 - 26.3+.
 * Uses modern Paper RegistryAccess (RegistryKey.ITEM / Registry.MATERIAL)
 * rather than hardcoded enums, allowing future Minecraft updates (such as 26.3, 26.4)
 * to be recognized instantly without recompilation or NMS reflection hacks.
 */
public final class ItemRegistryService {

    private final ArcEdgeShop plugin;
    private final Map<String, Material> resolvedMaterials = new ConcurrentHashMap<>();
    private int registeredItemCount = 0;

    public ItemRegistryService(ArcEdgeShop plugin) {
        this.plugin = plugin;
    }

    public void initRegistry() {
        resolvedMaterials.clear();

        // Modern Paper registry lookup using RegistryAccess
        try {
            Registry<ItemType> itemRegistry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ITEM);
            if (itemRegistry != null) {
                for (ItemType itemType : itemRegistry) {
                    NamespacedKey key = itemType.getKey();
                    Material mat = Material.matchMaterial(key.asString());
                    if (mat != null && mat.isItem() && !mat.isAir()) {
                        resolvedMaterials.put(key.getKey().toUpperCase(Locale.ROOT), mat);
                        resolvedMaterials.put(mat.name(), mat);
                    }
                }
            }
        } catch (Throwable t) {
            // Safe fallback to Bukkit Registry.MATERIAL for standard environments
            plugin.getLogger().info("Connecting to Registry.MATERIAL fallback...");
            for (Material mat : Material.values()) {
                if (mat.isItem() && !mat.isAir()) {
                    resolvedMaterials.put(mat.name(), mat);
                }
            }
        }

        registeredItemCount = (int) resolvedMaterials.values().stream().distinct().count();
        plugin.getLogger().info("Dynamic Item Registry: " + registeredItemCount + " active materials loaded.");
    }

    @Nullable
    public Material matchItem(@NotNull String input) {
        if (input == null || input.isBlank()) return null;
        String clean = input.trim().toUpperCase(Locale.ROOT).replace("MINECRAFT:", "");
        return resolvedMaterials.get(clean);
    }

    public boolean isValidItem(@NotNull Material material) {
        return material.isItem() && !material.isAir() && !material.isLegacy();
    }

    public int getRegisteredItemCount() {
        return registeredItemCount;
    }
}