package com.mogutofu.mcai;

import com.mogutofu.mcai.net.ClientPresence;
import com.mogutofu.mcai.net.McaiNetwork;
import com.mogutofu.mcai.net.PendingTransfers;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@code /ai} 指令树 —— 权限等级 0，所有玩家一视同仁。
 *
 * <pre>
 * /ai                              用法提示
 * /ai &lt;消息&gt;                       兼容旧行为：只发消息
 * /ai message &lt;消息&gt;               只发消息
 * /ai screenshot [消息]            截图 + 消息发给 AI
 * /ai file &lt;本地路径&gt; &lt;消息&gt;      本地文件 + 消息发给 AI
 * </pre>
 *
 * <p>literal 子节点注册在 greedy 参数之前：Brigadier 按声明顺序匹配，所以
 * {@code /ai message 你好} 走子命令，老格式 {@code /ai 你好} 仍然落到 greedy 分支。
 * 没有客户端模组（没收到 ClientHello）时附件命令降级为只发消息。</p>
 */
@Mod.EventBusSubscriber(modid = McaiMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AiCommandHandler {

    private static final String USAGE = "用法：/ai <内容>";
    private static final String EXTENDED_USAGE =
            "用法：/ai message <内容> | /ai screenshot [消息] | /ai file <本地路径> <消息>";
    private static final String CLIENT_MISSING = "客户端未安装 mcai-client";

    private AiCommandHandler() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("ai")
                .executes(AiCommandHandler::usage)
                // 新：只发消息
                .then(Commands.literal("message")
                        .executes(AiCommandHandler::extendedUsage)
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(AiCommandHandler::messageCommand)))
                // 新：截图（消息可省略）
                .then(Commands.literal("screenshot")
                        .executes(context -> screenshotCommand(context, ""))
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(context -> screenshotCommand(context, StringArgumentType.getString(context, "message")))))
                // 新：本地文件（路径 + 消息都必填）
                .then(Commands.literal("file")
                        .executes(AiCommandHandler::extendedUsage)
                        .then(Commands.argument("path", StringArgumentType.string())
                                .then(Commands.argument("message", StringArgumentType.greedyString())
                                        .executes(AiCommandHandler::fileCommand))))
                // 旧格式兜底：必须最后注册，literal 优先
                .then(Commands.argument("text", StringArgumentType.greedyString())
                        .executes(AiCommandHandler::legacyCommand)));

        McaiMod.LOGGER.info("[mcai] 已注册 /ai 指令（兼容旧格式，新增 message/screenshot/file）");
    }

    // ── 各分支 ────────────────────────────────────────────────────────

    private static int usage(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal(USAGE).withStyle(ChatFormatting.GRAY), false);
        return 0;
    }

    private static int extendedUsage(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal(EXTENDED_USAGE).withStyle(ChatFormatting.GRAY), false);
        context.getSource().sendSuccess(
                () -> Component.literal("旧格式 /ai <内容> 仍然可用").withStyle(ChatFormatting.DARK_GRAY), false);
        context.getSource().sendSuccess(
                () -> Component.literal("文件路径要用引号：/ai file \"C:/Users/me/笔记.txt\" 帮我看看；Windows 反斜杠写两个或直接用 /")
                        .withStyle(ChatFormatting.DARK_GRAY), false);
        return 0;
    }

    private static int legacyCommand(CommandContext<CommandSourceStack> context) {
        return submitText(context, StringArgumentType.getString(context, "text"));
    }

    private static int messageCommand(CommandContext<CommandSourceStack> context) {
        return submitText(context, StringArgumentType.getString(context, "message"));
    }

    private static int submitText(CommandContext<CommandSourceStack> context, String rawMessage) {
        ServerPlayer player = playerOrNull(context);
        if (player == null) {
            return 0;
        }
        if (!ensureEnabled(player)) {
            return 0;
        }
        String message = rawMessage == null ? "" : rawMessage.trim();
        if (message.isEmpty()) {
            usage(context);
            return 0;
        }
        if (!ensureNotRateLimited(player)) {
            return 0;
        }
        ChatFeedback.thinking(player);
        AiHttpClient.ask(context.getSource().getServer(), player, message);
        return 1;
    }

    private static int screenshotCommand(CommandContext<CommandSourceStack> context, String rawMessage) {
        ServerPlayer player = playerOrNull(context);
        if (player == null) {
            return 0;
        }
        if (!ensureEnabled(player)) {
            return 0;
        }
        String message = rawMessage == null ? "" : rawMessage.trim();
        boolean hasClient = ClientPresence.hasClient(player.getUUID());

        if (!hasClient && message.isEmpty()) {
            // 没客户端又没消息，呼叫 AI 也不会有东西可发：只提示用法
            usage(context);
            ChatFeedback.notice(player,
                    "/ai screenshot 需要客户端安装 mcai-client；未安装时请带上消息，例如 /ai screenshot 看看我的基地",
                    ChatFormatting.DARK_GRAY);
            return 0;
        }
        if (!ensureNotRateLimited(player)) {
            return 0;
        }
        if (!hasClient) {
            ChatFeedback.degradeToText(context.getSource().getServer(), player, message, CLIENT_MISSING);
            return 1;
        }
        if (!McaiNetwork.isReady()) {
            ChatFeedback.degradeToText(context.getSource().getServer(), player, message, "服务端附件通道未就绪");
            return 1;
        }
        ChatFeedback.notice(player, "正在获取游戏画面…", ChatFormatting.DARK_GRAY);
        PendingTransfers.startScreenshot(context.getSource().getServer(), player, message);
        return 1;
    }

    private static int fileCommand(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = playerOrNull(context);
        if (player == null) {
            return 0;
        }
        if (!ensureEnabled(player)) {
            return 0;
        }
        String path = StringArgumentType.getString(context, "path").trim();
        String message = StringArgumentType.getString(context, "message").trim();
        if (path.isEmpty()) {
            context.getSource().sendFailure(Component.literal("路径不能为空。"));
            return 0;
        }
        if (!ensureNotRateLimited(player)) {
            return 0;
        }
        if (!ClientPresence.hasClient(player.getUUID())) {
            ChatFeedback.degradeToText(context.getSource().getServer(), player, message, CLIENT_MISSING);
            return message.isEmpty() ? 0 : 1;
        }
        if (!McaiNetwork.isReady()) {
            ChatFeedback.degradeToText(context.getSource().getServer(), player, message, "服务端附件通道未就绪");
            return message.isEmpty() ? 0 : 1;
        }
        String displayPath = path.length() > 120 ? path.substring(0, 120) + "…" : path;
        ChatFeedback.notice(player, "正在读取文件 " + displayPath + "…", ChatFormatting.DARK_GRAY);
        PendingTransfers.startFile(context.getSource().getServer(), player, message, path);
        return 1;
    }

    // ── 公共校验 ──────────────────────────────────────────────────────

    private static ServerPlayer playerOrNull(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("这个指令只能在游戏里用。"));
        }
        return player;
    }

    private static boolean ensureEnabled(ServerPlayer player) {
        if (McaiConfig.enabled()) {
            return true;
        }
        ChatFeedback.notice(player, "AI 未开启", ChatFormatting.RED);
        return false;
    }

    private static boolean ensureNotRateLimited(ServerPlayer player) {
        if (RateLimiter.tryAcquire(player.getUUID(), McaiConfig.rateLimitSeconds())) {
            return true;
        }
        ChatFeedback.notice(player, "慢一点，刚问过啦", ChatFormatting.GRAY);
        return false;
    }
}