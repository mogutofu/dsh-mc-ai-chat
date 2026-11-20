package com.mogutofu.mcai.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 每玩家 /ai 限流（与 Forge 1.2.0 的 RateLimiter 语义一致）。 */
public final class Cooldown {

    private final Map<UUID, Long> last = new HashMap<>();

    /** 距离上次成功请求够不够久；够久就记一笔并返回 true。 */
    public boolean tryAcquire(UUID playerId, int seconds) {
        if (seconds <= 0 || playerId == null) {
            return true;
        }
        long now = System.currentTimeMillis();
        Long previous = last.get(playerId);
        if (previous != null && now - previous < seconds * 1000L) {
            return false;
        }
        last.put(playerId, now);
        return true;
    }

    public void clear() {
        last.clear();
    }

    public void remove(UUID playerId) {
        last.remove(playerId);
    }
}
