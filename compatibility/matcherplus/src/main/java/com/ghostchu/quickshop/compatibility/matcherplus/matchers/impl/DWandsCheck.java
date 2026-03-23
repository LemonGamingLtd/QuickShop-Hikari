package com.ghostchu.quickshop.compatibility.matcherplus.matchers.impl;
/*
 * QuickShop-Hikari
 * Copyright (C) 2025 Daniel "creatorfromhell" Vidmar
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import com.ghostchu.quickshop.compatibility.matcherplus.matchers.ItemCheck;
import de.tr7zw.nbtapi.NBTItem;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class DWandsCheck implements ItemCheck {

  /**
   * List of all DWands wand type prefixes.
   */
  private static final List<String> WAND_TYPES = Arrays.asList(
      "sell",
      "craft",
      "smelt",
      "stacker",
      "battery",
      "builder"
  );

  /**
   * DWands items require unique UUIDs for each wand, so they need unique processing.
   *
   * @return true, DWands wands always require unique processing
   */
  @Override
  public boolean requiresUniqueProcessing() {
    return true;
  }

  /**
   * Prepares a DWands wand for delivery by regenerating its unique wand ID.
   *
   * @param stack the wand ItemStack to prepare
   *
   * @return the wand with a new unique ID
   */
  @Override
  public @NotNull ItemStack prepareForDelivery(final @NotNull ItemStack stack) {

    final String wandType = getWandType(stack);
    if (wandType == null) {
      return stack;
    }

    final NBTItem nbtItem = new NBTItem(stack, true);
    final String idKey = wandType + "WandId";

    nbtItem.setString(idKey, UUID.randomUUID().toString());

    return nbtItem.getItem();
  }

  /**
   * Check if this check applies to the specified ItemStack.
   *
   * @param stack the ItemStack to check
   * @return true if the item is a DWands wand, otherwise false
   */
  @Override
  public boolean applies(final @Nullable ItemStack stack) {

    if (stack == null || stack.getType() == Material.AIR) {
      return false;
    }

    return getWandType(stack) != null;
  }

  /**
   * Checks if two DWands wand ItemStacks match each other.
   * Wands match if they are the same wand type and have the same durability,
   * regardless of their unique wand ID.
   *
   * @param stack   the first ItemStack to compare
   * @param compare the second ItemStack to compare
   * @return true if the two wands match (same type and durability), false otherwise
   */
  @Override
  public boolean matches(final @Nullable ItemStack stack, final @Nullable ItemStack compare) {

    final String originalType = getWandType(stack);
    final String compareType = getWandType(compare);

    if (originalType == null || !originalType.equals(compareType)) {
      return false;
    }

    final Integer originalDurability = getWandDurability(stack, originalType);
    final Integer compareDurability = getWandDurability(compare, compareType);

    if (originalDurability == null || compareDurability == null) {
      return originalDurability == null && compareDurability == null;
    }

    return originalDurability.equals(compareDurability);
  }

  /**
   * Gets the wand type for an ItemStack, if it is a DWands wand.
   *
   * @param stack the ItemStack to check
   * @return the wand type (e.g., "sell", "craft") or null if not a wand
   */
  @Nullable
  private String getWandType(final @Nullable ItemStack stack) {

    if (stack == null || stack.getType() == Material.AIR) {
      return null;
    }

    final NBTItem nbtItem = new NBTItem(stack);

    for (final String type : WAND_TYPES) {
      final String durabilityKey = type + "WandDurability";
      if (nbtItem.hasTag(durabilityKey)) {
        return type;
      }
    }

    return null;
  }

  /**
   * Gets the wand durability value from an ItemStack.
   *
   * @param stack    the ItemStack to get durability from
   * @param wandType the type of wand
   * @return the durability value, or null if not found
   */
  @Nullable
  private Integer getWandDurability(final @Nullable ItemStack stack, final String wandType) {

    if (stack == null || stack.getType() == Material.AIR) {
      return null;
    }

    final NBTItem nbtItem = new NBTItem(stack);
    final String durabilityKey = wandType + "WandDurability";

    if (nbtItem.hasTag(durabilityKey)) {
      return nbtItem.getInteger(durabilityKey);
    }

    return null;
  }
}