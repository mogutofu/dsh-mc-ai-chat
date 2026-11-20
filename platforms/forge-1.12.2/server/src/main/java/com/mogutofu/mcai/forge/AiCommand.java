package com.mogutofu.mcai.forge;

import com.mogutofu.mcai.core.AiSettings;
import com.mogutofu.mcai.core.Cooldown;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * {@code /ai} 指令 —— 权限等级 0，所有玩家一视同仁。
 *
 * <pre>
 * /ai                              用法提示
 * /ai &lt;消息&gt;                       兼容旧行为：只发消息
 * /ai message &lt;消息&gt;               只发消息
 * /ai screenshot [消息]            截图 + 消息发给 AI
 * /ai file &lt;本地路径&gt; &lt;消息&gt;      本地文件 + 消息发给 AI
 * </pre>
 *
 * <p>1.12.2 没有 Brigadier，第一个词按字面量分支匹配；{@code message}/{@code screenshot}/
 * {@code file} 之外的内容整段当旧格式的文本。没有客户端模组（没收到 ClientHello）时
 * 附件命令降级为只发消息。</p>
 */
public final class AiCommand extends CommandBase {

    private static final String USAGE = "用法：/ai <内容>";
    private static final String EXTENDED_USAGE =
            "用法：/ai message <内容> | /ai screenshot [消息] | /ai file <本地路径> <消息>";
    private static final String CLIENT_MISSING = "客户端未安装 mcai-client";

    /** 每玩家 /ai 冷却，语义与 1.2.0 的 RateLimiter 一致。 */
    private static final Cooldown COOLDOWN = new Cooldown();

    @Override
    public String getName() {
        return "ai";
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
            for (String option : new String[]{"message", "screenshot", "file"}) {
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
        EntityPlayerMP player = sender instanceof EntityPlayerMP ? (EntityPlayerMP) sender : null;
        if (args == null || args.length == 0) {
            if (player == null) {
                send(sender, USAGE);
                return;
            }
            sendUsage(player);
            return;
        }
        String head = args[0] == null ? "" : args[0].toLowerCase(Locale.ROOT);
        if ("message".equals(head)) {
            submitText(server, sender, player, join(args, 1));
            return;
        }
        if ("screenshot".equals(head)) {
            screenshot(server, sender, player, join(args, 1));
            return;
        }
        if ("file".equals(head)) {
            file(server, sender, player, join(args, 1));
            return;
        }
        // 旧格式兜底：/ai 你好
        submitText(server, sender, player, join(args, 0));
    }

    // ── 各分支 ────────────────────────────────────────────────────────

    private void submitText(MinecraftServer server, ICommandSender sender, EntityPlayerMP player, String rawMessage) {
        if (player == null) {
            send(sender, "这个指令只能在游戏里用。");
            return;
        }
        String message = rawMessage == null ? "" : rawMessage.trim();
        if (message.isEmpty()) {
            sendUsage(player);
            return;
        }
        AiSettings settings = AiSettings.load(McaiMod.commonConfig());
        if (!settings.enabled) {
            ChatFeedback.notice(player, "AI 未开启", TextFormatting.RED);
            return;
        }
        if (!acquireCooldown(player, settings)) {
            return;
        }
        ChatFeedback.thinking(player);
        AiRequests.ask(server, player, message);
    }

    private void screenshot(MinecraftServer server, ICommandSender sender, EntityPlayerMP player, String rawMessage) {
        if (player == null) {
            send(sender, "这个指令只能在游戏里用。");
            return;
        }
        String message = rawMessage == null ? "" : rawMessage.trim();
        AiSettings settings = AiSettings.load(McaiMod.commonConfig());
        if (!settings.enabled) {
            ChatFeedback.notice(player, "AI 未开启", TextFormatting.RED);
            return;
        }
        boolean hasClient = ClientPresence.hasClient(player.getUniqueID());
        if (!hasClient && message.isEmpty()) {
            // 没客户端又没消息，呼叫 AI 也不会有东西可发：只提示用法
            sendUsage(player);
            ChatFeedback.notice(player,
                    "/ai screenshot 需要客户端安装 mcai-client；未安装时请带上消息，例如 /ai screenshot 看看我的基地",
                    TextFormatting.DARK_GRAY);
            return;
        }
        if (!acquireCooldown(player, settings)) {
            return;
        }
        if (!hasClient) {
            ChatFeedback.degradeToText(server, player, message, CLIENT_MISSING);
            return;
        }
        if (!McaiNetwork.isReady()) {
            ChatFeedback.degradeToText(server, player, message, "服务端附件通道未就绪");
            return;
        }
        ChatFeedback.notice(player, "正在获取游戏画面…", TextFormatting.DARK_GRAY);
        PendingTransfers.startScreenshot(server, player, message);
    }

    private void file(MinecraftServer server, ICommandSender sender, EntityPlayerMP player, String raw) {
        if (player == null) {
            send(sender, "这个指令只能在游戏里用。");
            return;
        }
        String text = raw == null ? "" : raw.trim();
        String path;
        String message;
        // raw = "<本地路径>" <消息> 或 本地路径 <消息>；路径有空格时用引号包起来
        if (text.startsWith("\"")) {
            int close = text.indexOf('"', 1);
            if (close < 0) {
                send(sender, "路径的引号没有闭合。");
                return;
            }
            path = text.substring(1, close).trim();
            message = text.substring(close + 1).trim();
        } else {
            int space = text.indexOf(' ');
            if (space < 0) {
                path = text;
                message = "";
            } else {
                path = text.substring(0, space).trim();
                message = text.substring(space + 1).trim();
            }
        }
        if (path.isEmpty()) {
            send(sender, "路径不能为空。");
            return;
        }
        if (message.isEmpty()) {
            send(sender, "用法：/ai file <本地路径> <消息>（路径有空格时用引号包起来）");
            return;
        }
        AiSettings settings = AiSettings.load(McaiMod.commonConfig());
        if (!settings.enabled) {
            ChatFeedback.notice(player, "AI 未开启", TextFormatting.RED);
            return;
        }
        if (!acquireCooldown(player, settings)) {
            return;
        }
        if (!ClientPresence.hasClient(player.getUniqueID())) {
            ChatFeedback.degradeToText(server, player, message, CLIENT_MISSING);
            return;
        }
        if (!McaiNetwork.isReady()) {
            ChatFeedback.degradeToText(server, player, message, "服务端附件通道未就绪");
            return;
        }
        String displayPath = path.length() > 120 ? path.substring(0, 120) + "…" : path;
        ChatFeedback.notice(player, "正在读取文件 " + displayPath + "…", TextFormatting.DARK_GRAY);
        PendingTransfers.startFile(server, player, message, path);
    }

    // ── 公共校验 / 小工具 ─────────────────────────────────────────────

    private boolean acquireCooldown(EntityPlayerMP player, AiSettings settings) {
        if (COOLDOWN.tryAcquire(player.getUniqueID(), settings.rateLimitSeconds)) {
            return true;
        }
        ChatFeedback.notice(player, "慢一点，刚问过啦", TextFormatting.GRAY);
        return false;
    }

    private void sendUsage(EntityPlayerMP player) {
        ChatFeedback.notice(player, USAGE, TextFormatting.GRAY);
        ChatFeedback.notice(player, EXTENDED_USAGE, TextFormatting.GRAY);
        ChatFeedback.notice(player, "旧格式 /ai <内容> 仍然可用", TextFormatting.DARK_GRAY);
        ChatFeedback.notice(player,
                "文件路径：/ai file \"C:\\Users\\me\\笔记.txt\" 帮我看看（路径有空格要加引号，反斜杠直接写）",
                TextFormatting.DARK_GRAY);
        ChatFeedback.notice(player, "路径不加引号也行（不能有空格）：/ai file C:/Users/me/a.md 帮我看看",
                TextFormatting.DARK_GRAY);
    }

    private static void send(ICommandSender sender, String text) {
        if (sender == null || text == null) {
            return;
        }
        sender.sendMessage(new TextComponentString(text));
    }

    /** "a b c"[start..] 用空格拼回一整段，兼容中文与路径里的空格。 */
    private static String join(String[] args, int start) {
        if (args == null || start >= args.length) {
            return "";
        }
        StringBuilder builder = new StringBuilder(64);
        for (int index = start; index < args.length; index++) {
            if (index > start) {
                builder.append(' ');
            }
            builder.append(args[index] == null ? "" : args[index]);
        }
        return builder.toString();
    }
}
