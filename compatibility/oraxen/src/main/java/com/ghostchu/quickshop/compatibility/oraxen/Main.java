package com.ghostchu.quickshop.compatibility.oraxen;

import com.ghostchu.quickshop.QuickShop;
import com.ghostchu.quickshop.api.registry.BuiltInRegistry;
import com.ghostchu.quickshop.api.registry.Registry;
import com.ghostchu.quickshop.api.registry.builtin.itemexpression.ItemExpressionHandler;
import com.ghostchu.quickshop.api.registry.builtin.itemexpression.ItemExpressionRegistry;
import com.ghostchu.quickshop.compatibility.CompatibilityModule;
import io.th0rgal.oraxen.api.OraxenItems;
import io.th0rgal.oraxen.items.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Main extends CompatibilityModule implements ItemExpressionHandler {

  @Override
  public void init() {
    final Registry registry = QuickShop.getInstance().getRegistry().getRegistry(BuiltInRegistry.ITEM_EXPRESSION);
    if(registry instanceof ItemExpressionRegistry itemExpressionRegistry) {
      if(itemExpressionRegistry.registerHandlerSafely(this)) {
        getLogger().info("Register Oraxen ItemExpressionHandler successfully!");
      }
    }
  }

  @Override
  public @NotNull Plugin getPlugin() {
    return this;
  }

  @Override
  public String getPrefix() {
    return "oraxen";
  }

  @Override
  public boolean match(final ItemStack stack, final String expression) {
    return expression.equals(OraxenItems.getIdByItem(stack));
  }

  @Override
  public @Nullable Component displayName(@NotNull ItemStack stack) {
    final ItemBuilder builder = OraxenItems.getBuilderByItem(stack);
    if (builder == null) {
      return ItemExpressionHandler.super.displayName(stack);
    }

    final String displayName = builder.getItemName();
    return displayName != null ? MiniMessage.miniMessage().deserialize(displayName) : ItemExpressionHandler.super.displayName(stack);
  }
}
