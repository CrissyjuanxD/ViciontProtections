package com.viciont.viciontprotections.managers;

import org.bukkit.inventory.ItemStack;

public class ItemUtils {
    public static boolean areSimilarForTrade(ItemStack required, ItemStack given) {
        return given != null && required.getType() == given.getType();
    }
}

/*
Esta clase se encarga de comparar los objetos de tipo ItemStack para ver si son similares en los trades,
pero de momento no se usa. Talvez en un futuro se use para comparar los objetos de tipo ItemStack para añadir items customizados.*/
