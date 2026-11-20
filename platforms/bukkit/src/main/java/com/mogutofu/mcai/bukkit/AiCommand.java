package com.mogutofu.mcai.bukkit;

import com.mogutofu.mcai.core.AiSettings;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@code /ai}：文字聊天 + 附件桥接。
 *
 * <p>{@code /ai screenshot}、{@code /ai file} 先看玩家有没有 mcai-client 1.4.0 桥接：
 * 有就走 {@code mcai:bridge} + HTTP 上传；没有（没装客户端 / 旧客户端 / 端口没起来）
 * 按 1.3.0 的行为降级为只发文字并提示。</p>
 */
public final class AiCommand implements CommandExecutor, TabCompleter {

    private static final String USAGE = "用法：/ai <内容>（或 /ai message <内容>；装了 mcai-client 1.4.0 还能用 "
            + "/ai screenshot [内容] 和 /ai file <本地路径> <消息>）";
    private static final String NO_CLIENT = "客户端未安装 mcai-client 1.4.0（或附件桥接未启用），已改为只发送消息。";

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
            askText(player, text);
            return true;
        }
        if ("screenshot".equals(sub)) {
            String text = join(args, 1);
            boolean bridged = ready(player, text);
            if (!bridged) {
                if (text.isEmpty()) {
                    player.sendMessage(USAGE);
                    return true;
                }
                player.sendMessage("[小铜] " + NO_CLIENT);
                askText(player, text);
                return true;
            }
            player.sendMessage("[小铜] 正在截图…");
            if (!plugin.bridge().requestAttachment(player, true, "", text)) {
                // 桥接在 isBridged 之后失效（端口停了/发送异常）：降级
                plugin.getLogger().warning("向 " + player.getName() + " 发起截图请求失败，降级为只发文字");
                player.sendMessage("[小铜] 附件请求发送失败，已改为只发送消息。");
                plugin.askAi(player.getUniqueId(), player.getName(), text, null);
            }
            return true;
        }
        if ("file".equals(sub)) {
            String raw = join(args, 1);
            String[] parsed = splitPathAndMessage(raw);
            if (parsed[0].isEmpty()) {
                player.sendMessage("用法：/ai file <本地路径> <消息>（有空格时用引号包起来）");
                return true;
            }
            String path = parsed[0];
            String text = parsed[1];
            if (!ready(player, text)) {
                player.sendMessage("[小铜] " + NO_CLIENT);
                if (!text.isEmpty()) {
                    askText(player, text);
                }
                return true;
            }
            player.sendMessage("[小铜] 正在读取 " + path + " …");
            if (!plugin.bridge().requestAttachment(player, false, path, text)) {
                plugin.getLogger().warning("向 " + player.getName() + " 发起文件请求失败，降级为只发文字");
                player.sendMessage("[小铜] 附件请求发送失败，已改为只发送消息。");
                plugin.askAi(player.getUniqueId(), player.getName(), text, null);
            }
            return true;
        }
        askText(player, join(args, 0));
        return true;
    }

    /** 前置检查：AI 开启 + 限流 + 有没有桥接。返回 true 表示可以走附件。 */
    private boolean ready(Player player, String text) {
        AiSettings settings = plugin.aiSettings();
        if (!settings.enabled) {
            player.sendMessage("[小铜] AI 未开启");
            return false;
        }
        if (!plugin.cooldown().tryAcquire(player.getUniqueId(), settings.rateLimitSeconds)) {
            player.sendMessage("[小铜] 慢一点，刚问过啦");
            return false;
        }
        return plugin.bridge() != null && plugin.bridge().isBridged(player);
    }

    private void askText(Player player, String text) {
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
        plugin.askAi(player.getUniqueId(), player.getName(), text, null);
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
