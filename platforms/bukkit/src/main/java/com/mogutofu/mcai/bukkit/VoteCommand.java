package com.mogutofu.mcai.bukkit;

import com.mogutofu.mcai.core.Actor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** {@code /vote yes|no|status}。 */
public final class VoteCommand implements CommandExecutor, TabCompleter {

    private final McaiPlugin plugin;

    VoteCommand(McaiPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("只有游戏内玩家能投票");
            return true;
        }
        Player player = (Player) sender;
        Actor actor = Actor.player(player.getUniqueId(), player.getName(), player.isOp());
        if (args.length == 0) {
            plugin.weather().voteUsage(actor);
            return true;
        }
        String sub = args[0].toLowerCase();
        if ("yes".equals(sub)) {
            plugin.weather().vote(actor, true);
        } else if ("no".equals(sub)) {
            plugin.weather().vote(actor, false);
        } else if ("status".equals(sub)) {
            plugin.weather().status(actor);
        } else {
            plugin.weather().voteUsage(actor);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (String option : Arrays.asList("yes", "no", "status")) {
                if (option.startsWith(args[0].toLowerCase())) {
                    out.add(option);
                }
            }
            return out;
        }
        return new ArrayList<>();
    }
}
