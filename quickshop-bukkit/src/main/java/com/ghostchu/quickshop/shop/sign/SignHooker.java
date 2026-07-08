package com.ghostchu.quickshop.shop.sign;

import com.ghostchu.quickshop.QuickShop;
import com.ghostchu.quickshop.api.shop.Shop;
import com.ghostchu.quickshop.listener.AbstractQSListener;
import com.ghostchu.quickshop.util.Util;
import com.ghostchu.quickshop.util.logger.Log;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class SignHooker extends AbstractQSListener {

  private static final int LOOK_DISTANCE = 5;
  private static final int SCROLL_VISIBLE_CHARS = 12;
  private static final int SCROLL_PADDING = 3;
  private static final long SCROLL_PERIOD_TICKS = 10L;
  private static final char LEGACY_COLOR_CHAR = '§';
  private static final BlockFace[] SHOP_LOOKUP_FACES = {
          BlockFace.NORTH,
          BlockFace.SOUTH,
          BlockFace.EAST,
          BlockFace.WEST,
          BlockFace.UP,
          BlockFace.DOWN
  };
  private final Map<UUID, ScrollState> scrollingPlayers = new HashMap<>();
  private final WrappedTask scrollTask;

  public SignHooker(final QuickShop plugin) {
    super(plugin);
    this.scrollTask = QuickShop.folia().getScheduler().runTimer(this::tickScrollingSigns, SCROLL_PERIOD_TICKS, SCROLL_PERIOD_TICKS);
  }

  @EventHandler
  public void onPlayerChunkLoadEvent(final PlayerChunkLoadEvent event) {
    final Player player = event.getPlayer();
    final Chunk chunk = event.getChunk();

    final Map<Location, Shop> shops = plugin.getShopManager().getShops(player.getWorld().getName(), chunk.getX(), chunk.getZ());
    if (shops != null) {
      shops.forEach((loc, shop)->updatePerPlayerShopSign(player, loc, shop));
    }
  }

  public void updatePerPlayerShopSign(final Player player, final Location location, final Shop shop) {

    Util.ensureThread(false);
    if(!shop.isLoaded()) {
      return;
    }
    Log.debug("Updating per-player packet sign: Player=" + player.getName() + ", Location=" + location + ", Shop=" + shop.getShopId());
    final List<Component> lines = shop.getSignText(plugin.getTextManager().findRelativeLanguages(player));
    for(final Sign sign : shop.getSigns()) {

      plugin.platform().sendSignTextChange(player, sign, plugin.getConfig().getBoolean("shop.sign-glowing"), lines);
    }
  }

  private void tickScrollingSigns() {
    Util.ensureThread(false);
    for (final Player player : plugin.getJavaPlugin().getServer().getOnlinePlayers()) {
      updateScrollingSign(player);
    }
  }

  private void updateScrollingSign(@NotNull final Player player) {
    final Block target = player.getTargetBlockExact(LOOK_DISTANCE);
    if( target == null || !(target.getState(false) instanceof final Sign sign)) {
      restorePreviousScrollingSign(player);
      return;
    }

    final Shop shop = findShopForSign(sign);
    if (shop == null || !shop.isLoaded()) {
      restorePreviousScrollingSign(player);
      return;
    }

    final Component itemNameComponent = Util.getItemStackName(shop.getItem());
    final String itemName = PlainTextComponentSerializer.plainText().serialize(itemNameComponent);
    if (itemName.length() <= SCROLL_VISIBLE_CHARS) {
      restorePreviousScrollingSign(player);
      return;
    }

    final List<Component> lines = new ArrayList<>(shop.getSignText(plugin.getTextManager().findRelativeLanguages(player)));
    final int itemLine = itemLineIndex(shop);
    if (itemLine < 0 || itemLine >= lines.size()) {
      restorePreviousScrollingSign(player);
      return;
    }

    final int offset = scrollOffset(player, shop, sign, itemName);
    lines.set(itemLine, LegacyComponentSerializer.legacySection().deserialize(scrollWindow(itemNameComponent, offset)));
    plugin.platform().sendSignTextChange(player, sign, plugin.getConfig().getBoolean("shop.sign-glowing"), lines);
  }

  private void restorePreviousScrollingSign(@NotNull final Player player) {
    final ScrollState previous = scrollingPlayers.remove(player.getUniqueId());
    if (previous == null) {
      return;
    }
    final Shop previousShop = plugin.getShopManager().getShop(previous.shopId());
    if (previousShop == null || !previousShop.isLoaded()) {
      return;
    }
    updatePerPlayerShopSign(player, previous.signLocation(), previousShop);
  }

  @Nullable
  private Shop findShopForSign(@NotNull final Sign sign) {
    for (final BlockFace face : SHOP_LOOKUP_FACES) {
      final Shop shop = plugin.getShopManager().getShopIncludeAttached(sign.getBlock().getRelative(face).getLocation());
      if (shop == null || !shop.isLoaded()) {
        continue;
      }
      for (final Sign shopSign : shop.getSigns()) {
        if (sameBlock(shopSign.getLocation(), sign.getLocation())) {
          return shop;
        }
      }
    }
    return null;
  }

  private int itemLineIndex(@NotNull final Shop shop) {
    final List<String> template = plugin.getShopManager().shopLayoutProvider().layoutTemplate(shop);
    for(int i = 0; i < template.size(); i++) {
      if("item".equals(template.get(i).toLowerCase(Locale.ROOT))) {
        return i;
      }
    }
    return 2;
  }

  private int scrollOffset(@NotNull final Player player, @NotNull final Shop shop, @NotNull final Sign sign, @NotNull final String itemName) {
    final ScrollState current = new ScrollState(shop.getShopId(), sign.getLocation().toBlockLocation(), itemName.length(), 0);
    final ScrollState previous = scrollingPlayers.get(player.getUniqueId());
    if(previous == null || previous.shopId() != current.shopId() || !sameBlock(previous.signLocation(), current.signLocation()) || previous.itemNameLength() != current.itemNameLength()) {
      scrollingPlayers.put(player.getUniqueId(), current);
      return current.offset();
    }

    final int nextOffset = (previous.offset() + 1) % (itemName.length() + SCROLL_PADDING);
    scrollingPlayers.put(player.getUniqueId(), new ScrollState(previous.shopId(), previous.signLocation(), previous.itemNameLength(), nextOffset));
    return nextOffset;
  }

  @NotNull
  private String scrollWindow(@NotNull final Component itemName, final int offset) {
    final List<FormattedCharacter> characters = formattedCharacters(itemName);
    final int cycleLength = characters.size() + SCROLL_PADDING;
    final int safeOffset = Math.floorMod(offset, cycleLength);
    final StringBuilder builder = new StringBuilder();
    String lastFormat = "";

    for(int i = 0; i < SCROLL_VISIBLE_CHARS; i++) {
      final int index = (safeOffset + i) % cycleLength;
      if(index >= characters.size()) {
        builder.append(' ');
        continue;
      }
      final FormattedCharacter character = characters.get(index);
      if(!character.format().equals(lastFormat)) {
        builder.append(character.format());
        lastFormat = character.format();
      }
      builder.append(character.value());
    }
    return builder.toString();
  }

  @NotNull
  private List<FormattedCharacter> formattedCharacters(@NotNull final Component component) {
    final String legacy = LegacyComponentSerializer.legacySection().serialize(component);
    final List<FormattedCharacter> characters = new ArrayList<>();
    String activeFormat = "";

    for(int i = 0; i < legacy.length(); i++) {
      final char current = legacy.charAt(i);
      if(current == LEGACY_COLOR_CHAR && i + 1 < legacy.length()) {
        final LegacySequence sequence = readLegacySequence(legacy, i);
        activeFormat = updateActiveFormat(activeFormat, sequence);
        i += sequence.sequence().length() - 1;
        continue;
      }
      characters.add(new FormattedCharacter(current, activeFormat));
    }

    return characters;
  }

  @NotNull
  private LegacySequence readLegacySequence(@NotNull final String legacy, final int index) {
    if(index + 13 < legacy.length() && Character.toLowerCase(legacy.charAt(index + 1)) == 'x') {
      final String hexSequence = legacy.substring(index, index + 14);
      boolean validHexSequence = true;
      for(int i = 2; i < hexSequence.length(); i += 2) {
        if(hexSequence.charAt(i) != LEGACY_COLOR_CHAR) {
          validHexSequence = false;
          break;
        }
      }
      if(validHexSequence) {
        return new LegacySequence(hexSequence, true);
      }
    }

    final String sequence = legacy.substring(index, index + 2);
    final char code = Character.toLowerCase(sequence.charAt(1));
    return new LegacySequence(sequence, isColorCode(code) || code == 'r');
  }

  @NotNull
  private String updateActiveFormat(@NotNull final String activeFormat, @NotNull final LegacySequence sequence) {
    if(sequence.sequence().equalsIgnoreCase("§r")) {
      return "";
    }
    if(sequence.resetsFormatting()) {
      return sequence.sequence();
    }
    return activeFormat + sequence.sequence();
  }

  private boolean isColorCode(final char code) {
    return (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f');
  }

  private boolean sameBlock(@NotNull final Location first, @NotNull final Location second) {
    return first.getBlockX() == second.getBlockX()
          && first.getBlockY() == second.getBlockY()
          && first.getBlockZ() == second.getBlockZ()
          && Objects.equals(first.getWorld(), second.getWorld());
  }

  public void updatePerPlayerShopSignBroadcast(final Location location, final Shop shop) {

    final World world = shop.bukkitLocation().getWorld();
    if(world == null) {
      return;
    }
    QuickShop.folia().getScheduler().runAtLocationLater(shop.bukkitLocation(), ()->{
      final Collection<Player> nearbyPlayers = getPlayersTrackingChunk(world, shop.bukkitLocation());
      for(final Player nearbyPlayer : nearbyPlayers) {
        updatePerPlayerShopSign(nearbyPlayer, location, shop);
      }
    }, 1);
  }

  private static final boolean CAN_USE_PLAYERS_SEEING_CHUNK;

  // getPlayersSeeingChunk was added in 1.20.6, so check for the existence of the method and fallback to a worse method on earlier versions.
  static {

    boolean exists = false;
    try {
      //noinspection ConstantValue
      exists = World.class.getMethod("getPlayersSeeingChunk", int.class, int.class) != null;
    } catch (ReflectiveOperationException ignored) {}

    CAN_USE_PLAYERS_SEEING_CHUNK = exists;
  }

  private Collection<Player> getPlayersTrackingChunk(final World world, final Location locationForChunk) {

    if (CAN_USE_PLAYERS_SEEING_CHUNK) {
      return world.getPlayersSeeingChunk(locationForChunk.getBlockX() >> 4, locationForChunk.getBlockZ() >> 4);
    } else {
      final int viewDistanceBlocks = plugin.getJavaPlugin().getServer().getViewDistance() * 16;
      return world.getNearbyEntitiesByType(Player.class, locationForChunk, viewDistanceBlocks, world.getMaxHeight(), viewDistanceBlocks);
    }
  }

  private record FormattedCharacter(char value, String format) {}
  private record LegacySequence(String sequence, boolean resetsFormatting) {}
  private record ScrollState(long shopId, Location signLocation, int itemNameLength, int offset) {}
}
