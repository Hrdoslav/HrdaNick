// This is a source code for HrdaNick, please use it just for study, do not use this for creating your own plugins!
package cz.hrda.hrdaNick;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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

    private File nicknamesFile, randomFile;
    private FileConfiguration nicknamesConfig, randomConfig;
    private final Map<UUID, String> nicknames = new HashMap<>(); // Formát: "Nick|Skin"
    private final Random random = new Random();

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();

        nicknamesFile = new File(getDataFolder(), "nicknames.yml");
        if (!nicknamesFile.exists()) saveResource("nicknames.yml", false);

        randomFile = new File(getDataFolder(), "random.yml");
        if (!randomFile.exists()) saveResource("random.yml", false);

        loadConfigs();

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("nick").setExecutor(this);
        getCommand("nick").setTabCompleter(this);
    }

    private void loadConfigs() {
        nicknamesConfig = YamlConfiguration.loadConfiguration(nicknamesFile);
        randomConfig = YamlConfiguration.loadConfiguration(randomFile);

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

        if (args[0].equalsIgnoreCase("reload") && sender.hasPermission("hrdanick.admin")) {
            loadConfigs();
            for (Player p : Bukkit.getOnlinePlayers()) {
                String data = nicknames.get(p.getUniqueId());
                if (data != null && data.contains("|")) {
                    String[] parts = data.split("\\|");
                    applyNick(p, parts[0], parts[1]);
                }
            }
            sender.sendMessage("§a[HrdaNick] All configs reloaded!");
            return true;
        }

        // /nick list
        if (args[0].equalsIgnoreCase("list") && sender.hasPermission("hrdanick.admin")) {
            sender.sendMessage("§6--- List of active nicks ---");
            if (!nicknamesConfig.contains("nicks") || nicknamesConfig.getConfigurationSection("nicks").getKeys(false).isEmpty()) {
                sender.sendMessage("§cNone found.");
            } else {
                for (String uuidStr : nicknamesConfig.getConfigurationSection("nicks").getKeys(false)) {
                    UUID uuid = UUID.fromString(uuidStr);
                    String data = nicknamesConfig.getString("nicks." + uuidStr);
                    String nick = data.split("\\|")[0];

                    // Zkusíme najít jméno hráče (i offline)
                    org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                    String realName = (op.getName() != null) ? op.getName() : uuidStr;

                    sender.sendMessage("§e" + realName + " §7-> §f" + ChatColor.translateAlternateColorCodes('&', nick));
                }
            }
            return true;
        }

        // /nick resetall
        if (args[0].equalsIgnoreCase("resetall") && sender.hasPermission("hrdanick.admin")) {
            // 1. Resetujeme online hráče
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (nicknames.containsKey(p.getUniqueId())) {
                    performReset(p);
                }
            }

            // 2. Kompletní smazání z configu a paměti (pro offline hráče)
            nicknames.clear();
            nicknamesConfig.set("nicks", null);
            saveNicknamesConfig();

            sender.sendMessage("§a[HrdaNick] All nicks were removed (even the offline ones).");
            return true;
        }

        if (args[0].equalsIgnoreCase("random") && sender.hasPermission("hrdanick.admin") && args.length >= 2) {
            if (args[1].equalsIgnoreCase("@a")) {
                for (Player p : Bukkit.getOnlinePlayers()) setRandomNick(p);
                sender.sendMessage("§a[HrdaNick] Random nick applied to everyone!");
            } else {
                Player target = Bukkit.getPlayer(args[1]);
                if (target != null) setRandomNick(target);
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("reset") && sender instanceof Player p) {
            performReset(p);
            return true;
        }

        if (args[0].equalsIgnoreCase("set") && sender.hasPermission("hrdanick.admin") && args.length >= 3) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target != null) {
                String newNick = ChatColor.translateAlternateColorCodes('&', args[2]);
                saveAndApply(target, newNick, ChatColor.stripColor(newNick));
            }
            return true;
        }

        if (sender instanceof Player p && p.hasPermission("hrdanick.use")) {
            String newNick = ChatColor.translateAlternateColorCodes('&', args[0]);
            saveAndApply(p, newNick, ChatColor.stripColor(newNick));
            p.sendMessage("§aYour nick is now: §f" + newNick);
            return true;
        }
        return true;
    }

    private void setRandomNick(Player p) {
        List<String> prefixes = randomConfig.getStringList("parts.prefixes");
        List<String> mains = randomConfig.getStringList("parts.mains");
        List<String> suffixes = randomConfig.getStringList("parts.suffixes");
        List<String> skins = randomConfig.getStringList("skins");

        String prefix = (random.nextDouble() < randomConfig.getDouble("chances.prefix", 0.5)) ?
                prefixes.get(random.nextInt(prefixes.size())) : "";
        String main = mains.get(random.nextInt(mains.size()));
        String suffix = (random.nextDouble() < randomConfig.getDouble("chances.suffix", 0.5)) ?
                suffixes.get(random.nextInt(suffixes.size())) : "";

        String finalNick = ChatColor.translateAlternateColorCodes('&', prefix + main + suffix);
        String randomSkin = skins.get(random.nextInt(skins.size()));

        saveAndApply(p, finalNick, randomSkin);
    }

    private void saveAndApply(Player p, String nick, String skin) {
        String data = nick + "|" + skin;
        nicknames.put(p.getUniqueId(), data);
        nicknamesConfig.set("nicks." + p.getUniqueId(), data);
        saveNicknamesConfig();
        applyNick(p, nick, skin);
    }

    private void applyNick(Player player, String nick, String skinName) {
        if (player == null || !player.isOnline()) return;

        // Odstraníme barevné kódy pro technické účely (NickAPI a TabList)
        String plain = ChatColor.stripColor(nick);

        // 1. Nastavíme technický nick v NickAPI
        NickAPI.setNick(player, plain);

        // 2. Refresh hráče - změní model a jméno pro ostatní hráče (nutné pro Tab)
        NickAPI.refreshPlayer(player);

        // 3. Bukkit jména
        // setDisplayName mění jméno v chatu (zde barvy chceme)
        player.setDisplayName(nick);

        // setPlayerListName nastavujeme jako PLAIN (bez barev),
        // aby Minecraft a ostatní pluginy správně doplňovaly jméno v příkazech
        player.setPlayerListName(plain);

        // 4. Nastavení skinu - Delay 30 ticků je klíčový, aby NickAPI nepřepsalo skin
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (player.isOnline()) {
                // Používáme příkaz skinu až po kompletním refreshi identity
                player.performCommand("skin set " + skinName);
            }
        }, 30L);
    }

    private void performReset(Player player) {
        nicknames.remove(player.getUniqueId());
        nicknamesConfig.set("nicks." + player.getUniqueId(), null);
        saveNicknamesConfig();

        player.performCommand("skin clear");
        NickAPI.resetNick(player);
        NickAPI.refreshPlayer(player);
        player.setDisplayName(player.getName());
        player.setPlayerListName(player.getName());
        player.sendMessage("§a[HrdaNick] Reset done!");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(AsyncPlayerChatEvent event) {
        String data = nicknames.get(event.getPlayer().getUniqueId());
        if (data != null && data.contains("|")) {
            String nick = data.split("\\|")[0];
            // Nastavíme formát místo broadcastu
            event.setFormat(nick + ChatColor.WHITE + ": %2$s");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        String data = nicknames.get(event.getPlayer().getUniqueId());
        if (data != null && data.contains("|")) {
            String[] parts = data.split("\\|");
            event.setJoinMessage(event.getJoinMessage().replace(event.getPlayer().getName(), parts[0] + ChatColor.YELLOW));
            Bukkit.getScheduler().runTaskLater(this, () -> applyNick(event.getPlayer(), parts[0], parts[1]), 15L);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        String data = nicknames.get(event.getPlayer().getUniqueId());
        if (data != null && data.contains("|")) {
            String[] parts = data.split("\\|");
            Bukkit.getScheduler().runTaskLater(this, () -> applyNick(event.getPlayer(), parts[0], parts[1]), 20L);
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
        String data = nicknames.get(p.getUniqueId());
        if (data == null || !data.contains("|")) return message;

        String nick = data.split("\\|")[0]; // Vezmeme jen nick před svislítkem
        String real = p.getName();

        // Přidáme RESET, aby barva nicku nepřebarvila zbytek zprávy
        return message.replace(real, nick + ChatColor.RESET);
    }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage();
        String[] parts = msg.split(" ");
        boolean changed = false;

        // Procházíme všechny argumenty příkazu (vynecháme nultý, což je samotný příkaz)
        for (int i = 1; i < parts.length; i++) {
            for (Map.Entry<UUID, String> entry : nicknames.entrySet()) {
                String data = entry.getValue();
                if (data == null || !data.contains("|")) continue;

                // Získáme čistý nick bez barev a bez skinu (vezmeme část před '|')
                String savedNick = ChatColor.stripColor(data.split("\\|")[0]);

                // Porovnáme s tím, co hráč napsal jako argument
                if (savedNick.equalsIgnoreCase(parts[i])) {
                    Player target = Bukkit.getPlayer(entry.getKey());
                    if (target != null) {
                        // Nahradíme nick v příkazu skutečným jménem hráče, kterému server rozumí
                        parts[i] = target.getName();
                        changed = true;
                    }
                }
            }
        }

        // Pokud jsme našli a nahradili nějaký nick, aktualizujeme zprávu příkazu
        if (changed) {
            event.setMessage(String.join(" ", parts));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGlobalTab(TabCompleteEvent event) {
        boolean enabled = nicknamesConfig.getBoolean("settings.global-tab-complete", true);
        if (!enabled) return;

        String buffer = event.getBuffer();
        if (buffer.isEmpty() || !buffer.contains(" ")) return; // Funguje jen pro argumenty příkazů

        List<String> completions = new ArrayList<>(event.getCompletions());
        String lastWord = buffer.substring(buffer.lastIndexOf(' ') + 1).toLowerCase();

        // 1. Odstraníme z našeptávače reálná jména hráčů, kteří mají nastavený nick
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (nicknames.containsKey(online.getUniqueId())) {
                completions.remove(online.getName());
            }
        }

        // 2. Přidáme do našeptávače nicky, které odpovídají rozepsanému slovu
        for (String value : nicknames.values()) {
            if (value != null && value.contains("|")) {
                String nickOnly = ChatColor.stripColor(value.split("\\|")[0]);

                if (nickOnly.toLowerCase().startsWith(lastWord) && !completions.contains(nickOnly)) {
                    completions.add(nickOnly);
                }
            }
        }

        Collections.sort(completions);
        event.setCompletions(completions);
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        if (args.length == 1) {
            List<String> opts = new ArrayList<>(Arrays.asList("reset"));
            if (s.hasPermission("hrdanick.admin")) opts.addAll(Arrays.asList("set", "list", "reload", "resetall", "random"));
            return StringUtil.copyPartialMatches(args[0], opts, new ArrayList<>());
        } else if (args.length == 2 && s.hasPermission("hrdanick.admin")) {
            List<String> pNames = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) pNames.add(p.getName());
            return StringUtil.copyPartialMatches(args[1], pNames, new ArrayList<>());
        }
        return new ArrayList<>();
    }
}
