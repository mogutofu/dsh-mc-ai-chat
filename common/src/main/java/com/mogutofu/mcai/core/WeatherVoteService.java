package com.mogutofu.mcai.core;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 天气指令 + 游戏内投票的纯逻辑（所有平台共用，不依赖 Minecraft 类）。
 *
 * <pre>
 * /weather clear|rain|thunder [持续秒数]   普通玩家可用（替换原版需要 OP 的 /weather）
 * /vote yes|no|status                     天气投票
 * </pre>
 *
 * <p>规则：在线玩家 ≤ directThreshold（默认 2）时直接生效；否则发起投票，
 * 发起人自动算 1 票同意，同意票达到 approveNeed（默认 2）通过，
 * 反对票达到 rejectNeed（默认 1）作废，超时 timeoutSeconds（默认 60 秒）作废。
 * 每人发起有 cooldownSeconds（默认 60 秒）冷却。控制台 / OP 直接生效。</p>
 */
public final class WeatherVoteService {

    /** 平台适配层：发消息、执行天气、读在线人数与配置。 */
    public interface Host {
        int onlineCount();

        void send(Actor actor, String message);

        void broadcast(String message);

        void applyWeather(String type, Integer durationSeconds);

        void log(String message);

        File configFile();
    }

    private static final int DEFAULT_DURATION_TICKS = 6000;

    private final Host host;
    private final Map<UUID, Long> lastRequestAt = new HashMap<>();
    private PendingVote pending;
    private int tickCounter;

    public WeatherVoteService(Host host) {
        this.host = host;
    }

    // ── /weather ──────────────────────────────────────────────────────

    public int weather(Actor actor, String type, Integer durationSeconds) {
        WeatherSettings cfg = WeatherSettings.load(host.configFile());
        if (!cfg.enabled) {
            host.send(actor, "天气功能已被服主关闭");
            return 0;
        }
        long now = System.currentTimeMillis();
        if (!actor.console && cfg.cooldownSeconds > 0) {
            Long last = lastRequestAt.get(actor.id);
            if (last != null) {
                long waitMs = cfg.cooldownSeconds * 1000L;
                if (now - last < waitMs) {
                    long remain = (waitMs - (now - last) + 999) / 1000;
                    host.send(actor, "天气改太快了，再等 " + remain + " 秒");
                    return 0;
                }
            }
        }

        PendingVote active = pending;
        if (active != null) {
            if (!active.expired(now)) {
                host.send(actor, "已经有一个天气投票在进行中（/vote status 查看）");
                return 0;
            }
            pending = null;
        }

        String typeName = chineseType(type);
        int online = host.onlineCount();
        if (actor.console || actor.op || online <= cfg.directThreshold) {
            host.applyWeather(type, durationSeconds);
            host.broadcast(actor.displayName() + " 把天气改成了" + typeName + durationText(durationSeconds));
            host.log("天气改为 " + type + "（" + actor.displayName() + "，direct，在线 " + online + " 人）");
            if (!actor.console) {
                lastRequestAt.put(actor.id, now);
            }
            return 1;
        }

        pending = new PendingVote(actor.id, actor.displayName(), type, durationSeconds, cfg, now);
        host.broadcast("玩家 " + actor.displayName() + " 想把天气改成" + typeName + "（在线 " + online + " 人，需要 "
                + cfg.approveNeed + " 人同意）");
        host.broadcast("投票：/vote yes 同意 · /vote no 反对 · /vote status 查看（" + cfg.timeoutSeconds + " 秒内有效）");
        lastRequestAt.put(actor.id, now);
        host.log("天气投票发起：" + actor.displayName() + " -> " + type + "（在线 " + online + " 人，需 "
                + cfg.approveNeed + " 票）");
        return 1;
    }

    public int weatherUsage(Actor actor) {
        WeatherSettings cfg = WeatherSettings.load(host.configFile());
        host.send(actor, "用法：/weather clear|rain|thunder [持续秒数]");
        host.send(actor, cfg.describe());
        return 0;
    }

    // ── /vote ─────────────────────────────────────────────────────────

    public int voteUsage(Actor actor) {
        host.send(actor, "用法：/vote yes 同意 · /vote no 反对 · /vote status 查看当前投票");
        return 0;
    }

    public int vote(Actor actor, boolean agree) {
        if (actor.console) {
            host.send(actor, "只有游戏内玩家能投票");
            return 0;
        }
        long now = System.currentTimeMillis();
        PendingVote vote = pending;
        if (vote == null) {
            host.send(actor, "现在没有正在进行的天气投票");
            return 0;
        }
        if (vote.expired(now)) {
            pending = null;
            host.broadcast("天气投票已超时作废");
            host.send(actor, "现在没有正在进行的天气投票");
            return 0;
        }
        if (vote.yes.contains(actor.id) || vote.no.contains(actor.id)) {
            host.send(actor, "你已经投过票了");
            return 0;
        }
        if (agree) {
            vote.yes.add(actor.id);
            host.broadcast(actor.displayName() + " 同意（" + vote.yes.size() + "/" + vote.approveNeed + "）");
        } else {
            vote.no.add(actor.id);
            host.broadcast(actor.displayName() + " 反对（" + vote.no.size() + "/" + vote.rejectNeed + " 就作废）");
        }
        decide(vote);
        return 1;
    }

    public int status(Actor actor) {
        long now = System.currentTimeMillis();
        PendingVote vote = pending;
        if (vote == null || vote.expired(now)) {
            if (vote != null) {
                pending = null;
            }
            host.send(actor, "现在没有正在进行的天气投票");
            return 0;
        }
        host.send(actor, "发起人 " + vote.initiatorName + " 想把天气改成" + chineseType(vote.type)
                + "：同意 " + vote.yes.size() + "/" + vote.approveNeed
                + "，反对 " + vote.no.size() + "/" + vote.rejectNeed
                + "，还剩 " + vote.remainingSeconds(now) + " 秒");
        host.send(actor, "投票：/vote yes 同意 · /vote no 反对");
        return 1;
    }

    // ── 生命周期 ──────────────────────────────────────────────────────

    /** 平台每 tick 调一次；内部每 20 tick 检查一次超时。 */
    public void tick() {
        tickCounter++;
        if (tickCounter % 20 != 0) {
            return;
        }
        PendingVote vote = pending;
        if (vote == null || !vote.expired(System.currentTimeMillis())) {
            return;
        }
        pending = null;
        host.broadcast("天气投票超时，未执行");
        host.log("天气投票超时：" + vote.initiatorName + " -> " + vote.type);
    }

    /** 玩家登出：清冷却；在线人数掉到 0 就作废投票，否则按已投票人数判定一次。 */
    public void logout(UUID playerId) {
        lastRequestAt.remove(playerId);
        PendingVote vote = pending;
        if (vote == null) {
            return;
        }
        if (host.onlineCount() == 0) {
            pending = null;
            return;
        }
        decide(vote);
    }

    public void reset() {
        pending = null;
        lastRequestAt.clear();
        tickCounter = 0;
    }

    public static int defaultDurationTicks() {
        return DEFAULT_DURATION_TICKS;
    }

    // ── 内部 ──────────────────────────────────────────────────────────

    private void decide(PendingVote vote) {
        if (pending != vote) {
            return;
        }
        if (vote.yes.size() >= vote.approveNeed) {
            pending = null;
            host.applyWeather(vote.type, vote.durationSeconds);
            host.broadcast("投票通过（" + vote.yes.size() + "/" + vote.approveNeed + " 同意）：天气改为"
                    + chineseType(vote.type) + durationText(vote.durationSeconds));
            host.log("天气投票通过：" + vote.initiatorName + " -> " + vote.type);
            return;
        }
        if (vote.no.size() >= vote.rejectNeed) {
            pending = null;
            host.broadcast("天气投票被否决（" + vote.no.size() + " 人反对），天气不变");
            host.log("天气投票被否决：" + vote.initiatorName + " -> " + vote.type);
            return;
        }
        int online = host.onlineCount();
        int voted = vote.yes.size() + vote.no.size();
        if (online > 0 && voted >= online) {
            pending = null;
            host.broadcast("天气投票结束：同意 " + vote.yes.size() + "/" + vote.approveNeed
                    + "，没有达到门槛，天气不变");
            host.log("天气投票未达门槛：" + vote.initiatorName + " -> " + vote.type);
        }
    }

    public static String chineseType(String type) {
        if ("clear".equals(type)) {
            return "晴天";
        }
        if ("rain".equals(type)) {
            return "下雨";
        }
        if ("thunder".equals(type)) {
            return "雷雨";
        }
        return type;
    }

    public static String durationText(Integer durationSeconds) {
        return durationSeconds == null ? "" : "（持续 " + durationSeconds + " 秒）";
    }

    /** 一次进行中的天气投票。发起人自动算 1 票同意。 */
    private static final class PendingVote {
        final String initiatorName;
        final String type;
        final Integer durationSeconds;
        final int approveNeed;
        final int rejectNeed;
        final long deadlineMs;
        final Set<UUID> yes = new HashSet<>();
        final Set<UUID> no = new HashSet<>();

        PendingVote(UUID initiator, String initiatorName, String type, Integer durationSeconds,
                    WeatherSettings cfg, long now) {
            this.initiatorName = initiatorName;
            this.type = type;
            this.durationSeconds = durationSeconds;
            this.approveNeed = cfg.approveNeed;
            this.rejectNeed = cfg.rejectNeed;
            this.deadlineMs = now + cfg.timeoutSeconds * 1000L;
            this.yes.add(initiator);
        }

        boolean expired(long now) {
            return now > deadlineMs;
        }

        long remainingSeconds(long now) {
            return Math.max(0, (deadlineMs - now + 999) / 1000);
        }
    }
}
