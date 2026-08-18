package com.ghostchu.quickshop.compatibility.matcherplus.matchers.impl;

import com.ghostchu.quickshop.compatibility.matcherplus.matchers.ItemCheck;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

public class SeasonalItemsCheck implements ItemCheck {

  private static final NamespacedKey TYPE_KEY = new NamespacedKey("lgreclaim", "seasonal_type");
  private static final NamespacedKey ID_KEY = new NamespacedKey("lgreclaim", "seasonal_id");

  /**
   * Check if this check applies to the specified ItemStack.
   *
   * @param stack the ItemStack to check
   *
   * @return true if the item is a tracked seasonal item, otherwise false
   */
  @Override
  public boolean applies(final @Nullable ItemStack stack) {

    if(stack == null || stack.getItemMeta() == null) {

      return false;
    }
    return stack.getItemMeta().getPersistentDataContainer().has(TYPE_KEY, PersistentDataType.STRING);
  }

  /**
   * Checks if two ItemStack objects match, ignoring only the unique seasonal id.
   *
   * @param stack   the first ItemStack to compare
   * @param compare the second ItemStack to compare
   *
   * @return true if the two items are the same seasonal item, false otherwise
   */
  @Override
  public boolean matches(final @Nullable ItemStack stack, final @Nullable ItemStack compare) {

    if(stack == null || compare == null) {

      return stack == compare;
    }
    return withoutId(stack).isSimilar(withoutId(compare));
  }

  /**
   * Return a clone of the item with the unique seasonal id key removed, so two copies that differ
   * only by that id compare as similar.
   *
   * @param original the item to copy
   *
   * @return a clone without the {@code lgreclaim:seasonal_id} key
   */
  private ItemStack withoutId(final ItemStack original) {

    final ItemStack copy = original.clone();
    final ItemMeta meta = copy.getItemMeta();
    if(meta != null && meta.getPersistentDataContainer().has(ID_KEY, PersistentDataType.STRING)) {

      meta.getPersistentDataContainer().remove(ID_KEY);
      copy.setItemMeta(meta);
    }
    return copy;
  }
}
