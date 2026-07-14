package com.ghostchu.quickshop.command.subcommand;

import com.ghostchu.quickshop.QuickShop;
import com.ghostchu.quickshop.api.command.CommandHandler;
import com.ghostchu.quickshop.api.command.CommandParser;
import com.ghostchu.quickshop.api.economy.EconomyProvider;
import com.ghostchu.quickshop.api.shop.Shop;
import com.ghostchu.quickshop.api.shop.ShopAction;
import com.ghostchu.quickshop.economy.provider.LGEnchantsProvider;
import com.ghostchu.quickshop.api.shop.interaction.InteractionClick;
import com.ghostchu.quickshop.shop.SimpleInfo;
import com.ghostchu.quickshop.util.ShopUtil;
import com.ghostchu.quickshop.util.Util;
import com.ghostchu.quickshop.util.logger.Log;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BlockIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.ghostchu.quickshop.listener.PlayerListener.searchShop;

public class SubCommand_Create implements CommandHandler<Player> {

  private final QuickShop plugin;


  public SubCommand_Create(@NotNull final QuickShop plugin) {

    this.plugin = plugin;
  }

  @Override
  public void onCommand(@NotNull final Player sender, @NotNull final String commandLabel, @NotNull final CommandParser parser) {

    final BlockIterator bIt = new BlockIterator(sender, 10);
    final List<String> args = parser.getArgs();
    if(args.isEmpty()) {
      plugin.text().of(sender, "command.wrong-args").send();
      return;
    }

    final String price = args.getFirst();

    String preselectedCurrency = null;
    int itemArgIdx = -1;
    int amountArgIdx = -1;
    if(args.size() >= 2) {
      final String maybeCurrency = args.get(1);
      if(isSelectableCurrency(sender, maybeCurrency)) {
        preselectedCurrency = maybeCurrency;
        if(args.size() >= 3) {
          itemArgIdx = 2;
        }
        if(args.size() >= 4) {
          amountArgIdx = 3;
        }
      } else if(isBlockedCreateCurrency(maybeCurrency)) {
        plugin.text().of(sender, "currency-not-exists").send();
        return;
      } else {
        itemArgIdx = 1;
        if(args.size() >= 3) {
          amountArgIdx = 2;
        }
      }
    }

    final ItemStack item;
    if(itemArgIdx < 0) {
      item = sender.getInventory().getItemInMainHand();
      if(item.getType().isAir()) {
        plugin.text().of(sender, "no-anythings-in-your-hand").send();
        return;
      }
    } else {
      final String matName = args.get(itemArgIdx);
      final Material material = matchMaterial(matName);
      if(material == null) {
        plugin.text().of(sender, "item-not-exist", matName).send();
        return;
      }
      int amount = 1;
      if(amountArgIdx > 0 && plugin.perm().hasPermission(sender, "quickshop.create.stack") && plugin.isAllowStack()) {
        try {
          amount = Integer.parseInt(args.get(amountArgIdx));
        } catch(final NumberFormatException ignored) {
          amount = 1;
        }
        if(amount < 1) {
          amount = 1;
        }
        final int maxSize = Util.getItemMaxStackSize(material);
        if(amount > maxSize) {
          amount = maxSize;
        }
      }
      item = new ItemStack(material, amount);
    }
    Log.debug("Pending task for material: " + item + (preselectedCurrency != null? ", currency=" + preselectedCurrency : ""));

    while(bIt.hasNext()) {
      final Block b = bIt.next();
      if(Util.hasBlockedPdcKey(b)) {
        plugin.text().of(sender, "blocked-container-type").send();
        return;
      }
      if(!Util.canBeShop(b) || !ShopUtil.allowed(b, item)) {
        continue;
      }

      final Map.Entry<@Nullable Shop, @NotNull InteractionClick> search = searchShop(b, sender);
      if(search.getKey() != null) {
        continue;
      }

      // Send creation menu.
      final SimpleInfo info = new SimpleInfo(b.getLocation(), ShopAction.CREATE_SELL, item, b.getRelative(sender.getFacing().getOppositeFace()), false);
      info.setPreselectedCurrency(preselectedCurrency);
      plugin.getShopManager().getInteractiveManager().put(sender.getUniqueId(), info);
      plugin.getShopManager().handleChat(sender, price);
      return;
    }
    plugin.text().of(sender, "not-looking-at-valid-shop-block").send();
  }

  private boolean isSelectableCurrency(@NotNull final Player sender, @NotNull final String candidate) {

    if(candidate.isEmpty()) {
      return false;
    }
    if(isBlockedCreateCurrency(candidate)) {
      return false;
    }
    final EconomyProvider provider = plugin.getEconomyManager().provider();
    if(provider == null) {
      return false;
    }
    if(!provider.multiCurrency()) {
      return false;
    }
    try {
      return provider.supportsCurrency(sender.getWorld().getName(), candidate);
    } catch(final Throwable ignored) {
      return false;
    }
  }

  private boolean isBlockedCreateCurrency(@NotNull final String candidate) {

    final EconomyProvider provider = plugin.getEconomyManager().provider();
    return LGEnchantsProvider.CURRENCY_MONEY.equalsIgnoreCase(candidate)
          && provider instanceof final LGEnchantsProvider lgEnchantsProvider
          && lgEnchantsProvider.isTokensAvailable();
  }

  @Nullable
  private Material matchMaterial(String itemName) {

    itemName = itemName.toUpperCase();
    itemName = itemName.replace(" ", "_");
    final Material material = Material.matchMaterial(itemName);
    if(isValidMaterial(material)) {
      return material;
    }
    return null;
  }

  private boolean isValidMaterial(@Nullable final Material material) {

    return material != null && !material.isAir();
  }

  @NotNull
  @Override
  public List<String> onTabComplete(
          @NotNull final Player sender, @NotNull final String commandLabel, @NotNull final CommandParser parser) {

    final int position = parser.getArgs().size();
    if(position == 1) {
      return Collections.singletonList(plugin.text().of(sender, "tabcomplete.price").plain());
    }
    if(position == 2) {
      final List<String> suggestions = new ArrayList<>();
      suggestions.addAll(availableCurrencySuggestions(sender));
      if(sender.getInventory().getItemInMainHand().getType().isAir()) {
        suggestions.add(plugin.text().of(sender, "tabcomplete.item").plain());
      }
      return suggestions.isEmpty()? Collections.emptyList() : suggestions;
    }
    if(position == 3) {
      final boolean firstIsCurrency = !parser.getArgs().isEmpty() && isSelectableCurrency(sender, parser.getArgs().get(1));
      if(firstIsCurrency) {
        return Collections.singletonList(plugin.text().of(sender, "tabcomplete.item").plain());
      }
      if(sender.getInventory().getItemInMainHand().getType().isAir()) {
        return Collections.singletonList(plugin.text().of(sender, "tabcomplete.amount").plain());
      }
    }
    if(position == 4) {
      final boolean firstIsCurrency = parser.getArgs().size() >= 2 && isSelectableCurrency(sender, parser.getArgs().get(1));
      if(firstIsCurrency) {
        return Collections.singletonList(plugin.text().of(sender, "tabcomplete.amount").plain());
      }
    }
    return Collections.emptyList();
  }

  @NotNull
  private List<String> availableCurrencySuggestions(@NotNull final Player sender) {

    final EconomyProvider provider = plugin.getEconomyManager().provider();
    if(provider == null || !provider.multiCurrency()) {
      return Collections.emptyList();
    }
    final List<String> suggestions = new ArrayList<>();
    final String world = sender.getWorld().getName();
    for(final String candidate : new String[]{"money", "tokens", "lemons"}) {
      try {
        if(isSelectableCurrency(sender, candidate) && provider.supportsCurrency(world, candidate)) {
          suggestions.add(candidate);
        }
      } catch(final Throwable ignored) {
      }
    }
    return suggestions;
  }

}
