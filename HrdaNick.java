// This is a source code for HrdaNick, please use it just for study, do not use this for creating your own plugins!
package cz.hrda.hrdaNick;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.server.TabCompleteEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.StringUtil;
import xyz.haoshoku.nick.api.NickAPI;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class HrdaNick extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private File nicknamesFile;
    private FileConfiguration nicknamesConfig;
    private final Map<UUID, String> nicknames = new HashMap<>();

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        nicknamesFile = new File(getDataFolder(), "nicknames.yml");
        if (!nicknamesFile.exists()) saveResource("nicknames.yml", false);

        loadNicknames();

        if (!nicknamesConfig.contains("settings.global-tab-complete")) {
            nicknamesConfig.set("settings.global-tab-complete", true);
            saveNicknamesConfig();
        }

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("nick").setExecutor(this);
        getCommand("nick").setTabCompleter(this);
    }

    private void loadNicknames() {
        nicknamesConfig = YamlConfiguration.loadConfiguration(nicknamesFile);
        nicknames.clear();
        if (nicknamesConfig.contains("nicks")) {
            for (String key : nicknamesConfig.getConfigurationSection("nicks").getKeys(false)) {
                nicknames.put(UUID.fromString(key), nicknamesConfig.getString("nicks." + key));
            }
        }
    }

    private void saveNicknamesConfig() {
        try { nicknamesConfig.save(nicknamesFile); } catch (IOException e) { e.printStackTrace(); }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return false;

        // --- ADMIN COMMANDS ---
        if (args[0].equalsIgnoreCase("reload") && sender.hasPermission("hrdanick.admin")) {
            loadNicknames();
            for (Player p : Bukkit.getOnlinePlayers()) {
                String nick = nicknames.get(p.getUniqueId());
                if (nick != null) applyNick(p, nick);
            }
            sender.sendMessage("§a[HrdaNick] Configuration reloaded!");
            return true;
        }

        if (args[0].equalsIgnoreCase("list") && sender.hasPermission("hrdanick.admin")) {
            sender.sendMessage("§6§lList of nicked players:");
            if (nicknamesConfig.contains("nicks")) {
                for (String uuidStr : nicknamesConfig.getConfigurationSection("nicks").getKeys(false)) {
                    String nick = nicknamesConfig.getString("nicks." + uuidStr);
                    OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(uuidStr));
                    sender.sendMessage("§e" + (op.getName() != null ? op.getName() : "Neznámý") + " §7-> §f" + ChatColor.translateAlternateColorCodes('&', nick));
                }
            } else {
                sender.sendMessage("§7None found.");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("resetall") && sender.hasPermission("hrdanick.admin")) {
            for (Player p : Bukkit.getOnlinePlayers()) performReset(p);
            nicknames.clear();
            nicknamesConfig.set("nicks", null);
            saveNicknamesConfig();
            sender.sendMessage("§a[HrdaNick] All players were cleared from nicknames.yml!");
            return true;
        }

        if (args[0].equalsIgnoreCase("set") && sender.hasPermission("hrdanick.admin") && args.length >= 3) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target != null) {
                String newNick = ChatColor.translateAlternateColorCodes('&', args[2]);
                saveAndApply(target, newNick);
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("reset")) {
            if (args.length == 2 && args[1].equalsIgnoreCase("@a") && sender.hasPermission("hrdanick.admin")) {
                for (Player p : Bukkit.getOnlinePlayers()) performReset(p);
                return true;
            }
            if (sender instanceof Player p) performReset(p);
            return true;
        }

        // --- PLAYER COMMAND: /nick <nick> ---
        if (sender instanceof Player p) {
            if (!p.hasPermission("hrdanick.use")) {
                p.sendMessage("§cYou don't have permissions!");
                return true;
            }
            String newNick = ChatColor.translateAlternateColorCodes('&', args[0]);
            saveAndApply(p, newNick);
            p.sendMessage("§aYou nick is now: §f" + newNick);
            return true;
        }
        return true;
    }

    private void saveAndApply(Player p, String nick) {
        nicknames.put(p.getUniqueId(), nick);
        nicknamesConfig.set("nicks." + p.getUniqueId(), nick);
        saveNicknamesConfig();
        applyNick(p, nick);
    }

    private void performReset(Player player) {
        // 1. KROK: Hráč si vymaže skin (SUDO)
        // Opět používáme performCommand, aby to proběhlo pod identitou hráče
        if (player.isOnline()) {
            player.performCommand("skin clear");
        }

        // 2. KROK: Odstranění z databáze a configu
        nicknames.remove(player.getUniqueId());
        nicknamesConfig.set("nicks." + player.getUniqueId(), null);
        saveNicknamesConfig();

        // 3. KROK: Reset vizuálních věcí přes NickAPI a Bukkit
        NickAPI.resetNick(player);
        NickAPI.refreshPlayer(player);
        player.setDisplayName(player.getName());
        player.setPlayerListName(player.getName());
        player.setCustomName(null);

        player.sendMessage("§a[HrdaNick] Your nick have been reset!");
    }

    private void applyNick(Player player, String nick) {
        if (player == null) return;

        String plain = ChatColor.stripColor(nick);

        // 1. KROK: Hráč si nastaví skin sám za sebe (SUDO)
        // Dáme mu 2 ticky, aby se to nepohádalo s joinem
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (player.isOnline()) {
                player.performCommand("skin set " + plain);
            }
        }, 2L);

        // 2. KROK: FINÁLNÍ ÚDER (NickAPI a jména)
        // Počkáme celou sekundu (20 ticků), než SkinsRestorer dokončí svůj refresh.
        // Tím zajistíme, že NickAPI bude mít poslední slovo.
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (player.isOnline()) {
                // Nejdřív nastavíme nick v API
                NickAPI.setNick(player, plain);


                // A nakonec vnutíme barevná jména do Bukkitu
                player.setDisplayName(nick);
                player.setPlayerListName(nick);
                player.setCustomName(nick);
                player.setCustomNameVisible(false);
            }
        }, 25L); // Zvýšeno na 25 ticků (cca 1.2 sekundy)
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onChat(AsyncPlayerChatEvent event) {
        if (event.isCancelled()) return;
        String nick = nicknames.get(event.getPlayer().getUniqueId());
        if (nick != null) {
            event.setCancelled(true);
            Bukkit.broadcastMessage("<" + nick + ChatColor.WHITE + "> " + event.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        String nick = nicknames.get(event.getPlayer().getUniqueId());
        if (nick != null && event.getQuitMessage() != null) {
            event.setQuitMessage(event.getQuitMessage().replace(event.getPlayer().getName(), nick + ChatColor.YELLOW));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String nick = nicknames.get(player.getUniqueId());
        if (nick != null) {
            if (event.getJoinMessage() != null) {
                event.setJoinMessage(event.getJoinMessage().replace(player.getName(), nick + ChatColor.YELLOW));
            }
            Bukkit.getScheduler().runTaskLater(this, () -> applyNick(player, nick), 10L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        String message = event.getDeathMessage();
        if (message == null || message.isEmpty()) return;

        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        message = replaceAllNames(message, victim);

        if (killer != null) {
            message = replaceAllNames(message, killer);
        }

        event.setDeathMessage(message);;
    }

    private String replaceAllNames(String message, Player p) {
        String nick = nicknames.get(p.getUniqueId());
        if (nick == null) return message;

        String real = p.getName();
        String display = ChatColor.stripColor(p.getDisplayName());

        message = message.replace(real, nick);
        message = message.replace(display, nick);

        return message;
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        String nick = nicknames.get(event.getPlayer().getUniqueId());
        if (nick != null) {
            Bukkit.getScheduler().runTaskLater(this, () -> applyNick(event.getPlayer(), nick), 15L);
        }
    }
    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage();
        String[] parts = msg.split(" ");
        boolean changed = false;
        for (int i = 1; i < parts.length; i++) {
            for (Map.Entry<UUID, String> entry : nicknames.entrySet()) {
                if (ChatColor.stripColor(entry.getValue()).equalsIgnoreCase(parts[i])) {
                    Player target = Bukkit.getPlayer(entry.getKey());
                    if (target != null) { parts[i] = target.getName(); changed = true; }
                }
            }
        }
        if (changed) event.setMessage(String.join(" ", parts));
    }

    @EventHandler
    public void onGlobalTab(TabCompleteEvent event) {
        boolean enabled = nicknamesConfig.getBoolean("settings.global-tab-complete", true);
        if (!enabled) return;

        List<String> completions = new ArrayList<>(event.getCompletions());
        for (String n : nicknames.values()) {
            completions.add(ChatColor.stripColor(n));
        }
        event.setCompletions(completions);
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        if (args.length == 1) {
            List<String> opts = new ArrayList<>(Arrays.asList("reset"));
            if (s.hasPermission("hrdanick.admin")) opts.addAll(Arrays.asList("set", "list", "reload", "resetall"));
            return StringUtil.copyPartialMatches(args[0], opts, new ArrayList<>());
        } else if (args.length == 2 && s.hasPermission("hrdanick.admin")) {
            List<String> pNames = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) pNames.add(p.getName());
            return StringUtil.copyPartialMatches(args[1], pNames, new ArrayList<>());
        }
        return new ArrayList<>();
    }
}
