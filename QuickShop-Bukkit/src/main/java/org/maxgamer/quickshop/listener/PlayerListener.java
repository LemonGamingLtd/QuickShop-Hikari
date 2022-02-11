/*
 * This file is a part of project QuickShop, the name is PlayerListener.java
 *  Copyright (C) PotatoCraft Studio and contributors
 *
 *  This program is free software: you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License as published by the
 *  Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.maxgamer.quickshop.listener;

import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.ReloadStatus;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BlockIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.maxgamer.quickshop.QuickShop;
import org.maxgamer.quickshop.api.economy.AbstractEconomy;
import org.maxgamer.quickshop.api.inventory.InventoryWrapper;
import org.maxgamer.quickshop.api.shop.Info;
import org.maxgamer.quickshop.api.shop.Shop;
import org.maxgamer.quickshop.api.shop.ShopAction;
import org.maxgamer.quickshop.shop.InteractionController;
import org.maxgamer.quickshop.shop.SimpleInfo;
import org.maxgamer.quickshop.shop.inventory.BukkitInventoryWrapper;
import org.maxgamer.quickshop.util.MsgUtil;
import org.maxgamer.quickshop.util.Util;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class PlayerListener extends AbstractQSListener {
    private final Cache<UUID, Long> cooldownMap = CacheBuilder
            .newBuilder()
            .expireAfterWrite(1,TimeUnit.SECONDS)
            .build();

    public PlayerListener(QuickShop plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onClick(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }

        // ----Adventure dupe click workaround start----
        if (e.getPlayer().getGameMode() == GameMode.ADVENTURE) {
            if(cooldownMap.getIfPresent(e.getPlayer().getUniqueId())!= null) {
                return;
            }
            cooldownMap.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
        }
        // ----Adventure dupe click workaround end----

        Map.Entry<Shop, ClickType> shopSearched = searchShop(e.getClickedBlock(), e.getPlayer());

        if (shopSearched.getKey() == null && shopSearched.getValue() == ClickType.AIR) {
            return;
        }

        InteractionController.Interaction interaction = null;
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (e.getPlayer().isSneaking()) {
                interaction = shopSearched.getValue() == ClickType.SIGN ? InteractionController.Interaction.SNEAKING_RIGHT_CLICK_SIGN : InteractionController.Interaction.SNEAKING_RIGHT_CLICK_SHOPBLOCK;
            } else {
                interaction = shopSearched.getValue() == ClickType.SIGN ? InteractionController.Interaction.STANDING_RIGHT_CLICK_SIGN : InteractionController.Interaction.STANDING_RIGHT_CLICK_SHOPBLOCK;
            }
        } else if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (e.getPlayer().isSneaking()) {
                interaction = shopSearched.getValue() == ClickType.SIGN ? InteractionController.Interaction.SNEAKING_LEFT_CLICK_SIGN : InteractionController.Interaction.SNEAKING_LEFT_CLICK_SHOPBLOCK;
            } else {
                interaction = shopSearched.getValue() == ClickType.SIGN ? InteractionController.Interaction.STANDING_LEFT_CLICK_SIGN : InteractionController.Interaction.STANDING_LEFT_CLICK_SHOPBLOCK;
            }
        }
        if (interaction == null) {
            return;
        }
        Util.debugLog("Click: " + interaction.name());
        Util.debugLog("Behavior Mapping: " + plugin.getInteractionController().getBehavior(interaction).name());
        switch (plugin.getInteractionController().getBehavior(interaction)) {
            case CONTROL_PANEL:
                if(shopSearched.getKey() !=null) {
                    openControlPanel(e.getPlayer(), shopSearched.getKey());
                    e.setCancelled(true);
                    e.setUseInteractedBlock(Event.Result.DENY);
                    e.setUseItemInHand(Event.Result.DENY);
                }
                break;
            case TRADE_INTERACTION:
                if (shopSearched.getKey() == null) {
                    if (createShop(e.getPlayer(), e.getClickedBlock())) {
                        e.setCancelled(true);
                        e.setUseInteractedBlock(Event.Result.DENY);
                        e.setUseItemInHand(Event.Result.DENY);
                        break;
                    }
                } else {
                    if (shopSearched.getKey().isBuying()) {
                        if (sellToShop(e.getPlayer(), shopSearched.getKey(), false, false)) {
                            e.setCancelled(true);
                            e.setUseInteractedBlock(Event.Result.DENY);
                            e.setUseItemInHand(Event.Result.DENY);
                        }
                        break;
                    }
                    if (shopSearched.getKey().isSelling()) {
                        if (buyFromShop(e.getPlayer(), shopSearched.getKey(), false, false)) {
                            e.setCancelled(true);
                            e.setUseInteractedBlock(Event.Result.DENY);
                            e.setUseItemInHand(Event.Result.DENY);
                        }
                        break;
                    }
                }
            case TRADE_DIRECT:
                if (shopSearched.getKey() == null) // No shop here
                {
                    return;
                }
                if (shopSearched.getKey().isBuying()) {
                    if (sellToShop(e.getPlayer(), shopSearched.getKey(), true, false)) {
                        e.setCancelled(true);
                        e.setUseInteractedBlock(Event.Result.DENY);
                        e.setUseItemInHand(Event.Result.DENY);
                    }
                    break;
                }
                if (shopSearched.getKey().isSelling()) {
                    if (sellToShop(e.getPlayer(), shopSearched.getKey(), true, false)) {
                        e.setCancelled(true);
                        e.setUseInteractedBlock(Event.Result.DENY);
                        e.setUseItemInHand(Event.Result.DENY);
                    }
                    break;
                }
            case TRADE_DIRECT_ALL:
                if (shopSearched.getKey().isSelling()) {
                    if (buyFromShop(e.getPlayer(), shopSearched.getKey(), true, true)) {
                        e.setCancelled(true);
                        e.setUseInteractedBlock(Event.Result.DENY);
                        e.setUseItemInHand(Event.Result.DENY);
                    }
                    break;
                }
                if (shopSearched.getKey().isBuying()) {
                    if (buyFromShop(e.getPlayer(), shopSearched.getKey(), true, true)) {
                        e.setCancelled(true);
                        e.setUseInteractedBlock(Event.Result.DENY);
                        e.setUseItemInHand(Event.Result.DENY);
                    }
                    break;
                }
            case NONE:
                break;
        }
    }

    public boolean sellToShop(@NotNull Player p, @Nullable Shop shop, boolean direct, boolean all) {
        if (shop == null) {
            Util.debugLog("Shop null");
            return false;
        }
        if (!shop.isBuying()) {
            return false;
        }

        this.playClickSound(p);
        plugin.getShopManager().sendShopInfo(p, shop);
        shop.setSignText();
        if (shop.getRemainingSpace() == 0) {
            plugin.text().of(p, "purchase-out-of-space", shop.ownerName()).send();
            return true;
        }
        final AbstractEconomy eco = plugin.getEconomy();
        final double price = shop.getPrice();
        final Inventory playerInventory = p.getInventory();
        final String tradeAllWord = plugin.getConfig().getString("shop.word-for-trade-all-items", "all");
        final double ownerBalance = eco.getBalance(shop.getOwner(), shop.getLocation().getWorld(), shop.getCurrency());
        int items = getPlayerCanSell(shop, ownerBalance, price, new BukkitInventoryWrapper(playerInventory));
        Map<UUID, Info> actions = plugin.getShopManager().getActions();
        Info info = new SimpleInfo(shop.getLocation(), ShopAction.PURCHASE_SELL, null, null, shop, false);
        actions.put(p.getUniqueId(), info);
        if(!direct) {
            if (shop.isStackingShop()) {
                plugin.text().of(p, "how-many-sell-stack", Component.text(shop.getItem().getAmount()), Component.text(items), Component.text(tradeAllWord)).send();
            } else {
                plugin.text().of(p, "how-many-sell", Component.text(items), Component.text(tradeAllWord)).send();
            }
        }else{
            if(all){
                plugin.getShopManager().actionBuying(p.getUniqueId(), new BukkitInventoryWrapper(p.getInventory()), eco, info, shop, shop.getShopStackingAmount());
            }else{
                plugin.getShopManager().actionBuying(p.getUniqueId(), new BukkitInventoryWrapper(p.getInventory()), eco, info, shop, buyingShopAllCalc(eco, shop,p));
            }
        }
        return true;
    }

    private int sellingShopAllCalc(@NotNull AbstractEconomy eco, @NotNull Shop shop, @NotNull Player p) {
        int amount;
        int shopHaveItems = shop.getRemainingStock();
        int invHaveSpaces = Util.countSpace(new BukkitInventoryWrapper(p.getInventory()), shop);
        if (shop.isAlwaysCountingContainer() || !shop.isUnlimited()) {
            amount = Math.min(shopHaveItems, invHaveSpaces);
        } else {
            // should check not having items but having empty slots, cause player is trying to buy
            // items from the shop.
            amount = Util.countSpace(new BukkitInventoryWrapper(p.getInventory()), shop);
        }
        // typed 'all', check if player has enough money than price * amount
        double price = shop.getPrice();
        double balance = eco.getBalance(p.getUniqueId(), shop.getLocation().getWorld(),
                shop.getCurrency());
        amount = Math.min(amount, (int) Math.floor(balance / price));
        if (amount < 1) { // typed 'all' but the auto set amount is 0
            // when typed 'all' but player can't buy any items
            if ((shop.isAlwaysCountingContainer() || !shop.isUnlimited()) && shopHaveItems < 1) {
                // but also the shop's stock is 0
                plugin.text().of(p, "shop-stock-too-low",
                        Component.text(shop.getRemainingStock()),
                        MsgUtil.getTranslateText(shop.getItem())).send();
                return 0;
            } else {
                // when if player's inventory is full
                if (invHaveSpaces <= 0) {
                    plugin.text().of(p, "not-enough-space",
                            Component.text(invHaveSpaces)).send();
                    return 0;
                }
                plugin.text().of(p, "you-cant-afford-to-buy",
                        LegacyComponentSerializer.legacySection().deserialize(Objects.requireNonNull(
                                plugin.getShopManager().format(price, shop.getLocation().getWorld(),
                                        shop.getCurrency()))),
                        LegacyComponentSerializer.legacySection().deserialize(Objects.requireNonNull(
                                plugin.getShopManager().format(balance, shop.getLocation().getWorld(),
                                        shop.getCurrency())))).send();
            }
            return 0;
        }
        return amount;
    }

    private int buyingShopAllCalc(@NotNull AbstractEconomy eco, @NotNull Shop shop, @NotNull Player p) {
        int amount;
        int shopHaveSpaces =
                Util.countSpace(shop.getInventory(), shop);
        int invHaveItems = Util.countItems(new BukkitInventoryWrapper(p.getInventory()), shop);
        // Check if shop owner has enough money
        double ownerBalance = eco
                .getBalance(shop.getOwner(), shop.getLocation().getWorld(),
                        shop.getCurrency());
        int ownerCanAfford;
        if (shop.getPrice() != 0) {
            ownerCanAfford = (int) (ownerBalance / shop.getPrice());
        } else {
            ownerCanAfford = Integer.MAX_VALUE;
        }
        if (shop.isAlwaysCountingContainer() || !shop.isUnlimited()) {
            amount = Math.min(shopHaveSpaces, invHaveItems);
            amount = Math.min(amount, ownerCanAfford);
        } else {
            amount = Util.countItems(new BukkitInventoryWrapper(p.getInventory()), shop);
            // even if the shop is unlimited, the config option pay-unlimited-shop-owners is set to
            // true,
            // the unlimited shop owner should have enough money.
            if (plugin.getConfig().getBoolean("shop.pay-unlimited-shop-owners")) {
                amount = Math.min(amount, ownerCanAfford);
            }
        }
        if (amount < 1) { // typed 'all' but the auto set amount is 0
            if (shopHaveSpaces == 0) {
                // when typed 'all' but the shop doesn't have any empty space
                plugin.text().of(p, "shop-has-no-space", Component.text(shopHaveSpaces),
                        MsgUtil.getTranslateText(shop.getItem())).send();
                return 0;
            }
            if (ownerCanAfford == 0
                    && (!shop.isUnlimited()
                    || plugin.getConfig().getBoolean("shop.pay-unlimited-shop-owners"))) {
                // when typed 'all' but the shop owner doesn't have enough money to buy at least 1
                // item (and shop isn't unlimited or pay-unlimited is true)
                plugin.text().of(p, "the-owner-cant-afford-to-buy-from-you",
                        LegacyComponentSerializer.legacySection().deserialize(Objects.requireNonNull(
                                plugin.getShopManager().format(shop.getPrice(), shop.getLocation().getWorld(),
                                        shop.getCurrency()))),
                       LegacyComponentSerializer.legacySection().deserialize(Objects.requireNonNull(
                               plugin.getShopManager().format(ownerBalance, shop.getLocation().getWorld(),
                                       shop.getCurrency())))).send();
                return 0;
            }
            // when typed 'all' but player doesn't have any items to sell
            plugin.text().of(p, "you-dont-have-that-many-items",
                    Component.text(amount),
                    MsgUtil.getTranslateText(shop.getItem())).send();
            return 0;
        }
        return amount;
    }

    public boolean buyFromShop(@NotNull Player p, @Nullable Shop shop, boolean direct, boolean all) {
        if (shop == null) {
            Util.debugLog("Shop null");
            return false;
        }
        if (!shop.isSelling()) {
            return false;
        }
        this.playClickSound(p);
        plugin.getShopManager().sendShopInfo(p, shop);
        shop.setSignText();
        if (shop.getRemainingStock() == 0) {
            plugin.text().of(p, "purchase-out-of-stock", shop.ownerName()).send();
            return true;
        }
        final AbstractEconomy eco = plugin.getEconomy();
        final double price = shop.getPrice();
        final Inventory playerInventory = p.getInventory();
        final String tradeAllWord = plugin.getConfig().getString("shop.word-for-trade-all-items", "all");
        Map<UUID, Info> actions = plugin.getShopManager().getActions();
        Info info = new SimpleInfo(shop.getLocation(), ShopAction.PURCHASE_BUY, null, null, shop, false);
        actions.put(p.getUniqueId(), info);
        final double traderBalance = eco.getBalance(p.getUniqueId(), shop.getLocation().getWorld(), shop.getCurrency());
        int itemAmount = getPlayerCanBuy(shop, traderBalance, price, new BukkitInventoryWrapper(playerInventory));
        if(!direct) {
            if (shop.isStackingShop()) {
                plugin.text().of(p, "how-many-buy-stack", Component.text(shop.getItem().getAmount()), Component.text(itemAmount), Component.text(tradeAllWord)).send();
            } else {
                plugin.text().of(p, "how-many-buy", Component.text(itemAmount), Component.text(tradeAllWord)).send();
            }
        }else{
            if(all){
                plugin.getShopManager().actionSelling(p.getUniqueId(), new BukkitInventoryWrapper(p.getInventory()), eco, info, shop, sellingShopAllCalc(eco, shop,p));
            }else{
                plugin.getShopManager().actionSelling(p.getUniqueId(), new BukkitInventoryWrapper(p.getInventory()), eco, info, shop, shop.getShopStackingAmount());
            }
        }
        return true;
    }

    public boolean createShop(@NotNull Player player, @Nullable Block block) {
        if (block == null) {
            return false; // This shouldn't happen because we have checked action type.
        }
        if (player.getGameMode() != GameMode.SURVIVAL) {
            return false; // Only survival :)
        }
        if (player.getInventory().getItemInMainHand().getType().isAir()) {
            return false; // Air cannot be used for trade
        }
        ItemStack stack = player.getInventory().getItemInMainHand();
        ShopAction action = null;
        if (player.hasPermission("quickshop.create.sell")) {
            action = ShopAction.CREATE_SELL;
        } else if (player.hasPermission("quickshop.create.buy")) {
            action = ShopAction.CREATE_BUY;
        }
        if (action == null) {
            // No permission
            return false;
        }
        // Double chest creation permission check
        if (Util.isDoubleChest(block.getBlockData()) &&
                !QuickShop.getPermissionManager().hasPermission(player, "quickshop.create.double")) {
            plugin.text().of(player, "no-double-chests").send();
            return true;
        }
        // Blacklist check
        if (Util.isBlacklisted(stack)
                && !QuickShop.getPermissionManager()
                .hasPermission(player, "quickshop.bypass." + stack.getType().name())) {
            plugin.text().of(player, "blacklisted-item").send();
            return true;
        }
        // Check if had enderchest shop creation permission
        if (block.getType() == Material.ENDER_CHEST
                && !QuickShop.getPermissionManager().hasPermission(player, "quickshop.create.enderchest")) {
            return false;
        }
        // Check if block is a wall sign
        if (Util.isWallSign(block.getType())) {
            return false;
        }
        // Finds out where the sign should be placed for the shop
        Block last = null;
        final Location from = player.getLocation().clone();
        from.setY(block.getY());
        from.setPitch(0);
        final BlockIterator bIt = new BlockIterator(from, 0, 7);
        while (bIt.hasNext()) {
            final Block n = bIt.next();
            if (n.equals(block)) {
                break;
            }
            last = n;
        }
        // Send creation menu.
        final SimpleInfo info = new SimpleInfo(block.getLocation(), action, stack, last, false);
        plugin.getShopManager().getActions().put(player.getUniqueId(), info);
        plugin.text().of(player, "how-much-to-trade-for", MsgUtil.getTranslateText(stack), Component.text(
                plugin.isAllowStack() &&
                        QuickShop.getPermissionManager().hasPermission(player, "quickshop.create.stacks")
                        ? stack.getAmount() : 1)).send();
        return true;
    }

    @NotNull
    public Map.Entry<@Nullable Shop, @NotNull ClickType> searchShop(@Nullable Block b, @NotNull Player p) {
        if (b == null) {
            return new AbstractMap.SimpleEntry<>(null, ClickType.AIR);
        }
        Shop shop = plugin.getShopManager().getShop(b.getLocation());
        // If that wasn't a shop, search nearby shops
        if (shop == null) {
            final Block attached;
            if (Util.isWallSign(b.getType())) {
                attached = Util.getAttached(b);
                if (attached != null) {
                    shop = plugin.getShopManager().getShop(attached.getLocation());
                    return new AbstractMap.SimpleImmutableEntry<>(shop, ClickType.SIGN);
                }
            } else if (Util.isDoubleChest(b.getBlockData())) {
                attached = Util.getSecondHalf(b);
                if (attached != null) {
                    Shop secondHalfShop = plugin.getShopManager().getShop(attached.getLocation());
                    if (secondHalfShop != null && !p.getUniqueId().equals(secondHalfShop.getOwner())) {
                        // If player not the owner of the shop, make him select the second half of the
                        // shop
                        // Otherwise owner will be able to create new double chest shop
                        shop = secondHalfShop;
                    }
                }
            }
        }
        return new AbstractMap.SimpleImmutableEntry<>(shop, ClickType.SHOPBLOCK);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAdventureClick(PlayerAnimationEvent event) {
        if (event.getPlayer().getGameMode() != GameMode.ADVENTURE) {
            return;
        }
        // ----Adventure dupe click workaround start----
        if (event.getPlayer().getGameMode() == GameMode.ADVENTURE) {
            if(cooldownMap.getIfPresent(event.getPlayer().getUniqueId())!= null) {
                return;
            }
            cooldownMap.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
        }
        // ----Adventure dupe click workaround end----
        Block focused = event.getPlayer().getTargetBlock(null, 5);
        PlayerInteractEvent interactEvent
                = new PlayerInteractEvent(event.getPlayer(),
                focused.getType() == Material.AIR ? Action.LEFT_CLICK_AIR : Action.LEFT_CLICK_BLOCK,
                event.getPlayer().getInventory().getItemInMainHand(),
                focused,
                event.getPlayer().getFacing().getOppositeFace());
        onClick(interactEvent);
    }

    private void playClickSound(@NotNull Player player) {
        if (plugin.getConfig().getBoolean("effect.sound.onclick")) {
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 80.f, 1.0f);
        }
    }

    private void openControlPanel(@NotNull Player p, @NotNull Shop shop) {
        if (shop.getOwner().equals(p.getUniqueId()) || QuickShop.getPermissionManager().hasPermission(p, "quickshop.other.control")) {
            MsgUtil.sendControlPanelInfo(p, shop);
            this.playClickSound(p);
            shop.setSignText();
        }
    }

    private int getPlayerCanBuy( @NotNull Shop shop, double traderBalance, double price, @NotNull InventoryWrapper playerInventory) {
        boolean isContainerCountingNeeded = shop.isUnlimited() && !shop.isAlwaysCountingContainer();
        if (shop.isFreeShop()) { // Free shop
            return isContainerCountingNeeded ? Util.countSpace(playerInventory, shop) : Math.min(shop.getRemainingStock(), Util.countSpace(playerInventory, shop));
        }
        int itemAmount = Math.min(Util.countSpace(playerInventory, shop), (int) Math.floor(traderBalance / price));
        if (!isContainerCountingNeeded) {
            itemAmount = Math.min(itemAmount, shop.getRemainingStock());
        }
        if (itemAmount < 0) {
            itemAmount = 0;
        }
        return itemAmount;
    }

    private int getPlayerCanSell( @NotNull Shop shop, double ownerBalance, double price, @NotNull  InventoryWrapper playerInventory) {
        boolean isContainerCountingNeeded = shop.isUnlimited() && !shop.isAlwaysCountingContainer();
        if (shop.isFreeShop()) {
            return isContainerCountingNeeded ? Util.countItems(playerInventory, shop) : Math.min(shop.getRemainingSpace(), Util.countItems(playerInventory, shop));
        }

        int items = Util.countItems(playerInventory, shop);
        final int ownerCanAfford = (int) (ownerBalance / price);
        if (!isContainerCountingNeeded) {
            // Amount check player amount and shop empty slot
            items = Math.min(items, shop.getRemainingSpace());
            // Amount check player selling item total cost and the shop owner's balance
            items = Math.min(items, ownerCanAfford);
        } else if (plugin.getConfig().getBoolean("shop.pay-unlimited-shop-owners")) {
            // even if the shop is unlimited, the config option pay-unlimited-shop-owners is set to
            // true,
            // the unlimited shop owner should have enough money.
            items = Math.min(items, ownerCanAfford);
        }
        if (items < 0) {
            items = 0;
        }
        return items;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent e) {
        try {
            Location location;
            //for strange NPE from spigot API fix
            //noinspection ConstantConditions
            if (e.getInventory() == null) {
                return;
            }
            location = e.getInventory().getLocation();
            if (location == null) {
                return; /// ignored as workaround, GH-303
            }
            //Cannot tick distance manager while unloading playerchunks
            //https://github.com/pl3xgaming/Purpur/issues/631
            if (!Util.isLoaded(location)) {
                return;
            }
            final Shop shop = plugin.getShopManager().getShopIncludeAttached(location);
            if (shop != null) {
                shop.setSignText();
            }
        } catch (NullPointerException ignored) {
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Util.debugLog("Player " + e.getPlayer().getName() + " using new locale " + e.getPlayer().getLocale() + ": " + LegacyComponentSerializer.legacySection().serialize(plugin.text().of(e.getPlayer(), "file-test").forLocale(e.getPlayer().getLocale())));
        plugin.getDatabaseHelper().setPlayerLocale(e.getPlayer().getUniqueId(), e.getPlayer().getLocale());
        // Notify the player any messages they were sent
        if (plugin.getConfig().getBoolean("shop.auto-fetch-shop-messages")) {
            MsgUtil.flush(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onJoin(PlayerLocaleChangeEvent e) {
        Util.debugLog("Player " + e.getPlayer().getName() + " using new locale " + e.getLocale() + ": " + LegacyComponentSerializer.legacySection().serialize(plugin.text().of(e.getPlayer(), "file-test").forLocale(e.getLocale())));
        plugin.getDatabaseHelper().setPlayerLocale(e.getPlayer().getUniqueId(), e.getPlayer().getLocale());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent e) {
        // Remove them from the menu
        plugin.getShopManager().getActions().remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        onMove(new PlayerMoveEvent(e.getPlayer(), e.getFrom(), e.getTo()));
    }

    /*
     * Cancels the menu for broken shop block
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerBreakShopCreationChest(BlockBreakEvent event) {
        @Nullable Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        Map<UUID, Info> actionMap = plugin.getShopManager().getActions();
        final Info info = actionMap.get(player.getUniqueId());
        if (info != null && info.getLocation().equals(event.getBlock().getLocation())) {
            actionMap.remove(player.getUniqueId());
            if (info.getAction().isTrading()) {
                plugin.text().of(player, "shop-purchase-cancelled").send();
            } else if (info.getAction().isCreating()) {
                plugin.text().of(player, "shop-creation-cancelled").send();
            }
        }
    }

    /*
     * Waits for a player to move too far from a shop, then cancels the menu.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        final Info info = plugin.getShopManager().getActions().get(e.getPlayer().getUniqueId());
        if (info == null) {
            return;
        }
        final Player p = e.getPlayer();
        final Location loc1 = info.getLocation();
        final Location loc2 = p.getLocation();
        if (loc1.getWorld() != loc2.getWorld() || loc1.distanceSquared(loc2) > 25) {
            if (info.getAction().isTrading()) {
                plugin.text().of(p, "shop-purchase-cancelled").send();
            } else if (info.getAction().isCreating()) {
                plugin.text().of(p, "shop-creation-cancelled").send();
            }
            Util.debugLog(p.getName() + " too far with the shop location.");
            plugin.getShopManager().getActions().remove(p.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDyeing(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getItem() == null || !Util.isDyes(e.getItem().getType())) {
            return;
        }
        final Block block = e.getClickedBlock();
        if (block == null || !Util.isWallSign(block.getType())) {
            return;
        }
        final Block attachedBlock = Util.getAttached(block);
        if (attachedBlock == null || plugin.getShopManager().getShopIncludeAttached(attachedBlock.getLocation()) == null) {
            return;
        }
        e.setCancelled(true);
        Util.debugLog("Disallow " + e.getPlayer().getName() + " dye the shop sign.");
    }

    /**
     * Callback for reloading
     *
     * @return Reloading success
     */
    @Override
    public ReloadResult reloadModule() {
        return ReloadResult.builder().status(ReloadStatus.SUCCESS).build();
    }

    enum ClickType {
        SHOPBLOCK,
        SIGN,
        AIR
    }
}
