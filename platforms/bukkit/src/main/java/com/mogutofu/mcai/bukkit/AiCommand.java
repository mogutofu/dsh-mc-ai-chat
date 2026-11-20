package com.mogutofu.mcai.bukkit;

import com.mogutofu.mcai.core.AiClient;
import com.mogutofu.mcai.core.AiSettings;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** {@code /ai}：Bukkit 版只发文字；screenshot/file 会降级为只发消息并提示。 */
public final class AiCommand implements CommandExecutor, TabCompleter {

    private static final String USAGE = "用法：/ai <内容>（或 /ai message <内容>）";

    private final McaiPlugin plugin;

    AiCommand(McaiPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("这个指令只能在游戏里用。");
            return true;
        }
        Player player = (Player) sender;
        if (args.length == 0) {
            player.sendMessage(USAGE);
            return true;
        }
        String sub = args[0].toLowerCase();
        if ("message".equals(sub)) {
            String text = join(args, 1);
            if (text.isEmpty()) {
                player.sendMessage(USAGE);
                return true;
            }
            ask(player, text);
            return true;
        }
        if ("screenshot".equals(sub)) {
            String text = join(args, 1);
            if (text.isEmpty()) {
                player.sendMessage(USAGE);
                player.sendMessage("Bukkit 版没有客户端附件功能，请用 /ai <内容>。");
                return true;
            }
            player.sendMessage("[小铜] Bukkit 版不支持截图，已改为只发送消息。");
            ask(player, text);
            return true;
        }
        if ("file".equals(sub)) {
            String raw = join(args, 1);
            String[] parsed = splitPathAndMessage(raw);
            if (parsed[0].isEmpty() || parsed[1].isEmpty()) {
                player.sendMessage("用法：/ai file <本地路径> <消息>（Bukkit 版不支持附件，只会发送消息）");
                return true;
            }
            player.sendMessage("[小铜] Bukkit 版不支持读取本地文件，已改为只发送消息。");
            ask(player, parsed[1]);
            return true;
        }
        ask(player, join(args, 0));
        return true;
    }

    private void ask(Player player, String text) {
        AiSettings settings = plugin.aiSettings();
        if (!settings.enabled) {
            player.sendMessage("[小铜] AI 未开启");
            return;
        }
        if (!plugin.cooldown().tryAcquire(player.getUniqueId(), settings.rateLimitSeconds)) {
            player.sendMessage("[小铜] 慢一点，刚问过啦");
            return;
        }
        player.sendMessage("[小铜] 正在输入…");
        AiClient.ask(settings, player.getUniqueId(), player.getName(), text, null, plugin.getLogger()::info,
                (ok, reply) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Player current = plugin.getServer().getPlayer(player.getUniqueId());
                    if (current == null) {
                        return;
                    }
                    current.sendMessage(ok ? "[小铜] " + reply : "[小铜] 出错了：" + reply);
                }));
    }

    private static String join(String[] args, int from) {
        if (from >= args.length) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(args[i]);
        }
        return out.toString().trim();
    }

    /** 与 Forge 版一致：支持引号包路径，也支持无空格裸路径。 */
    static String[] splitPathAndMessage(String raw) {
        String path;
        String message;
        if (raw.startsWith("\"")) {
            int close = raw.indexOf('"', 1);
            if (close < 0) {
                return new String[]{"", ""};
            }
            path = raw.substring(1, close).trim();
            message = raw.substring(close + 1).trim();
        } else {
            int space = raw.indexOf(' ');
            if (space < 0) {
                path = raw.trim();
                message = "";
            } else {
                path = raw.substring(0, space).trim();
                message = raw.substring(space + 1).trim();
            }
        }
        return new String[]{path, message};
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("message", "screenshot", "file"), args[0]);
        }
        return new ArrayList<>();
    }

    private static List<String> filter(List<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.startsWith(prefix.toLowerCase())) {
                out.add(option);
            }
        }
        return out;
    }
}
