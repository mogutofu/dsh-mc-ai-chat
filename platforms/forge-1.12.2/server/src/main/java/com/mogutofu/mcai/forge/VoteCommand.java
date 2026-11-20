package com.mogutofu.mcai.forge;

import com.mogutofu.mcai.core.Actor;
import com.mogutofu.mcai.core.WeatherVoteService;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * {@code /vote yes|no|status} —— 天气投票。只允许游戏内玩家投票，控制台提示
 * “只有游戏内玩家能投票”（文案在公共核心里）。
 */
public final class VoteCommand extends CommandBase {

    private static final String USAGE = "用法：/vote yes 同意 · /vote no 反对 · /vote status 查看当前投票";

    @Override
    public String getName() {
        return "vote";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return USAGE;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos targetPos) {
        if (args == null || args.length <= 1) {
            String prefix = args == null || args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            List<String> options = new ArrayList<>();
            for (String option : new String[]{"yes", "no", "status"}) {
                if (option.startsWith(prefix)) {
                    options.add(option);
                }
            }
            return options;
        }
        return Collections.emptyList();
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        WeatherVoteService service = McaiMod.weatherService();
        if (service == null) {
            reply(sender, "天气服务还没准备好，稍后再试");
            return;
        }
        Actor actor = WeatherCommand.actorOf(sender);
        if (args == null || args.length == 0) {
            service.voteUsage(actor);
            return;
        }
        String head = args[0] == null ? "" : args[0].toLowerCase(Locale.ROOT);
        if ("yes".equals(head) || "agree".equals(head)) {
            service.vote(actor, true);
            return;
        }
        if ("no".equals(head) || "reject".equals(head)) {
            service.vote(actor, false);
            return;
        }
        if ("status".equals(head)) {
            service.status(actor);
            return;
        }
        service.voteUsage(actor);
    }

    private static void reply(ICommandSender sender, String text) {
        if (sender == null || text == null) {
            return;
        }
        sender.sendMessage(new TextComponentString("[天气] " + text));
    }
}
