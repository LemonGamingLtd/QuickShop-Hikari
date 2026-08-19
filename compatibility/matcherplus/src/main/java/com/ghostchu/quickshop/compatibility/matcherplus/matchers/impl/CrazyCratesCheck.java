package com.ghostchu.quickshop.compatibility.matcherplus.matchers.impl;

import com.badbones69.crazycrates.CrazyCrates;
import com.badbones69.crazycrates.api.objects.Crate;
import com.ghostchu.quickshop.compatibility.matcherplus.matchers.ItemCheck;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class CrazyCratesCheck implements ItemCheck {

  private final String defaultValue = "no-key";

  /**
   * Check if this check applies to the specified ItemStack
   *
   * @param stack the ItemStack to check
   *
   * @return true if the check applies to the ItemStack, otherwise false
   */
  @Override
  public boolean applies(final @Nullable ItemStack stack) {
    return CrazyCrates.getPlugin().getStarter().getCrazyManager().isKey(stack);
  }

  /**
   * Checks if two ItemStack objects match each other.
   *
   * @param stack   the first ItemStack to compare
   * @param compare the second ItemStack to compare
   *
   * @return true if the two ItemStack objects match, false otherwise
   */
  @Override
  public boolean matches(final @Nullable ItemStack stack, final @Nullable ItemStack compare) {
    final Crate originalCrate = CrazyCrates.getPlugin().getStarter().getCrazyManager().getCrateFromKey(stack);
    final Crate compareCrate = CrazyCrates.getPlugin().getStarter().getCrazyManager().getCrateFromKey(compare);

    return Objects.equals(originalCrate, compareCrate);
  }
}
