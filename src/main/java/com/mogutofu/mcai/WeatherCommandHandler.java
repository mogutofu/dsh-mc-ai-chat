package com.mogutofu.mcai;

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
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 玩家天气指令 + 游戏内投票。
 *
 * <pre>
 * /weather clear|rain|thunder [持续秒数]   普通玩家可用（会替换原版需要 OP 的 /weather）
 * /vote yes|no|status                     天气投票
 * </pre>
 *
 * <p>规则（配置在 config/mcai-weather.json，由 DSH 插件设置面板写入）：
 * 在线玩家 ≤ directThreshold（默认 2）时直接生效；否则发起投票，
 * 发起人自动算 1 票同意，同意票达到 approveNeed（默认 2）通过，
 * 反对票达到 rejectNeed（默认 1）作废，超时 timeoutSeconds（默认 60 秒）作废。
 * 每人发起有 cooldownSeconds（默认 60 秒）冷却。</p>
 */
@Mod.EventBusSubscriber(modid = McaiMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WeatherCommandHandler {

    private static final String PREFIX = "[天气] ";
    /** 同一条 /weather 的无持续秒数时，持续多少 tick（6000 tick = 5 分钟）。 */
    private static final int DEFAULT_DURATION_TICKS = 6000;
    private static final Map<UUID, Long> LAST_REQUEST_AT = new HashMap<>();
    private static PendingVote pending = null;
    private static int tickCounter = 0;

    private WeatherCommandHandler() {
    }

    // ── 指令注册 ──────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        // 原版 /weather 要求 OP（permission 2），普通玩家用不了；把原版节点拿掉换成我们自己的。
        try {
            dispatcher.getRoot().getChildren().removeIf(node -> "weather".equals(node.getName()) || "vote".equals(node.getName()));
            McaiMod.LOGGER.info("[mcai] 已替换原版 /weather（原版需要 OP，现在普通玩家也能发起天气投票）");
        } catch (Throwable broken) {
            McaiMod.LOGGER.warn("[mcai] 替换原版 /weather 失败（普通玩家可能仍受原版权限限制）：{}", broken.toString());
        }

        dispatcher.register(Commands.literal("weather")
                .executes(WeatherCommandHandler::weatherUsage)
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
                .executes(WeatherCommandHandler::voteUsage)
                .then(Commands.literal("yes").executes(WeatherCommandHandler::voteYes))
                .then(Commands.literal("no").executes(WeatherCommandHandler::voteNo))
                .then(Commands.literal("status").executes(WeatherCommandHandler::voteStatus)));

        McaiMod.LOGGER.info("[mcai] 已注册 /weather 与 /vote（游戏内天气投票）");
    }

    // ── /weather ──────────────────────────────────────────────────────

    private static int weatherUsage(CommandContext<CommandSourceStack> context) {
        WeatherConfig.Settings cfg = WeatherConfig.load();
        send(context.getSource(), "用法：/weather clear|rain|thunder [持续秒数]", ChatFormatting.GRAY);
        send(context.getSource(), "在线 ≤ " + cfg.directThreshold() + " 人：直接生效；更多人在线：需要 "
                + cfg.approveNeed() + " 人用 /vote yes 同意（/vote no 反对，/vote status 查看）", ChatFormatting.GRAY);
        return 0;
    }

    private static int request(CommandContext<CommandSourceStack> context, String type, Integer durationSeconds) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        if (server == null) {
            return 0;
        }
        WeatherConfig.Settings cfg = WeatherConfig.load();
        if (!cfg.enabled()) {
            send(source, "天气功能已被服主关闭", ChatFormatting.RED);
            return 0;
        }
        ServerPlayer player = source.getPlayer();
        long now = System.currentTimeMillis();
        if (player != null && cfg.cooldownSeconds() > 0) {
            Long last = LAST_REQUEST_AT.get(player.getUUID());
            if (last != null) {
                long waitMs = cfg.cooldownSeconds() * 1000L;
                if (now - last < waitMs) {
                    long remain = (waitMs - (now - last) + 999) / 1000;
                    ChatFeedback.notice(player, "天气改太快了，再等 " + remain + " 秒", ChatFormatting.GRAY);
                    return 0;
                }
            }
        }
        PendingVote active = pending;
        if (active != null) {
            if (!active.expired(now)) {
                send(source, "已经有一个天气投票在进行中（/vote status 查看）", ChatFormatting.YELLOW);
                return 0;
            }
            pending = null;
        }

        String typeName = chineseType(type);
        String who = player != null ? player.getGameProfile().getName() : "服务器控制台";
        int online = server.getPlayerList().getPlayerCount();

        if (player == null || online <= cfg.directThreshold()) {
            applyWeather(server, type, durationSeconds);
            ChatFeedback.broadcast(server, who + " 把天气改成了" + typeName + durationText(durationSeconds), ChatFormatting.GREEN);
            McaiMod.LOGGER.info("[mcai] 天气改为 {}（{}，direct，在线 {} 人）", type, who, online);
            if (player != null) {
                LAST_REQUEST_AT.put(player.getUUID(), now);
            }
            return 1;
        }

        pending = new PendingVote(player.getUUID(), who, type, durationSeconds, cfg, now);
        ChatFeedback.broadcast(server, "玩家 " + who + " 想把天气改成" + typeName + "（在线 " + online + " 人，需要 "
                + cfg.approveNeed() + " 人同意）", ChatFormatting.YELLOW);
        ChatFeedback.broadcast(server, "投票：/vote yes 同意 · /vote no 反对 · /vote status 查看（"
                + cfg.timeoutSeconds() + " 秒内有效）", ChatFormatting.GRAY);
        LAST_REQUEST_AT.put(player.getUUID(), now);
        McaiMod.LOGGER.info("[mcai] 天气投票发起：{} -> {}（在线 {} 人，需 {} 票）", who, type, online, cfg.approveNeed());
        return 1;
    }

    // ── /vote ─────────────────────────────────────────────────────────

    private static int voteUsage(CommandContext<CommandSourceStack> context) {
        send(context.getSource(), "用法：/vote yes 同意 · /vote no 反对 · /vote status 查看当前投票", ChatFormatting.GRAY);
        return 0;
    }

    private static int voteYes(CommandContext<CommandSourceStack> context) {
        return castVote(context, true);
    }

    private static int voteNo(CommandContext<CommandSourceStack> context) {
        return castVote(context, false);
    }

    private static int castVote(CommandContext<CommandSourceStack> context, boolean agree) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            send(source, "只有游戏内玩家能投票", ChatFormatting.GRAY);
            return 0;
        }
        if (server == null) {
            return 0;
        }
        PendingVote vote = pending;
        long now = System.currentTimeMillis();
        if (vote == null) {
            ChatFeedback.notice(player, "现在没有正在进行的天气投票", ChatFormatting.GRAY);
            return 0;
        }
        if (vote.expired(now)) {
            pending = null;
            ChatFeedback.broadcast(server, "天气投票已超时作废", ChatFormatting.GRAY);
            ChatFeedback.notice(player, "现在没有正在进行的天气投票", ChatFormatting.GRAY);
            return 0;
        }
        UUID id = player.getUUID();
        if (vote.yes.contains(id) || vote.no.contains(id)) {
            ChatFeedback.notice(player, "你已经投过票了", ChatFormatting.GRAY);
            return 0;
        }
        String name = player.getGameProfile().getName();
        if (agree) {
            vote.yes.add(id);
            ChatFeedback.broadcast(server, name + " 同意（" + vote.yes.size() + "/" + vote.approveNeed() + "）", ChatFormatting.GREEN);
        } else {
            vote.no.add(id);
            ChatFeedback.broadcast(server, name + " 反对（" + vote.no.size() + "/" + vote.rejectNeed() + " 就作废）", ChatFormatting.RED);
        }
        decide(server, vote);
        return 1;
    }

    private static int voteStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        PendingVote vote = pending;
        long now = System.currentTimeMillis();
        if (vote == null || vote.expired(now)) {
            if (vote != null) {
                pending = null;
            }
            send(source, "现在没有正在进行的天气投票", ChatFormatting.GRAY);
            return 0;
        }
        send(source, "发起人 " + vote.initiatorName + " 想把天气改成" + chineseType(vote.type)
                + "：同意 " + vote.yes.size() + "/" + vote.approveNeed()
                + "，反对 " + vote.no.size() + "/" + vote.rejectNeed()
                + "，还剩 " + vote.remainingSeconds(now) + " 秒", ChatFormatting.YELLOW);
        send(source, "投票：/vote yes 同意 · /vote no 反对", ChatFormatting.GRAY);
        return 1;
    }

    // ── 投票判定 ──────────────────────────────────────────────────────

    private static void decide(MinecraftServer server, PendingVote vote) {
        if (pending != vote) {
            return;
        }
        if (vote.yes.size() >= vote.approveNeed()) {
            pending = null;
            applyWeather(server, vote.type, vote.durationSeconds);
            ChatFeedback.broadcast(server, "投票通过（" + vote.yes.size() + "/" + vote.approveNeed() + " 同意）：天气改为"
                    + chineseType(vote.type) + durationText(vote.durationSeconds), ChatFormatting.GREEN);
            McaiMod.LOGGER.info("[mcai] 天气投票通过：{} -> {}", vote.initiatorName, vote.type);
            return;
        }
        if (vote.no.size() >= vote.rejectNeed()) {
            pending = null;
            ChatFeedback.broadcast(server, "天气投票被否决（" + vote.no.size() + " 人反对），天气不变", ChatFormatting.RED);
            McaiMod.LOGGER.info("[mcai] 天气投票被否决：{} -> {}", vote.initiatorName, vote.type);
            return;
        }
        int online = server.getPlayerList().getPlayerCount();
        int voted = vote.yes.size() + vote.no.size();
        if (online > 0 && voted >= online) {
            pending = null;
            ChatFeedback.broadcast(server, "天气投票结束：同意 " + vote.yes.size() + "/" + vote.approveNeed()
                    + "，没有达到门槛，天气不变", ChatFormatting.GRAY);
            McaiMod.LOGGER.info("[mcai] 天气投票未达门槛：{} -> {}", vote.initiatorName, vote.type);
        }
    }

    // ── 服务端事件 ────────────────────────────────────────────────────

    /** 每 tick 由 {@link McaiServerEvents} 调用，检查投票超时。 */
    public static void onServerTick() {
        tickCounter++;
        if (tickCounter % 20 != 0) {
            return;
        }
        PendingVote vote = pending;
        if (vote == null || !vote.expired(System.currentTimeMillis())) {
            return;
        }
        pending = null;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ChatFeedback.broadcast(server, "天气投票超时，未执行", ChatFormatting.GRAY);
        }
        McaiMod.LOGGER.info("[mcai] 天气投票超时：{} -> {}", vote.initiatorName, vote.type);
    }

    /** 玩家登出：清冷却；如果在线人数掉到 0 就作废投票，否则按已投票人数判定一次。 */
    public static void onPlayerLogout(UUID playerId) {
        LAST_REQUEST_AT.remove(playerId);
        PendingVote vote = pending;
        if (vote == null) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            pending = null;
            return;
        }
        if (server.getPlayerList().getPlayerCount() == 0) {
            pending = null;
            return;
        }
        decide(server, vote);
    }

    public static void onServerStopping() {
        pending = null;
        LAST_REQUEST_AT.clear();
        tickCounter = 0;
    }

    // ── 天气执行 ──────────────────────────────────────────────────────

    private static void applyWeather(MinecraftServer server, String type, Integer durationSeconds) {
        try {
            ServerLevel level = server.overworld();
            int ticks = durationSeconds == null ? DEFAULT_DURATION_TICKS : durationSeconds * 20;
            switch (type) {
                case "clear" -> level.setWeatherParameters(ticks, 0, false, false);
                case "rain" -> level.setWeatherParameters(0, ticks, true, false);
                case "thunder" -> level.setWeatherParameters(0, ticks, true, true);
                default -> {
                    return;
                }
            }
        } catch (Throwable broken) {
            McaiMod.LOGGER.error("[mcai] 设置天气失败：{}", broken.toString());
        }
    }

    // ── 小工具 ────────────────────────────────────────────────────────

    private static String chineseType(String type) {
        return switch (type) {
            case "clear" -> "晴天";
            case "rain" -> "下雨";
            case "thunder" -> "雷雨";
            default -> type;
        };
    }

    private static String durationText(Integer durationSeconds) {
        return durationSeconds == null ? "" : "（持续 " + durationSeconds + " 秒）";
    }

    private static void send(CommandSourceStack source, String text, ChatFormatting color) {
        if (source == null || text == null) {
            return;
        }
        source.sendSuccess(() -> Component.literal(PREFIX + text).withStyle(color), false);
    }

    /** 一次进行中的天气投票。发起人自动算 1 票同意。 */
    private static final class PendingVote {
        final UUID initiator;
        final String initiatorName;
        final String type;
        final Integer durationSeconds;
        final int approveNeed;
        final int rejectNeed;
        final long deadlineMs;
        final Set<UUID> yes = new HashSet<>();
        final Set<UUID> no = new HashSet<>();

        PendingVote(UUID initiator, String initiatorName, String type, Integer durationSeconds,
                    WeatherConfig.Settings cfg, long now) {
            this.initiator = initiator;
            this.initiatorName = initiatorName;
            this.type = type;
            this.durationSeconds = durationSeconds;
            this.approveNeed = cfg.approveNeed();
            this.rejectNeed = cfg.rejectNeed();
            this.deadlineMs = now + cfg.timeoutSeconds() * 1000L;
            this.yes.add(initiator);
        }

        boolean expired(long now) {
            return now > deadlineMs;
        }

        long remainingSeconds(long now) {
            return Math.max(0, (deadlineMs - now + 999) / 1000);
        }

        int approveNeed() {
            return approveNeed;
        }

        int rejectNeed() {
            return rejectNeed;
        }
    }
}
