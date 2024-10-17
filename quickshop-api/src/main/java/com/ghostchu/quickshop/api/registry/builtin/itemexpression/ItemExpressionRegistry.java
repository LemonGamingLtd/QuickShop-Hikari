package com.ghostchu.quickshop.api.registry.builtin.itemexpression;

import com.ghostchu.quickshop.api.registry.PrefixAlreadyRegisteredException;
import com.ghostchu.quickshop.api.registry.Registry;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public interface ItemExpressionRegistry extends Registry {

  Set<ItemExpressionHandler> getHandlers();

  boolean match(ItemStack stack, String expression);

  @Nullable
  Component displayName(ItemStack stack);

  void registerHandler(ItemExpressionHandler handler) throws PrefixAlreadyRegisteredException;

  boolean registerHandlerSafely(ItemExpressionHandler handler);

  void unregisterHandler(ItemExpressionHandler handler);

  void unregisterHandler(String prefix);

  void unregisterHandlers(Plugin plugin);
}
