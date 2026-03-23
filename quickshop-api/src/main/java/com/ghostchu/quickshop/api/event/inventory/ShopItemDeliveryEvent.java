package com.ghostchu.quickshop.api.event.inventory;

import com.ghostchu.quickshop.api.event.AbstractQSEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Called for each individual item being delivered during a shop transaction.
 */
public class ShopItemDeliveryEvent extends AbstractQSEvent {

  @NotNull
  private ItemStack item;

  /**
   * Creates a new ShopItemDeliveryEvent.
   *
   * @param item the item being delivered (single unit, amount will be 1)
   */
  public ShopItemDeliveryEvent(@NotNull final ItemStack item) {
    this.item = item;
  }

  /**
   * Gets the item being delivered.
   *
   * @return the item
   */
  @NotNull
  public ItemStack getItem() {
    return item;
  }

  /**
   * Sets the item to be delivered. Use this to modify the item before delivery.
   *
   * @param item the modified item
   */
  public void setItem(@NotNull final ItemStack item) {
    this.item = item;
  }
}