package com.mogutofu.mcai.neoforge;

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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.File;
import java.util.UUID;

/**
 * 玩家天气指令 + 游戏内投票，逻辑全部交给公共核心 {@link WeatherVoteService}。
 *
 * <pre>
 * /weather clear|rain|thunder [持续秒数]   普通玩家可用（会替换原版需要 OP 的 /weather）
 * /vote yes|no|status                     天气投票
 * </pre>
 *
 * <p>配置在 config/mcai-weather.json（由 DSH 插件设置面板写入），每次 /weather 重新读：
 * 在线 ≤ directThreshold（默认 2）直接生效；否则发起投票，发起人自动 1 票同意，
 * 同意票达到 approveNeed（默认 2）通过，反对票达到 rejectNeed（默认 1）作废，
 * 超时 timeoutSeconds（默认 60 秒）作废，发起有 cooldownSeconds（默认 60 秒）冷却。</p>
 */
public final class WeatherVote implements WeatherVoteService.Host {

    private static final String PREFIX = "[天气] ";

    private static final WeatherVote HOST = new WeatherVote();
    private static final WeatherVoteService SERVICE = new WeatherVoteService(HOST);

    /** 当前正在执行的控制台/玩家指令来源，控制台的天气提示也回给指令执行者（含 RCON）。 */
    private static CommandSourceStack activeSource;

    private WeatherVote() {
    }

    // ── 指令注册 ──────────────────────────────────────────────────────

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("weather")
                .executes(WeatherVote::weatherUsage)
                .then(Commands.literal("clear")
                        .executes(context -> request(context, "clear", null))
                        .then(Commands.argument("duration", IntegerArgumentType.integer(1, 1000000))
                                .executes(context -> request(context, "clear", IntegerArgumentType.getInteger(context, "duration")))))
                .then(Commands.literal("rain")
                        .executes(context -> request(context, "rain", null))
                        .then(Commands.argument("duration", IntegerArgumentType.integer(1, 1000000))
                                .executes(context -> request(context, "rain", IntegerArgumentType.getInteger(context, "duration")))))
                .then(Commands.literal("thunder")
                        .executes(context -> request(context, "thunder", null))
                        .then(Commands.argument("duration", IntegerArgumentType.integer(1, 1000000))
                                .executes(context -> request(context, "thunder", IntegerArgumentType.getInteger(context, "duration"))))));

        dispatcher.register(Commands.literal("vote")
                .executes(WeatherVote::voteUsage)
                .then(Commands.literal("yes").executes(context -> castVote(context, true)))
                .then(Commands.literal("no").executes(context -> castVote(context, false)))
                .then(Commands.literal("status").executes(WeatherVote::voteStatus)));

        McaiMod.LOGGER.info("[mcai] 已注册 /weather 与 /vote（游戏内天气投票）");
    }

    // ── 服务端生命周期 ────────────────────────────────────────────────

    public static void onServerTick() {
        SERVICE.tick();
    }

    public static void onPlayerLogout(UUID playerId) {
        SERVICE.logout(playerId);
    }

    public static void reset() {
        SERVICE.reset();
    }

    // ── /weather ──────────────────────────────────────────────────────

    private static int weatherUsage(CommandContext<CommandSourceStack> context) {
        return withSource(context.getSource(), () -> SERVICE.weatherUsage(actorOf(context.getSource())));
    }

    private static int request(CommandContext<CommandSourceStack> context, String type, Integer durationSeconds) {
        return withSource(context.getSource(),
                () -> SERVICE.weather(actorOf(context.getSource()), type, durationSeconds));
    }

    // ── /vote ─────────────────────────────────────────────────────────

    private static int voteUsage(CommandContext<CommandSourceStack> context) {
        return withSource(context.getSource(), () -> SERVICE.voteUsage(actorOf(context.getSource())));
    }

    private static int castVote(CommandContext<CommandSourceStack> context, boolean agree) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("只有游戏内玩家能投票"));
            return 0;
        }
        return withSource(context.getSource(), () -> SERVICE.vote(actorOf(player), agree));
    }

    private static int voteStatus(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("只有游戏内玩家能投票"));
            return 0;
        }
        return withSource(context.getSource(), () -> SERVICE.status(actorOf(player)));
    }

    /** 同步执行一次天气服务调用，把控制台反馈路由回指令执行者。 */
    private static int withSource(CommandSourceStack source, java.util.function.IntSupplier action) {
        CommandSourceStack previous = activeSource;
        activeSource = source;
        try {
            return action.getAsInt();
        } finally {
            activeSource = previous;
        }
    }

    // ── 小工具 ────────────────────────────────────────────────────────

    private static Actor actorOf(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return Actor.console();
        }
        return actorOf(player);
    }

    private static Actor actorOf(ServerPlayer player) {
        // 1.21.1 仍是数字权限等级：2 = OP（原版 /weather 的门槛）
        return Actor.player(player.getUUID(), player.getGameProfile().getName(), player.hasPermissions(2));
    }

    // ── WeatherVoteService.Host ───────────────────────────────────────

    @Override
    public int onlineCount() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? 0 : server.getPlayerList().getPlayerCount();
    }

    @Override
    public void send(Actor actor, String message) {
        String line = PREFIX + message;
        if (actor.console) {
            McaiMod.LOGGER.info("[mcai] {}", line);
            CommandSourceStack source = activeSource;
            if (source != null && source.getPlayer() == null) {
                // 控制台（包括 RCON）执行 /weather 时，用法/提示也要回给执行者
                source.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
            }
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(actor.id);
        if (player == null) {
            McaiMod.LOGGER.info("[mcai] {}（{} 已不在线）", line, actor.displayName());
            return;
        }
        player.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void broadcast(String message) {
        String line = PREFIX + message;
        McaiMod.LOGGER.info("[mcai] {}", line);
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        server.getPlayerList().broadcastSystemMessage(
                Component.literal(line).withStyle(ChatFormatting.YELLOW), false);
    }

    @Override
    public void applyWeather(String type, Integer durationSeconds) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        try {
            ServerLevel level = server.overworld();
            int ticks = durationSeconds == null
                    ? WeatherVoteService.defaultDurationTicks()
                    : Math.max(1, durationSeconds * 20);
            switch (type) {
                case "clear" -> level.setWeatherParameters(ticks, 0, false, false);
                case "rain" -> level.setWeatherParameters(0, ticks, true, false);
                case "thunder" -> level.setWeatherParameters(0, ticks, true, true);
                default -> {
                }
            }
        } catch (Throwable broken) {
            McaiMod.LOGGER.error("[mcai] 设置天气失败：{}", broken.toString());
        }
    }

    @Override
    public void log(String message) {
        McaiMod.LOGGER.info("[mcai] {}", message);
    }

    @Override
    public File configFile() {
        return McaiMod.weatherConfigFile();
    }
}
