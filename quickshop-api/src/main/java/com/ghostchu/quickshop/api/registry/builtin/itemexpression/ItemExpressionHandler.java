package com.ghostchu.quickshop.api.registry.builtin.itemexpression;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ItemExpressionHandler extends Comparable<ItemExpressionHandler> {

  @NotNull
  Plugin getPlugin();

  String getPrefix();

  default String getInternalPrefix0() {

    return getPrefix() + ":";
  }

  boolean match(ItemStack stack, String expression);

  @Nullable
  default Component displayName(@NotNull ItemStack stack) {
    return null;
  }

  @Override
  int hashCode();

  @Override
  default int compareTo(@NotNull final ItemExpressionHandler o) {

    return (getClass().getName() + getPrefix()).compareTo(o.getClass().getName() + o.getPrefix());
  }
}
