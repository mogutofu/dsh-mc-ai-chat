package com.mogutofu.mcai;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每个玩家一份冷却时间。
 *
 * <p>用 {@link ConcurrentHashMap#compute} 做原子判断，防止连着按回车时两个请求
 * 同时通过检查（那正好就是刷屏的写法）。</p>
 */
final class RateLimiter {

    /** 玩家离线后这条记录也没什么用了，半小时以上的顺手清掉。 */
    private static final long STALE_MS = 30 * 60 * 1000L;

    private static final Map<UUID, Long> LAST_ASK = new ConcurrentHashMap<>();

    private RateLimiter() {
    }

    /**
     * @return true 表示这次放行（并且已经记下时间），false 表示还在冷却里
     */
    static boolean tryAcquire(UUID playerId, int cooldownSeconds) {
        long now = System.currentTimeMillis();
        prune(now);

        if (cooldownSeconds <= 0) {
            LAST_ASK.put(playerId, now);
            return true;
        }

        long window = cooldownSeconds * 1000L;
        boolean[] allowed = {false};
        LAST_ASK.compute(playerId, (id, lastAsk) -> {
            if (lastAsk != null && now - lastAsk < window) {
                return lastAsk;
            }
            allowed[0] = true;
            return now;
        });
        return allowed[0];
    }

    private static void prune(long now) {
        if (LAST_ASK.size() < 128) {
            return;
        }
        LAST_ASK.entrySet().removeIf(entry -> now - entry.getValue() > STALE_MS);
    }
}
