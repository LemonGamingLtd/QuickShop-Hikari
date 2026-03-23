package com.ghostchu.quickshop.shop.operation;

import com.ghostchu.quickshop.api.event.inventory.ShopItemDeliveryEvent;
import com.ghostchu.quickshop.api.inventory.InventoryWrapper;
import com.ghostchu.quickshop.api.operation.Operation;
import com.ghostchu.quickshop.util.Util;
import com.ghostchu.quickshop.util.logger.Log;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Operation to add items
 */
public class AddItemOperation implements Operation {

  private final ItemStack item;
  private final int amount;
  private final InventoryWrapper inv;
  private final int itemMaxStackSize;
  private boolean committed;
  private boolean rollback;
  private ItemStack[] snapshot;


  /**
   * Constructor.
   *
   * @param item   item to add
   * @param amount amount to add
   * @param inv    The {@link InventoryWrapper} to add to
   */
  public AddItemOperation(@NotNull final ItemStack item, final int amount, @NotNull final InventoryWrapper inv) {

    this.item = item.clone();
    this.amount = amount;
    this.inv = inv;
    this.itemMaxStackSize = Util.getItemMaxStackSize(item.getType());
  }

  @Override
  public boolean commit() {

    committed = true;
    this.snapshot = inv.createSnapshot();

    if(requiresUniqueProcessing()) {
      return commitWithUniqueProcessing();
    }

    return commitNormal();
  }

  /**
   * Checks if the item requires unique processing by firing a test event.
   *
   * @return true if unique processing is required
   */
  private boolean requiresUniqueProcessing() {

    final ItemStack testItem = this.item.clone();
    testItem.setAmount(1);

    final ShopItemDeliveryEvent event = new ShopItemDeliveryEvent(testItem);
    event.callEvent();

    return !testItem.isSimilar(event.getItem());
  }

  /**
   * Commits the operation with unique processing for each individual item.
   *
   * @return true if successful
   */
  private boolean commitWithUniqueProcessing() {

    int remains = this.amount;
    int lastRemains = -1;

    while(remains > 0) {
      final ItemStack singleItem = this.item.clone();
      singleItem.setAmount(1);

      final ShopItemDeliveryEvent event = new ShopItemDeliveryEvent(singleItem);
      event.callEvent();

      final ItemStack processedItem = event.getItem();
      processedItem.setAmount(1);

      Log.debug("Committing add item operation (unique), remains: " + remains + ", item: " + processedItem);
      final Map<Integer, ItemStack> notSaved = inv.addItem(processedItem);

      if(notSaved.isEmpty()) {
        remains -= 1;
      } else {
        if(remains == lastRemains) {
          return false;
        }
      }
      lastRemains = remains;
    }
    return true;
  }

  /**
   * Commits the operation normally, adding items in stacks.
   *
   * @return true if successful
   */
  private boolean commitNormal() {

    int remains = this.amount;
    int lastRemains = -1;
    final ItemStack target = this.item.clone();

    while(remains > 0) {
      final int stackSize = Math.min(remains, itemMaxStackSize);
      target.setAmount(stackSize);
      Log.debug("Committing add item operation, remains: " + remains + ", stackSize: " + stackSize + ", target: " + target);
      final Map<Integer, ItemStack> notSaved = inv.addItem(target);
      if(notSaved.isEmpty()) {
        remains -= stackSize;
      } else {
        remains -= stackSize - notSaved.entrySet().iterator().next().getValue().getAmount();
      }
      if(remains == lastRemains) {
        return false;
      }
      lastRemains = remains;
    }
    return true;
  }

  @Override
  public boolean isCommitted() {

    return this.committed;
  }

  @Override
  public boolean isRollback() {

    return this.rollback;
  }

  @Override
  public boolean rollback() {

    rollback = true;
    return inv.restoreSnapshot(this.snapshot);
  }

}
