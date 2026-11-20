package com.mogutofu.mcai.fabric;

import com.mogutofu.mcai.core.Actor;
import com.mogutofu.mcai.core.WeatherVoteService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.util.List;
import java.util.UUID;

/**
 * 玩家天气指令 + 游戏内投票（逻辑在公共核心 {@link WeatherVoteService}）。
 *
 * <pre>
 * /weather clear|rain|thunder [持续秒数]   普通玩家可用（替换原版需要 OP 的 /weather）
 * /vote yes|no|status                     天气投票
 * </pre>
 *
 * <p>配置在 {@code config/mcai-weather.json}，每次 /weather 重新读（由 DSH 插件设置面板写入）。
 * 在线玩家 ≤ directThreshold（默认 2）时直接生效；否则发起投票，发起人自动算 1 票同意，
 * 同意票达到 approveNeed（默认 2）通过，反对票达到 rejectNeed（默认 1）作废，
 * 超时 timeoutSeconds（默认 60 秒）作废。</p>
 */
public final class Weather {

    private static final String PREFIX = "[天气] ";
    private static final WeatherVoteService SERVICE = new WeatherVoteService(new Host());

    private Weather() {
    }

    // ── 指令注册 ──────────────────────────────────────────────────────

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // 原版 /weather 要求 OP（permission 2），普通玩家用不了；把原版节点拿掉换成我们自己的。
        try {
            dispatcher.getRoot().getChildren()
                    .removeIf(node -> "weather".equals(node.getName()) || "vote".equals(node.getName()));
            McaiFabric.LOGGER.info("[mcai] 已替换原版 /weather（原版需要 OP，现在普通玩家也能发起天气投票）");
        } catch (Throwable broken) {
            McaiFabric.LOGGER.warn("[mcai] 替换原版 /weather 失败（普通玩家可能仍受原版权限限制）：{}", broken.toString());
        }

        dispatcher.register(Commands.literal("weather")
                .executes(Weather::weatherUsage)
                .then(Commands.literal("clear")
                        .executes(context -> request(context, "clear", null))
                        .then(Commands.argument("duration", IntegerArgumentType.integer(1, 1000000))
                                .executes(context -> request(context, "clear",
                                        IntegerArgumentType.getInteger(context, "duration")))))
                .then(Commands.literal("rain")
                        .executes(context -> request(context, "rain", null))
                        .then(Commands.argument("duration", IntegerArgumentType.integer(1, 1000000))
                                .executes(context -> request(context, "rain",
                                        IntegerArgumentType.getInteger(context, "duration")))))
                .then(Commands.literal("thunder")
                        .executes(context -> request(context, "thunder", null))
                        .then(Commands.argument("duration", IntegerArgumentType.integer(1, 1000000))
                                .executes(context -> request(context, "thunder",
                                        IntegerArgumentType.getInteger(context, "duration"))))));

        dispatcher.register(Commands.literal("vote")
                .executes(context -> SERVICE.voteUsage(actor(context.getSource())))
                .then(Commands.literal("yes").executes(context -> SERVICE.vote(actor(context.getSource()), true)))
                .then(Commands.literal("no").executes(context -> SERVICE.vote(actor(context.getSource()), false)))
                .then(Commands.literal("status").executes(context -> SERVICE.status(actor(context.getSource())))));

        McaiFabric.LOGGER.info("[mcai] 已注册 /weather 与 /vote（游戏内天气投票）");
    }

    // ── 指令分支 ──────────────────────────────────────────────────────

    private static int weatherUsage(CommandContext<CommandSourceStack> context) {
        return SERVICE.weatherUsage(actor(context.getSource()));
    }

    private static int request(CommandContext<CommandSourceStack> context, String type, Integer durationSeconds) {
        return SERVICE.weather(actor(context.getSource()), type, durationSeconds);
    }

    // ── 生命周期（由 McaiFabric 的事件调用） ──────────────────────────

    public static void tick() {
        SERVICE.tick();
    }

    public static void logout(UUID playerId) {
        SERVICE.logout(playerId);
    }

    public static void reset() {
        SERVICE.reset();
    }

    // ── Host 实现 ─────────────────────────────────────────────────────

    private static final class Host implements WeatherVoteService.Host {

        @Override
        public int onlineCount() {
            MinecraftServer server = McaiFabric.server();
            return server == null ? 0 : server.getPlayerList().getPlayerCount();
        }

        @Override
        public void send(Actor actor, String message) {
            String line = PREFIX + message;
            if (actor == null || actor.console || actor.id == null) {
                McaiFabric.LOGGER.info(line);
                return;
            }
            MinecraftServer server = McaiFabric.server();
            ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(actor.id);
            if (player != null) {
                player.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.GRAY));
            }
        }

        @Override
        public void broadcast(String message) {
            String line = PREFIX + message;
            McaiFabric.LOGGER.info(line);
            MinecraftServer server = McaiFabric.server();
            if (server == null) {
                return;
            }
            List<ServerPlayer> players = server.getPlayerList().getPlayers();
            for (ServerPlayer player : players) {
                player.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.YELLOW));
            }
        }

        @Override
        public void applyWeather(String type, Integer durationSeconds) {
            if (!"clear".equals(type) && !"rain".equals(type) && !"thunder".equals(type)) {
                return;
            }
            MinecraftServer server = McaiFabric.server();
            if (server == null) {
                return;
            }
            // 26.x 的 ServerLevel 已经没有 setWeatherParameters 了，直接执行原版 /weather 命令
            // （和 NeoForge 26.x 版同方案）；1.2.0 无持续秒数时默认 6000 tick = 300 秒。
            int seconds = durationSeconds == null ? 5 * 60 : Math.max(1, Math.min(1000000, durationSeconds));
            try {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                        "weather " + type + " " + seconds);
            } catch (Throwable broken) {
                McaiFabric.LOGGER.error("[mcai] 设置天气失败：{}", broken.toString());
            }
        }

        @Override
        public void log(String message) {
            McaiFabric.LOGGER.info("[mcai] " + message);
        }

        @Override
        public File configFile() {
            return McaiFabric.weatherConfigFile();
        }
    }

    /** 指令执行者 -> 公共核心的 Actor。控制台 / OP 直接执行，普通玩家走投票。 */
    private static Actor actor(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return Actor.console();
        }
        return Actor.player(player.getUUID(), player.getName().getString(), isOp(source));
    }

    /**
     * OP 判定：26.x 的指令权限 API 改过几轮，这里用反射拿
     * {@code CommandSourceStack.hasPermission(int)}，拿不到就当普通玩家（只是要多投票，
     * 不会崩）；控制台本来就不走这里。
     */
    private static boolean isOp(CommandSourceStack source) {
        try {
            Object value = CommandSourceStack.class.getMethod("hasPermission", int.class).invoke(source, 2);
            return value instanceof Boolean && (Boolean) value;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
