package com.cavetale.streamer;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
public final class StreamCommand implements TabExecutor {
    final StreamerPlugin plugin;

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player)) return false;
        if (args.length == 0) return false;
        Player player = (Player) sender;
        return onCommand(player, args[0], Arrays.copyOfRange(args, 1, args.length));
    }

    boolean onCommand(Player player, String cmd, String[] args) {
        switch (cmd) {
        case "optin": {
            if (args.length != 0) return false;
            if (!plugin.optouts.remove(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "You are already opted in.");
            } else {
                player.sendMessage(ChatColor.GREEN + "Opted back in.");
            }
            return true;
        }
        case "optout": {
            if (args.length != 0) return false;
            if (plugin.optouts.contains(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "You already opted out.");
            } else {
                plugin.optouts.add(player.getUniqueId());
                player.sendMessage(ChatColor.GREEN + "Opted out.");
            }
            return true;
        }
        default: return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String label, String[] args) {
        if (args.length == 0) return null;
        if (args.length == 1) {
            return complete(args[0], Stream.of("optin", "optout"));
        }
        return null;
    }


    private List<String> complete(final String arg,
                                  final Stream<String> opt) {
        return opt.filter(o -> o.startsWith(arg))
            .collect(Collectors.toList());
    }
}
