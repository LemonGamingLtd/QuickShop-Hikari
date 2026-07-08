package com.ghostchu.quickshop.economy.provider;

import com.ghostchu.quickshop.QuickShop;
import com.ghostchu.quickshop.api.economy.EconomyProvider;
import com.ghostchu.quickshop.api.obj.QUser;
import com.ghostchu.quickshop.common.util.CommonUtil;
import com.ghostchu.quickshop.util.logger.Log;
import ltd.lemongaming.storecurrency.Currency;
import ltd.lemongaming.storecurrency.DStoreCurrency;
import ltd.lemongaming.storecurrency.datastore.CurrencyDataStore;
import ltd.lemongaming.storecurrency.datastore.CurrencyTransaction;
import ltd.lemongaming.storecurrency.datastore.TransactionResult;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class DStoreCurrencyProvider implements EconomyProvider {
  public static final String CURRENCY_MONEY = "money";
  public static final String CURRENCY_LEMONS = "lemons";
  private static final String TRANSACTION_SOURCE = "QuickShop-Hikari";
  private static final long DSTORE_WAIT_SECONDS = 10L;

  private final QuickShop plugin;
  private final EconomyProvider delegate;
  private final boolean dStoreAvailable;
  private String lastError = "No transaction error logged";

  public DStoreCurrencyProvider(@NotNull final QuickShop plugin, @NotNull final EconomyProvider delegate) {
    this.plugin = plugin;
    this.delegate = delegate;
    this.dStoreAvailable = detectDStoreCurrency();
  }

  private static boolean detectDStoreCurrency() {
    if (!Bukkit.getPluginManager().isPluginEnabled("DStoreCurrency")) {
      return false;
    }
    if (!CommonUtil.isClassAvailable("ltd.lemongaming.storecurrency.DStoreCurrency")) {
      return false;
    }
    try {
      return DStoreCurrency.getInstance() != null;
    } catch (final Throwable ignored) {
      return false;
    }
  }

  public boolean isLemonsAvailable() {
    return dStoreAvailable && resolveLemonCurrency() != null;
  }

  @NotNull
  public EconomyProvider getDelegate() {
    return delegate;
  }

  @Override
  public @NotNull String name() {
    return "BuiltIn-DStoreCurrency";
  }

  @Override
  public String providerName() {
    return delegate.providerName() + (dStoreAvailable? " + DStoreCurrency" : "");
  }

  @Override
  public @NotNull String lastError() {
    return lastError;
  }

  @Override
  public boolean valid() {
    return delegate.valid();
  }

  @Override
  public boolean multiCurrency() {
    return dStoreAvailable || delegate.multiCurrency();
  }

  @Override
  public boolean supportsCurrency(final @NotNull String world, final @Nullable String currency) {
    if (isMoneyCurrency(currency)) {
      return delegate.valid();
    }

    if (isLemonCurrency(currency)) {
      return isLemonsAvailable();
    }

    return delegate.supportsCurrency(world, currency);
  }

  @Override
  public @NotNull String format(final @NotNull BigDecimal amount, final @NotNull String world, final @Nullable String currency) {
    if (isLemonCurrency(currency)) {
      final Currency c = resolveLemonCurrency();
      if(c != null) {
        return c.formatTokens(amount) + " " + ChatColor.translateAlternateColorCodes('&', c.currencyDisplayName());
      }
    }
    return delegate.format(amount, world, currency);
  }

  @Override
  public @NotNull BigDecimal balance(final @NotNull QUser user, final @NotNull String world, final @Nullable String currency) {
    if (!isLemonCurrency(currency)) {
      return delegate.balance(user, world, currency);
    }
    final UUID uuid = user.getUniqueId();
    if (uuid == null) {
      return BigDecimal.ZERO;
    }
    final Currency c = resolveLemonCurrency();
    if (c == null) {
      return BigDecimal.ZERO;
    }
    try {
      final TransactionResult<BigDecimal> result = c.getDataStore().getBalance(uuid).get(DSTORE_WAIT_SECONDS, TimeUnit.SECONDS);
      if (result != null && result.transactionSuccess() && result.getResult() != null) {
        return result.getResult();
      }
      return BigDecimal.ZERO;
    } catch (final TimeoutException te) {
      recordError("balance(lemons) timeout", te);
      return BigDecimal.ZERO;
    } catch (final Throwable t) {
      recordError("balance(lemons)", t);
      return BigDecimal.ZERO;
    }
  }

  @Override
  public boolean deposit(final @NotNull QUser user, final @NotNull String world, final @Nullable String currency, final @NotNull BigDecimal amount) {
    if (!isLemonCurrency(currency)) {
      return delegate.deposit(user, world, currency, amount);
    }
    final UUID uuid = user.getUniqueId();
    if (uuid == null) {
      return false;
    }
    if (amount.signum() <= 0) {
      return true;
    }
    final Currency c = resolveLemonCurrency();
    if (c == null) {
      this.lastError = "DStoreCurrency is not installed or the lemons currency is unavailable";
      return false;
    }
    final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
    try {
      final CurrencyTransaction result = c.getDataStore()
              .deposit(offlinePlayer, amount, CurrencyDataStore.DepositType.PLUGIN, TRANSACTION_SOURCE)
              .get(DSTORE_WAIT_SECONDS, TimeUnit.SECONDS);
      if (result != null && result.transactionSuccess()) {
        return true;
      }
      this.lastError = "DStoreCurrency deposit failed: " + (result != null? result.getErrorMessage() : "no response");
      return false;
    } catch (final TimeoutException te) {
      recordError("deposit(lemons) timeout", te);
      return false;
    } catch (final Throwable t) {
      recordError("deposit(lemons)", t);
      return false;
    }
  }

  @Override
  public boolean withdraw(final @NotNull QUser user, final @NotNull String world, final @Nullable String currency, final @NotNull BigDecimal amount) {
    if (!isLemonCurrency(currency)) {
      return delegate.withdraw(user, world, currency, amount);
    }
    final UUID uuid = user.getUniqueId();
    if (uuid == null) {
      return false;
    }
    if (amount.signum() <= 0) {
      return true;
    }
    final Currency c = resolveLemonCurrency();
    if (c == null) {
      this.lastError = "DStoreCurrency is not installed or the lemons currency is unavailable";
      return false;
    }
    try {
      final CurrencyTransaction result = c.getDataStore()
              .withdraw(uuid, amount, CurrencyDataStore.WithdrawType.PLUGIN, TRANSACTION_SOURCE)
              .get(DSTORE_WAIT_SECONDS, TimeUnit.SECONDS);
      if(result != null && result.transactionSuccess()) {
        return true;
      }
      this.lastError = "DStoreCurrency withdraw failed: " + (result != null? result.getErrorMessage() : "no response");
      return false;
    } catch (final TimeoutException te) {
      recordError("withdraw(lemons) timeout", te);
      return false;
    } catch (final Throwable t) {
      recordError("withdraw(lemons)", t);
      return false;
    }
  }

  @Nullable
  private Currency resolveLemonCurrency() {
    if (!dStoreAvailable) {
      return null;
    }
    try {
      return DStoreCurrency.getCurrencyById(CURRENCY_LEMONS).orElse(null);
    } catch (final Throwable t) {
      recordError("resolveLemonCurrency", t);
      return null;
    }
  }

  private void recordError(final String context, final Throwable throwable) {
    this.lastError = context + ": " + throwable.getMessage();
    if (plugin.getSentryErrorReporter() != null) {
      plugin.getSentryErrorReporter().ignoreThrow();
    }
    Log.debug("DStoreCurrencyProvider error in " + context + ": " + throwable.getMessage());
  }

  public static boolean isLemonCurrency(@Nullable final String currency) {
    return currency != null && CURRENCY_LEMONS.equalsIgnoreCase(currency);
  }

  public static boolean isMoneyCurrency(@Nullable final String currency) {
    return currency == null || currency.isEmpty() || CURRENCY_MONEY.equalsIgnoreCase(currency);
  }
}
