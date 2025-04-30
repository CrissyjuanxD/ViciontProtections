package com.viciont.viciontprotections.managers;

import com.viciont.viciontprotections.ViciontProtections;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

import java.util.ArrayList;
import java.util.List;

public class TradeManager {

    private final ViciontProtections plugin;
    private final ProtectionManager protectionManager;

    public TradeManager(ViciontProtections plugin, ProtectionManager protectionManager) {
        this.plugin = plugin;
        this.protectionManager = protectionManager;
    }

    public void setupAllTrades(Villager villager) {
        List<MerchantRecipe> recipes = new ArrayList<>();
        ConfigurationSection protectionSection = plugin.getConfig().getConfigurationSection("protection_types");

        if (protectionSection == null) {
            plugin.getLogger().warning("No se encontró 'protection_types' en config.yml!");
            return;
        }

        for (String type : protectionSection.getKeys(false)) {
            MerchantRecipe recipe = createTradeRecipe(type);
            if (recipe != null) {
                recipes.add(recipe);
            }
        }

        villager.setRecipes(recipes);
    }

    private MerchantRecipe createTradeRecipe(String protectionType) {
        List<?> costList = plugin.getConfig().getList("protection_types." + protectionType + ".cost");
        if (costList == null) {
            plugin.getLogger().warning("No hay costos para: " + protectionType);
            return null;
        }

        List<ItemStack> ingredients = new ArrayList<>();
        for (Object costEntry : costList) {
            ItemStack item = parseSimpleItem(costEntry);
            if (item != null) {
                ingredients.add(item);
            }
        }

        ItemStack result = protectionManager.createProtectionBlock(protectionType);
        MerchantRecipe recipe = new MerchantRecipe(result, 0, Integer.MAX_VALUE, false);

        if (!ingredients.isEmpty()) {
            recipe.addIngredient(ingredients.get(0));
            if (ingredients.size() > 1) {
                recipe.addIngredient(ingredients.get(1));
            }
        }

        return recipe;
    }

    private ItemStack parseSimpleItem(Object costEntry) {
        String materialPart;
        int amount;

        if (costEntry instanceof String) {
            String[] parts = ((String) costEntry).split(":");
            if (parts.length != 2) return null;
            materialPart = parts[0];
            try {
                amount = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                return null;
            }
        } else if (costEntry instanceof List<?>) {
            List<?> list = (List<?>) costEntry;
            if (list.isEmpty()) return null;
            String[] parts = list.get(0).toString().split(":");
            if (parts.length != 2) return null;
            materialPart = parts[0];
            try {
                amount = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                return null;
            }
        } else {
            return null;
        }

        Material material = Material.matchMaterial(materialPart);
        return material != null ? new ItemStack(material, amount) : null;
    }
}