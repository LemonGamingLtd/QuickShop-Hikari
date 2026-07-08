package com.ghostchu.quickshop.economy.provider;

import com.ghostchu.quickshop.QuickShop;
import com.ghostchu.quickshop.api.economy.EconomyProvider;
import com.ghostchu.quickshop.api.obj.QUser;
import com.ghostchu.quickshop.common.util.CommonUtil;
import com.ghostchu.quickshop.util.logger.Log;
import ltd.lemongaming.enchants.LGEnchants;
import ltd.lemongaming.enchants.token.TokenManager;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

public class LGEnchantsProvider implements EconomyProvider {
  public static final String CURRENCY_MONEY = "money";
  public static final String CURRENCY_TOKENS = "tokens";
  public static final String CURRENCY_TOKENS_ID = "lgenchants_tokens";
  private static final String TRANSACTION_SOURCE = "QuickShop-Hikari";

  private final QuickShop plugin;
  private final EconomyProvider delegate;
  private final boolean lgEnchantsAvailable;
  private String lastError = "No transaction error logged";

  public LGEnchantsProvider(@NotNull final QuickShop plugin, @NotNull final EconomyProvider delegate) {
    this.plugin = plugin;
    this.delegate = delegate;
    this.lgEnchantsAvailable = detectLGEnchants();
  }

  private static boolean detectLGEnchants() {
    if (!Bukkit.getPluginManager().isPluginEnabled("LGEnchants")) {
      return false;
    }
    if (!CommonUtil.isClassAvailable("ltd.lemongaming.enchants.LGEnchants")) {
      return false;
    }
    try {
      return LGEnchants.getInstance() != null && LGEnchants.getInstance().getTokenManager() != null;
    } catch (final Throwable ignored) {
      return false;
    }
  }

  public boolean isTokensAvailable() {
    return lgEnchantsAvailable;
  }

  @NotNull
  public EconomyProvider getDelegate() {
    return delegate;
  }

  @Override
  public @NotNull String name() {
    return "BuiltIn-LGEnchants";
  }

  @Override
  public String providerName() {
    return delegate.providerName() + (lgEnchantsAvailable? " + LGEnchants" : "");
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
    return lgEnchantsAvailable || delegate.multiCurrency();
  }

  @Override
  public boolean supportsCurrency(final @NotNull String world, final @Nullable String currency) {
    if (isMoneyCurrency(currency)) {
      return delegate.valid();
    }
    if (isTokenCurrency(currency)) {
      return lgEnchantsAvailable;
    }
    return delegate.supportsCurrency(world, currency);
  }

  @Override
  public @NotNull String format(final @NotNull BigDecimal amount, final @NotNull String world, final @Nullable String currency) {
    if (isTokenCurrency(currency) && lgEnchantsAvailable) {
      return TokenManager.getNumberFormat().format(amount.doubleValue()) + " Tokens";
    }
    return delegate.format(amount, world, currency);
  }

  @Override
  public @NotNull BigDecimal balance(final @NotNull QUser user, final @NotNull String world, final @Nullable String currency) {
    if (!isTokenCurrency(currency)) {
      return delegate.balance(user, world, currency);
    }
    final UUID uuid = user.getUniqueId();
    if (uuid == null || !lgEnchantsAvailable) {
      return BigDecimal.ZERO;
    }
    try {
      return BigDecimal.valueOf(tokenManager().getTokens(uuid));
    } catch (final Throwable t) {
      recordError("balance(tokens)", t);
      return BigDecimal.ZERO;
    }
  }

  @Override
  public boolean deposit(final @NotNull QUser user, final @NotNull String world, final @Nullable String currency, final @NotNull BigDecimal amount) {
    if (!isTokenCurrency(currency)) {
      return delegate.deposit(user, world, currency, amount);
    }
    final UUID uuid = user.getUniqueId();
    if (uuid == null) {
      return false;
    }
    if (amount.signum() <= 0) {
      return true;
    }
    if (!lgEnchantsAvailable) {
      this.lastError = "LGEnchants is not installed or enabled";
      return false;
    }
    try {
      tokenManager().addTokens(uuid, amount.doubleValue(), TRANSACTION_SOURCE);
      return true;
    } catch (final Throwable t) {
      recordError("deposit(tokens)", t);
      return false;
    }
  }

  @Override
  public boolean withdraw(final @NotNull QUser user, final @NotNull String world, final @Nullable String currency, final @NotNull BigDecimal amount) {
    if (!isTokenCurrency(currency)) {
      return delegate.withdraw(user, world, currency, amount);
    }
    final UUID uuid = user.getUniqueId();
    if (uuid == null) {
      return false;
    }
    if (amount.signum() <= 0) {
      return true;
    }
    if (!lgEnchantsAvailable) {
      this.lastError = "LGEnchants is not installed or enabled";
      return false;
    }
    try {
      final TokenManager manager = tokenManager();
      if (manager.getTokens(uuid) < amount.doubleValue()) {
        this.lastError = "Insufficient tokens";
        return false;
      }
      manager.removeTokens(uuid, amount.doubleValue(), TRANSACTION_SOURCE);
      return true;
    } catch (final Throwable t) {
      recordError("withdraw(tokens)", t);
      return false;
    }
  }

  private TokenManager tokenManager() {
    return LGEnchants.getInstance().getTokenManager();
  }

  private void recordError(final String context, final Throwable throwable) {
    this.lastError = context + ": " + throwable.getMessage();
    if (plugin.getSentryErrorReporter() != null) {
      plugin.getSentryErrorReporter().ignoreThrow();
    }
    Log.debug("LGEnchantsProvider error in " + context + ": " + throwable.getMessage());
  }

  public static boolean isMoneyCurrency(@Nullable final String currency) {
    return currency == null || currency.isEmpty() || CURRENCY_MONEY.equalsIgnoreCase(currency);
  }

  public static boolean isTokenCurrency(@Nullable final String currency) {
    if (currency == null) {
      return false;
    }
    final String normalized = currency.toLowerCase(Locale.ROOT);
    return normalized.equals(CURRENCY_TOKENS) || normalized.equals(CURRENCY_TOKENS_ID);
  }
}
