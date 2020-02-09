package com.cavetale.streamer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class StreamerPlugin extends JavaPlugin implements Listener {
    Random random = new Random();
    Map<UUID, Session> sessions = new HashMap<>();
    Player streamer;
    Player target;
    int targetTime = 0;

    @Override
    public void onEnable() {
        getServer().getScheduler().runTaskTimer(this, this::timer, 0L, 20);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        pickTarget(null);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (args.length == 0) return false;
        switch (args[0]) {
        case "target":
            if (args.length > 2) return false;
            if (args.length == 1) {
                sender.sendMessage("[Streamer] Picking new target");
                pickTarget();
                return true;
            } else if (args.length == 2) {
                Player p = getServer().getPlayerExact(args[1]);
                if (p == null) {
                    sender.sendMessage("Player not found: " + args[1]);
                } else {
                    sender.sendMessage("Selecting target: " + p.getName());
                    pickTarget(p);
                }
                return true;
            }
        case "info":
            sender.sendMessage("Streamer: "
                               + (streamer != null
                                  ? streamer.getName()
                                  : "N/A"));
            sender.sendMessage("Target: "
                               + (target != null
                                  ? target.getName()
                                  : "N/A"));
            sender.sendMessage("TargetTime: " + targetTime);
            return true;
        case "rank": {
            List<Session> rows = sessions.values().stream()
                .sorted((a, b) -> Integer.compare(b.score, a.score))
                .collect(Collectors.toList());
            sender.sendMessage("" + rows.size() + " Ranks:");
            int i = 1;
            for (Session row : rows) {
                sender.sendMessage(i++ + ") "
                                   + row.score
                                   + " " + row.player.getName()
                                   + " noMove=" + row.noMove);
            }
            return true;
        }
        default:
            return false;
        }
    }

    Session sessionOf(Player player) {
        UUID uuid = player.getUniqueId();
        Session session = sessions.get(uuid);
        if (session != null) return session;
        session = new Session(player);
        sessions.put(uuid, session);
        return session;
    }

    void pickTarget() {
        if (streamer == null) return;
        List<Session> targets = getServer().getOnlinePlayers().stream()
            .filter(p -> !p.equals(streamer))
            .filter(Player::isValid)
            .filter(p -> p.getGameMode() != GameMode.SPECTATOR)
            .map(this::sessionOf)
            .filter(s -> s.noMove < 10)
            .sorted((a, b) -> Integer.compare(b.score, a.score))
            .collect(Collectors.toList());
        int avg = 0;
        for (Session row : targets) {
            avg += row.score;
        }
        avg /= targets.size();
        final int avg2 = avg;
        targets.removeIf(t -> t.score < avg2);
        if (targets.isEmpty()) return;
        pickTarget(targets.get(random.nextInt(targets.size())).player);
    }

    void pickTarget(Player newTarget) {
        Player oldTarget = target;
        target = newTarget;
        targetTime = 0;
        if (streamer == null) return;
        if (oldTarget != null && !oldTarget.equals(target) && oldTarget.isOnline()) {
            oldTarget.sendMessage(ChatColor.BLUE + "[Streamer] "
                                  + ChatColor.WHITE + streamer.getName()
                                  + " says goodbye and thank you for now."
                                  + ChatColor.GREEN + " :)");
        }
        if (target != null && !target.equals(oldTarget)) {
            getLogger().info("pickTarget: " + target.getName());
            streamer.sendMessage(ChatColor.BLUE + "[Streamer] "
                                 + ChatColor.WHITE
                                 + "Now spectating " + target.getName() + ".");
            target.sendMessage(ChatColor.BLUE + "[Streamer] "
                               + ChatColor.WHITE
                               + streamer.getName() + " is spectating you on "
                               + ChatColor.BLUE + "https://twitch.tv/StarTux"
                               + ChatColor.WHITE + ".");
        }
    }

    boolean checkValidity() {
        if (streamer == null) {
            streamer = getServer().getPlayerExact("Cavetale");
        }
        if (streamer != null && !streamer.isOnline()) {
            getLogger().info("checkValidity: Streamer disappeared: " + streamer.getName());
            streamer = null;
        }
        if (target != null && !target.isOnline()) {
            getLogger().info("checkValidity: Target disappeared: " + target.getName());
            pickTarget(null);
        }
        if (streamer != null && target == null && streamer.getSpectatorTarget() != null) {
            detachStreamer();
        }
        return streamer != null && target != null;
    }

    void detachStreamer() {
        if (streamer == null) return;
        streamer.setSpectatorTarget(null);
        streamer.teleport(getServer().getWorld("spawn").getSpawnLocation());
    }

    boolean tooFar(Location a, Location b, double max) {
        if (!a.getWorld().equals(b.getWorld())) return true;
        return a.distanceSquared(b) > max * max;
    }

    void timer() {
        for (Player player : getServer().getOnlinePlayers()) {
            if (player.equals(streamer)) continue;
            sessionOf(player).timer();
        }
        checkValidity();
        if (streamer == null) return;
        if (target == null || targetTime > 7 * 60 || sessionOf(target).noMove > 40) {
            pickTarget();
        }
        if (target == null) return;
        Session session = sessionOf(target);
        targetTime += 1;
        if (streamer.getGameMode() != GameMode.SPECTATOR) {
            streamer.setGameMode(GameMode.SPECTATOR);
        }
        if (tooFar(streamer.getLocation(), target.getLocation(), 32.0)) {
            streamer.setSpectatorTarget(null);
            streamer.teleport(target);
        } else {
            if (streamer.getSpectatorTarget() == null) {
                streamer.setSpectatorTarget(target);
            }
            Block block = streamer.getEyeLocation().getBlock();
            if (block.isEmpty()) {
                if (block.getLightLevel() < 7) {
                    PotionEffect pot = new PotionEffect(PotionEffectType.NIGHT_VISION,
                                                        600, 0, true, false, false);
                    streamer.addPotionEffect(pot, true);
                } else if (block.getLightLevel() > 9) {
                    streamer.removePotionEffect(PotionEffectType.NIGHT_VISION);
                }
            }
        }
        streamer.sendActionBar(ChatColor.GRAY + "Spectating " + target.getDisplayName());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        checkValidity();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        sessions.remove(player.getUniqueId());
        if (player.equals(streamer)) {
            getLogger().info("Streamer logged out: " + streamer.getName());
            streamer = null;
        } else if (player.equals(target)) {
            getLogger().info("Target logged out: " + target.getName());
            target = null;
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (player.equals(target)) {
            streamer.setSpectatorTarget(null);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (player.equals(target)) {
            streamer.setSpectatorTarget(null);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        sessionOf(event.getPlayer()).score += 3;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        sessionOf(event.getPlayer()).score += 10;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        switch (event.getCause()) {
        case ENTITY_ATTACK:
        case ENTITY_EXPLOSION:
        case ENTITY_SWEEP_ATTACK:
            break;
        default:
            return;
        }
        sessionOf((Player) event.getEntity()).score += 4 * (int) event.getDamage();
    }
}
