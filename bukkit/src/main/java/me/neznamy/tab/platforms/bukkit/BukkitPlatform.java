package me.neznamy.tab.platforms.bukkit;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.earth2me.essentials.Essentials;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;

import me.clip.placeholderapi.PlaceholderAPI;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.chat.EnumChatFormat;
import me.neznamy.tab.api.protocol.PacketBuilder;
import me.neznamy.tab.platforms.bukkit.event.TabPlayerLoadEvent;
import me.neznamy.tab.platforms.bukkit.event.TabLoadEvent;
import me.neznamy.tab.platforms.bukkit.features.PerWorldPlayerlist;
import me.neznamy.tab.platforms.bukkit.features.PetFix;
import me.neznamy.tab.platforms.bukkit.features.TabExpansion;
import me.neznamy.tab.platforms.bukkit.features.WitherBossBar;
import me.neznamy.tab.platforms.bukkit.features.unlimitedtags.NameTagX;
import me.neznamy.tab.platforms.bukkit.permission.Vault;
import me.neznamy.tab.shared.Platform;
import me.neznamy.tab.shared.TAB;
import me.neznamy.tab.shared.features.PlaceholderManagerImpl;
import me.neznamy.tab.shared.features.bossbar.BossBarManagerImpl;
import me.neznamy.tab.shared.features.nametags.NameTag;
import me.neznamy.tab.shared.permission.LuckPerms;
import me.neznamy.tab.shared.permission.None;
import me.neznamy.tab.shared.permission.PermissionPlugin;
import me.neznamy.tab.shared.permission.UltraPermissions;
import me.neznamy.tab.shared.placeholders.PlayerPlaceholder;
import me.neznamy.tab.shared.placeholders.UniversalPlaceholderRegistry;
import net.milkbowl.vault.permission.Permission;

/**
 * Bukkit implementation of Platform
 */
public class BukkitPlatform implements Platform {

	//plugin instance
	private Main plugin;

	private BukkitPacketBuilder packetBuilder = new BukkitPacketBuilder();

	//booleans to check plugin presence
	private boolean placeholderAPI;
	private boolean viaversion;
	private boolean idisguise;
	private boolean libsdisguises;
	private Plugin essentials;

	/**
	 * Constructs new instance with given parameters
	 * @param plugin - plugin instance
	 */
	public BukkitPlatform(Main plugin) {
		this.plugin = plugin;
	}

	@Override
	public PermissionPlugin detectPermissionPlugin() {
		if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
			return new LuckPerms(getPluginVersion("LuckPerms"));
		} else if (Bukkit.getPluginManager().isPluginEnabled("UltraPermissions")) {
			return new UltraPermissions(getPluginVersion("UltraPermissions"));
		} else if (Bukkit.getPluginManager().isPluginEnabled("Vault")) {
			return new Vault(Bukkit.getServicesManager().getRegistration(Permission.class).getProvider(), getPluginVersion("Vault"));
		} else {
			return new None();
		}
	}

	@Override
	public void loadFeatures() {
		if (Bukkit.getPluginManager().isPluginEnabled("ViaVersion")) {
			try {
				Class.forName("com.viaversion.viaversion.api.Via");
				viaversion = true;
			} catch (ClassNotFoundException e) {
				TAB.getInstance().sendConsoleMessage("&c[TAB] An outdated version of ViaVersion (" + getPluginVersion("ViaVersion") + ") was detected.", true);
				TAB.getInstance().sendConsoleMessage("&c[TAB] TAB only supports ViaVersion 4.0.0 and above. Disabling ViaVersion hook.", true);
				TAB.getInstance().sendConsoleMessage("&c[TAB] This might cause problems, such as limitations still being present for latest MC clients as well as RGB not working.", true);
			}
		}
		if (Bukkit.getPluginManager().isPluginEnabled("Tablisknu")) {
			TAB.getInstance().sendConsoleMessage("&c[TAB] Detected plugin \"Tablisknu\", which causes TAB to not work properly. Consider removing the plugin.", true);
		}
		placeholderAPI = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
		idisguise = Bukkit.getPluginManager().isPluginEnabled("iDisguise");
		libsdisguises = Bukkit.getPluginManager().isPluginEnabled("LibsDisguises");
		essentials = Bukkit.getPluginManager().getPlugin("Essentials");
		TAB tab = TAB.getInstance();
		if (tab.getConfiguration().isPipelineInjection()) tab.getFeatureManager().registerFeature("injection", new BukkitPipelineInjector());
		new BukkitPlaceholderRegistry().registerPlaceholders(tab.getPlaceholderManager());
		new UniversalPlaceholderRegistry().registerPlaceholders(tab.getPlaceholderManager());
		loadNametagFeature(tab);
		tab.loadUniversalFeatures();
		if (tab.getConfiguration().getConfig().getBoolean("bossbar.enabled", false)) {
			if (tab.getServerVersion().getMinorVersion() < 9) {
				tab.getFeatureManager().registerFeature("bossbar", new WitherBossBar(plugin));
			} else {
				tab.getFeatureManager().registerFeature("bossbar", new BossBarManagerImpl());
			}
		}
		if (tab.getServerVersion().getMinorVersion() >= 9 && tab.getConfiguration().getConfig().getBoolean("fix-pet-names.enabled", false)) tab.getFeatureManager().registerFeature("petfix", new PetFix());
		if (tab.getConfiguration().getConfig().getBoolean("per-world-playerlist.enabled", false)) tab.getFeatureManager().registerFeature("pwp", new PerWorldPlayerlist(plugin));
		if (placeholderAPI) {
			new TabExpansion(plugin);
		}
		for (Player p : getOnlinePlayers()) {
			tab.addPlayer(new BukkitTabPlayer(p, plugin.getProtocolVersion(p)));
		}
	}
	
	private String getPluginVersion(String plugin) {
		return Bukkit.getPluginManager().getPlugin(plugin).getDescription().getVersion();
	}

	/**
	 * Loads nametag feature from config
	 * @param tab - tab instance
	 */
	private void loadNametagFeature(TAB tab) {
		if (tab.getConfiguration().getConfig().getBoolean("scoreboard-teams.enabled", true)) {
			if (tab.getConfiguration().getConfig().getBoolean("scoreboard-teams.unlimited-nametag-mode.enabled", false) && tab.getServerVersion().getMinorVersion() >= 8) {
				tab.getFeatureManager().registerFeature("nametagx", new NameTagX(plugin));
			} else {
				tab.getFeatureManager().registerFeature("nametag16", new NameTag());
			}
		}
	}

	/**
	 * Returns list of online players from Bukkit API
	 * @return list of online players from Bukkit API
	 */
	@SuppressWarnings("unchecked")
	private Player[] getOnlinePlayers() {
		try {
			Object players = Bukkit.class.getMethod("getOnlinePlayers").invoke(null);
			if (players instanceof Player[]) {
				//1.7.x
				return (Player[]) players;
			} else {
				//1.8+
				return ((Collection<Player>)players).toArray(new Player[0]); 
			}
		} catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
			TAB.getInstance().getErrorManager().printError("Failed to get online players", e);
			return new Player[0];
		}
	}

	@Override
	public void sendConsoleMessage(String message, boolean translateColors) {
		Bukkit.getConsoleSender().sendMessage(translateColors ? EnumChatFormat.color(message) : message);
	}

	@Override
	public void registerUnknownPlaceholder(String identifier) {
		PlaceholderManagerImpl pl = TAB.getInstance().getPlaceholderManager();
		if (identifier.startsWith("%rel_")) {
			//relational placeholder
			TAB.getInstance().getPlaceholderManager().registerRelationalPlaceholder(identifier, pl.getRelationalRefresh(identifier), (viewer, target) -> 
				placeholderAPI ? PlaceholderAPI.setRelationalPlaceholders((Player) viewer.getPlayer(), (Player) target.getPlayer(), identifier) : identifier);
		} else {
			//normal placeholder
			if (identifier.startsWith("%sync:")) {
				int refresh;
				if (pl.getServerPlaceholderRefreshIntervals().containsKey(identifier)) {
					refresh = pl.getServerPlaceholderRefreshIntervals().get(identifier);
				} else if (pl.getPlayerPlaceholderRefreshIntervals().containsKey(identifier)) {
					refresh = pl.getPlayerPlaceholderRefreshIntervals().get(identifier);
				} else {
					refresh = pl.getDefaultRefresh();
				}
				pl.registerPlaceholder(new PlayerPlaceholder(identifier, refresh, null) {
					
					@Override
					public Object get(TabPlayer p) {
						Bukkit.getScheduler().runTask(plugin, () -> {

							long time = System.nanoTime();
							String syncedPlaceholder = identifier.substring(6, identifier.length()-1);
							String value = placeholderAPI ? PlaceholderAPI.setPlaceholders((Player) p.getPlayer(), "%" + syncedPlaceholder + "%") : identifier;
							getLastValues().put(p.getName(), value);
							if (!getForceUpdate().contains(p.getName())) getForceUpdate().add(p.getName());
							TAB.getInstance().getCPUManager().addPlaceholderTime(getIdentifier(), System.nanoTime()-time);
						});
						return getLastValues().get(p.getName());
					}
				});
				return;
			}
			if (pl.getServerPlaceholderRefreshIntervals().containsKey(identifier)) {
				TAB.getInstance().getPlaceholderManager().registerServerPlaceholder(identifier, pl.getServerPlaceholderRefreshIntervals().get(identifier), () ->
						placeholderAPI ? PlaceholderAPI.setPlaceholders(null, identifier) : identifier);
			} else {
				int refresh;
				if (pl.getPlayerPlaceholderRefreshIntervals().containsKey(identifier)) {
					refresh = pl.getPlayerPlaceholderRefreshIntervals().get(identifier);
				} else {
					refresh = pl.getDefaultRefresh();
				}
				TAB.getInstance().getPlaceholderManager().registerPlayerPlaceholder(identifier, refresh, p -> 
					placeholderAPI ? PlaceholderAPI.setPlaceholders((Player) p.getPlayer(), identifier) : identifier);
			}
		}
	}

	@Override
	public String getServerVersion() {
		return Bukkit.getBukkitVersion().split("-")[0] + " (" + Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3] + ")";
	}

	@Override
	public File getDataFolder() {
		return plugin.getDataFolder();
	}

	@Override
	public void callLoadEvent() {
		Bukkit.getPluginManager().callEvent(new TabLoadEvent());
	}

	@Override
	public void callLoadEvent(TabPlayer player) {
		Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> Bukkit.getPluginManager().callEvent(new TabPlayerLoadEvent(player)));
	}

	@Override
	public int getMaxPlayers() {
		return Bukkit.getMaxPlayers();
	}

	public boolean isViaversionEnabled() {
		return viaversion;
	}

	public boolean isLibsdisguisesEnabled() {
		return libsdisguises;
	}
	
	public void setLibsdisguisesEnabled(boolean enabled) {
		libsdisguises = enabled;
	}

	public boolean isIdisguiseEnabled() {
		return idisguise;
	}

	public Essentials getEssentials() {
		return (Essentials) essentials;
	}

	@Override
	public PacketBuilder getPacketBuilder() {
		return packetBuilder;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Object getSkin(List<String> properties) {
		PropertyMap map = new PropertyMap();
		map.put("textures", new Property("textures", properties.get(0), properties.get(1)));
		return map;
	}
	
	@Override
	public boolean isProxy() {
		return false;
	}

	@Override
	public boolean isPluginEnabled(String plugin) {
		return Bukkit.getPluginManager().isPluginEnabled(plugin);
	}
}