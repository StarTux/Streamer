package com.cavetale.streamer;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class StreamerPlugin extends JavaPlugin implements Listener {
    public static final String PERM = "group.streamer";
    final StreamerCommand streamerCommand = new StreamerCommand(this);
    final StreamCommand streamCommand = new StreamCommand(this);
    final Random random = new Random();
    final Set<UUID> optouts = new HashSet<>();
    Map<UUID, Session> sessions = new HashMap<>();
    Player streamer;
    Player target;
    int targetTime = 0;
    Location spectateLocation;
    String targetServer;
    int sendServerCooldown;
    String url;
    String worldFilter;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getScheduler().runTaskTimer(this, this::timer, 0L, 20);
        getServer().getPluginManager().registerEvents(this, this);
        loadConf();
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getCommand("streamer").setExecutor(streamerCommand);
        getCommand("stream").setExecutor(streamCommand);
    }

    void loadConf() {
        reloadConfig();
        targetServer = getConfig().getString("TargetServer");
        url = getConfig().getString("URL");
    }

    @Override
    public void onDisable() {
        pickTarget(null);
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
            .filter(p -> p.hasPermission("streamer.target"))
            .filter(p -> !optouts.contains(p.getUniqueId()))
            .filter(p -> !p.hasMetadata("nostream"))
            .filter(p -> worldFilter == null || p.getWorld().getName().equals(worldFilter))
            .map(this::sessionOf)
            .filter(s -> s.afk < 10)
            .collect(Collectors.toList());
        if (targets.isEmpty()) {
            pickTarget(null);
            detachStreamer();
            return;
        }
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
            String cmd = "/stream optout";
            target.sendMessage(Component.join(JoinConfiguration.noSeparators(), new Component[] {
                        Component.text("[Streamer] ", NamedTextColor.BLUE),
                        Component.text(streamer.getName() + " is spectating you: ", NamedTextColor.WHITE),
                        Component.text("[Link]", NamedTextColor.BLUE)
                        .hoverEvent(HoverEvent.showText(Component.text(url, NamedTextColor.BLUE, TextDecoration.UNDERLINED)))
                        .clickEvent(ClickEvent.openUrl(url)),
                        Component.text(". "),
                        Component.text("[OptOut]", NamedTextColor.RED)
                        .hoverEvent(HoverEvent.showText(Component.text(cmd, NamedTextColor.RED)))
                        .clickEvent(ClickEvent.runCommand(cmd)),
                    }));
        }
    }

    Player findStreamer() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isPermissionSet(PERM) && player.hasPermission(PERM)) {
                return player;
            }
        }
        return null;
    }

    boolean checkValidity() {
        // Streamer
        if (streamer != null && !streamer.isOnline()) {
            streamer = null;
        }
        if (streamer != null && (!streamer.isPermissionSet(PERM) || !streamer.hasPermission(PERM))) {
            streamer = null;
        }
        if (streamer == null) {
            streamer = findStreamer();
        }
        // Target
        if (target != null) {
            if (!target.isOnline()) {
                pickTarget(null);
                detachStreamer();
            } else if (!target.hasPermission("streamer.target")) {
                pickTarget(null);
                detachStreamer();
            } else if (optouts.contains(target.getUniqueId())) {
                pickTarget(null);
                detachStreamer();
            } else if (target.hasMetadata("nostream")) {
                pickTarget(null);
                detachStreamer();
            }
        }
        // Detach if necessary
        if (streamer != null && target == null && streamer.getSpectatorTarget() != null) {
            detachStreamer();
        }
        return streamer != null && target != null;
    }

    void detachStreamer() {
        if (streamer == null || streamer.getGameMode() != GameMode.SPECTATOR) return;
        streamer.setSpectatorTarget(null);
    }

    boolean tooFar(Location a, Location b, double max) {
        if (!a.getWorld().equals(b.getWorld())) return true;
        return a.distanceSquared(b) > max * max;
    }

    void giveLuck(Player player) {
        PotionEffect pot = player.getPotionEffect(PotionEffectType.LUCK);
        final int dur = 219;
        if (pot == null || pot.getAmplifier() == 0 || pot.getDuration() < dur) {
            pot = new PotionEffect(PotionEffectType.LUCK, dur, 0,
                                   true, true, true);
            player.addPotionEffect(pot);
        }
    }

    void prepStreamer() {
        // Set gamemode
        if (streamer.getGameMode() != GameMode.SPECTATOR) {
            streamer.setGameMode(GameMode.SPECTATOR);
        }
        // Restore health
        double max = streamer.getAttribute(Attribute.MAX_HEALTH).getValue();
        if (streamer.getHealth() < max) {
            streamer.setHealth(max);
        }
        // Remove potion effects
        for (PotionEffectType type : PotionEffectType.values()) {
            if (type == PotionEffectType.NIGHT_VISION) continue;
            if (streamer.hasPotionEffect(type)) {
                streamer.removePotionEffect(type);
            }
        }
    }

    void applyNightVision() {
        Block block = streamer.getEyeLocation().getBlock();
        if (block.getLightLevel() < 8) {
            PotionEffect pot = new PotionEffect(PotionEffectType.NIGHT_VISION,
                                                600, 0, true, false, false);
            streamer.addPotionEffect(pot);
        } else if (block.getLightLevel() > 10) {
            streamer.removePotionEffect(PotionEffectType.NIGHT_VISION);
        }
    }

    /**
     * Target may not be null.
     */
    void spectateTarget() {
        giveLuck(target);
        Session session = sessionOf(target);
        targetTime += 1;
        prepStreamer();
        if (tooFar(streamer.getLocation(), target.getLocation(), 32.0)) {
            streamer.setSpectatorTarget(null);
            streamer.teleport(target);
        } else {
            if (streamer.getSpectatorTarget() == null) {
                streamer.setSpectatorTarget(target);
                spectateLocation = target.getLocation();
            } else if (spectateLocation != null && tooFar(spectateLocation, streamer.getLocation(), 256.0)) {
                streamer.setSpectatorTarget(null);
            }
        }
        streamer.sendActionBar(Component.text()
                               .append(Component.text("Spectating ", NamedTextColor.GRAY))
                               .append(target.displayName())
                               .build());
    }

    void timer() {
        for (Player player : getServer().getOnlinePlayers()) {
            if (player.equals(streamer)) continue;
            sessionOf(player).timer();
        }
        checkValidity();
        if (streamer == null) return;
        if (targetServer != null && !targetServer.isEmpty()) {
            if (sendServerCooldown > 0) {
                sendServerCooldown -= 1;
                return;
            }
            getLogger().info("Sending " + streamer.getName() + " to " + targetServer);
            sendServer(streamer, targetServer);
            sendServerCooldown = 200;
            return;
        }
        if (target == null || target.getGameMode() == GameMode.SPECTATOR || targetTime > 7 * 60 || sessionOf(target).afk > 40) {
            pickTarget();
        }
        if (target != null) {
            spectateTarget();
        }
        applyNightVision();
    }

    void sendServer(Player player, String server) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        try {
            dos.writeUTF("Connect");
            dos.writeUTF(server);
        } catch (IOException ex) {
            ex.printStackTrace();
            return;
        }
        player.sendPluginMessage(this, "BungeeCord", baos.toByteArray());
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
        if (!checkValidity()) return;
        Player player = event.getPlayer();
        if (player.equals(target)) {
            streamer.setSpectatorTarget(null);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!checkValidity()) return;
        Player player = event.getPlayer();
        if (player.equals(target)) {
            streamer.setSpectatorTarget(null);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity().equals(streamer)) {
            event.setCancelled(true);
        }
    }
}
