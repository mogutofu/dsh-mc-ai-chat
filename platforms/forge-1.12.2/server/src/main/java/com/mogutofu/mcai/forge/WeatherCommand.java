package com.mogutofu.mcai.forge;

import com.mogutofu.mcai.core.Actor;
import com.mogutofu.mcai.core.WeatherVoteService;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * {@code /weather clear|rain|thunder [持续秒数]} —— 普通玩家可用（顶掉原版需要 OP 的 /weather）。
 *
 * <p>状态机全在公共核心 {@link WeatherVoteService} 里：在线 ≤ directThreshold 直接生效，
 * 否则发起投票；本人、控制台、OP 直接执行。</p>
 */
public final class WeatherCommand extends CommandBase {

    private static final String USAGE = "用法：/weather clear|rain|thunder [持续秒数]";

    @Override
    public String getName() {
        return "weather";
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
            for (String option : new String[]{"clear", "rain", "thunder"}) {
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
        Actor actor = actorOf(sender);
        if (args == null || args.length == 0) {
            service.weatherUsage(actor);
            return;
        }
        String type = args[0] == null ? "" : args[0].toLowerCase(Locale.ROOT);
        if (!"clear".equals(type) && !"rain".equals(type) && !"thunder".equals(type)) {
            reply(sender, USAGE);
            service.weatherUsage(actor);
            return;
        }
        Integer duration = null;
        if (args.length >= 2) {
            duration = parseDuration(args[1]);
        }
        service.weather(actor, type, duration);
    }

    private static Integer parseDuration(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            int seconds = Integer.parseInt(raw.trim());
            if (seconds < 1 || seconds > 1000000) {
                return null;
            }
            return seconds;
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    static Actor actorOf(ICommandSender sender) {
        if (sender instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) sender;
            return Actor.player(player.getUniqueID(), player.getName(), player.canUseCommand(2, "weather"));
        }
        return Actor.console();
    }

    private static void reply(ICommandSender sender, String text) {
        if (sender == null || text == null) {
            return;
        }
        sender.sendMessage(new TextComponentString("[天气] " + text));
    }
}
