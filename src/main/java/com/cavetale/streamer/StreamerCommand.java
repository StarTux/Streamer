package com.cavetale.streamer;

import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
public final class StreamerCommand implements CommandExecutor {
    final StreamerPlugin plugin;

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (args.length == 0) return false;
        switch (args[0]) {
        case "target":
            if (args.length > 2) return false;
            if (args.length == 1) {
                sender.sendMessage("[Streamer] Picking new target");
                plugin.pickTarget();
                return true;
            } else if (args.length == 2) {
                Player p = plugin.getServer().getPlayerExact(args[1]);
                if (p == null) {
                    sender.sendMessage("Player not found: " + args[1]);
                } else {
                    sender.sendMessage("Selecting target: " + p.getName());
                    plugin.pickTarget(p);
                }
                return true;
            }
        case "info":
            sender.sendMessage("Streamer: "
                               + (plugin.streamer != null
                                  ? plugin.streamer.getName()
                                  : "N/A"));
            sender.sendMessage("Target: "
                               + (plugin.target != null
                                  ? plugin.target.getName()
                                  : "N/A"));
            sender.sendMessage("TargetTime: " + plugin.targetTime);
            sender.sendMessage("TargetServer: " + plugin.targetServer);
            sender.sendMessage("URL: " + plugin.url);
            return true;
        case "rank": {
            List<Session> rows = plugin.sessions.values().stream()
                .collect(Collectors.toList());
            sender.sendMessage("" + rows.size() + " Ranks:");
            int i = 1;
            for (Session row : rows) {
                sender.sendMessage(i++ + ") "
                                   + " " + row.player.getName()
                                   + " afk=" + row.afk);
            }
            return true;
        }
        case "reload": {
            plugin.loadConf();
            sender.sendMessage("Config reloaded.");
            return true;
        }
        case "world": {
            if (args.length < 2) {
                plugin.worldFilter = null;
                sender.sendMessage("World reset. All worlds are valid.");
            } else {
                plugin.worldFilter = args[1];
                sender.sendMessage("World filter: " + args[1]);
            }
            return true;
        }
        default:
            return false;
        }
    }
}
