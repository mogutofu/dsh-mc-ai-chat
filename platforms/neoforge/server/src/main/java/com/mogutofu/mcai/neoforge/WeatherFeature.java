package com.mogutofu.mcai.neoforge;

import com.mogutofu.mcai.core.Actor;
import com.mogutofu.mcai.core.WeatherSettings;
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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

/**
 * 玩家天气指令 + 游戏内投票（26.x 适配层，逻辑全部在 common 的 {@link WeatherVoteService}）。
 *
 * <pre>
 * /weather clear|rain|thunder [持续秒数]   普通玩家可用（会替换原版需要 OP 的 /weather）
 * /vote yes|no|status                     天气投票
 * </pre>
 *
 * <p>规则（配置在 config/mcai-weather.json，由 DSH 插件设置面板写入，每次 /weather 重新读）：
 * 在线玩家 ≤ directThreshold（默认 2）时直接生效；否则发起投票，
 * 发起人自动算 1 票同意，同意票达到 approveNeed（默认 2）通过，
 * 反对票达到 rejectNeed（默认 1）作废，超时 timeoutSeconds（默认 60 秒）作废。
 * 每人发起有 cooldownSeconds（默认 60 秒）冷却。控制台 / OP 直接生效。</p>
 */
public final class WeatherFeature implements WeatherVoteService.Host {

    private static final WeatherFeature INSTANCE = new WeatherFeature();

    private final WeatherVoteService service = new WeatherVoteService(this);
    private volatile MinecraftServer server;

    private WeatherFeature() {
    }

    public static WeatherFeature get() {
        return INSTANCE;
    }

    // ── 生命周期 ──────────────────────────────────────────────────────

    public void attach(MinecraftServer server) {
        this.server = server;
    }

    /** 每 tick 调一次（common 里每 20 tick 判定一次超时）。 */
    public void tick() {
        service.tick();
    }

    /** 玩家登出：清冷却，并在在线人数掉到 0 时作废投票。 */
    public void logout(UUID playerId) {
        service.logout(playerId);
    }

    public void reset() {
        service.reset();
        this.server = null;
    }

    /** 缺文件时补一份默认配置（DSH 插件之后会覆盖写）。 */
    public static void writeDefaultConfigIfMissing() {
        try {
            File file = McaiPaths.weatherConfigFile();
            if (file.isFile()) {
                return;
            }
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory()) {
                parent.mkdirs();
            }
            Files.write(file.toPath(),
                    WeatherSettings.DEFAULTS.defaultJson().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // 写不出来不影响默认配置生效
        }
    }

    // ── 指令注册 ──────────────────────────────────────────────────────

    public void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        // 原版 /weather 要求 OP（permission 2），普通玩家用不了；把原版节点拿掉换成我们自己的。
        try {
            dispatcher.getRoot().getChildren().removeIf(node ->
                    "weather".equals(node.getName()) || "vote".equals(node.getName()));
            McaiMod.LOGGER.info("[mcai] 已替换原版 /weather（原版需要 OP，现在普通玩家也能发起天气投票）");
        } catch (Throwable broken) {
            McaiMod.LOGGER.warn("[mcai] 替换原版 /weather 失败（普通玩家可能仍受原版权限限制）：{}",
                    broken.toString());
        }

        dispatcher.register(Commands.literal("weather")
                .executes(context -> {
                    service.weatherUsage(actorOf(context.getSource()));
                    return 0;
                })
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
                .executes(context -> {
                    service.voteUsage(actorOf(context.getSource()));
                    return 0;
                })
                .then(Commands.literal("yes").executes(context -> vote(context, true)))
                .then(Commands.literal("no").executes(context -> vote(context, false)))
                .then(Commands.literal("status").executes(context -> {
                    service.status(actorOf(context.getSource()));
                    return 1;
                })));

        McaiMod.LOGGER.info("[mcai] 已注册 /weather 与 /vote（游戏内天气投票）");
    }

    private int request(CommandContext<CommandSourceStack> context, String type, Integer durationSeconds) {
        return service.weather(actorOf(context.getSource()), type, durationSeconds);
    }

    private int vote(CommandContext<CommandSourceStack> context, boolean agree) {
        return service.vote(actorOf(context.getSource()), agree);
    }

    private static Actor actorOf(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return Actor.console();
        }
        return Actor.player(player.getUUID(), player.getGameProfile().getName(), player.hasPermissions(2));
    }

    // ── WeatherVoteService.Host ───────────────────────────────────────

    @Override
    public int onlineCount() {
        MinecraftServer current = server;
        return current == null ? 0 : current.getPlayerList().getPlayerCount();
    }

    @Override
    public void send(Actor actor, String message) {
        if (message == null) {
            return;
        }
        String line = "[天气] " + message;
        if (actor == null || actor.console) {
            McaiMod.LOGGER.info("[天气] {}", message);
            return;
        }
        ServerPlayer player = playerOf(actor.id);
        if (player != null) {
            player.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public void broadcast(String message) {
        ChatFeedback.broadcast(server, message);
    }

    @Override
    public void applyWeather(String type, Integer durationSeconds) {
        MinecraftServer current = server;
        if (current == null) {
            return;
        }
        try {
            ServerLevel level = current.overworld();
            int ticks = durationSeconds == null
                    ? WeatherVoteService.defaultDurationTicks()
                    : durationSeconds * 20;
            if ("clear".equals(type)) {
                level.setWeatherParameters(ticks, 0, false, false);
            } else if ("rain".equals(type)) {
                level.setWeatherParameters(0, ticks, true, false);
            } else if ("thunder".equals(type)) {
                level.setWeatherParameters(0, ticks, true, true);
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
        return McaiPaths.weatherConfigFile();
    }

    private ServerPlayer playerOf(UUID id) {
        MinecraftServer current = server;
        if (current == null || id == null) {
            return null;
        }
        return current.getPlayerList().getPlayer(id);
    }
}
