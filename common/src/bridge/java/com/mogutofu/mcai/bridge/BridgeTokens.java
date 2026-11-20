package com.mogutofu.mcai.bridge;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一次性上传令牌：{@code BridgeUploadServer} 发、HTTP handler 核销。
 *
 * <p>令牌用 {@link SecureRandom} 生成，绑定<b>玩家 UUID + transferId</b>，用一次就作废；
 * 超过 TTL 也作废（{@link #consume(String, long)} 会顺手清掉过期项）。纯逻辑、无 Minecraft
 * 依赖，可以直接单测。</p>
 */
public final class BridgeTokens {

    /** 令牌核销成功后拿到的绑定信息。 */
    public static final class Grant {
        public final UUID playerId;
        public final UUID transferId;

        public Grant(UUID playerId, UUID transferId) {
            this.playerId = playerId;
            this.transferId = transferId;
        }
    }

    private static final int TOKEN_BYTES = 24;

    private final Map<String, Entry> entries = new ConcurrentHashMap<String, Entry>();
    private final SecureRandom random = new SecureRandom();

    private static final class Entry {
        final Grant grant;
        final long expiresAtMillis;

        Entry(Grant grant, long expiresAtMillis) {
            this.grant = grant;
            this.expiresAtMillis = expiresAtMillis;
        }
    }

    /**
     * 发一个令牌。
     *
     * @param ttlSeconds ≤ 0 时按 120 秒算
     * @return URL-safe base64 令牌（32 字符）
     */
    public String issue(UUID playerId, UUID transferId, int ttlSeconds, long nowMillis) {
        byte[] raw = new byte[TOKEN_BYTES];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        int ttl = ttlSeconds <= 0 ? 120 : Math.min(ttlSeconds, 3600);
        entries.put(token, new Entry(new Grant(playerId, transferId), nowMillis + ttl * 1000L));
        return token;
    }

    /**
     * 核销令牌：不管成功失败，令牌都从表里删掉（一次性）。
     *
     * @return 绑定信息；令牌不存在 / 已过期 / 绑定不匹配时返回 {@code null}
     */
    public Grant consume(String token, UUID playerId, UUID transferId, long nowMillis) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        Entry entry = entries.remove(token);
        if (entry == null) {
            return null;
        }
        if (nowMillis > entry.expiresAtMillis) {
            return null;
        }
        if (entry.grant.playerId != null && playerId != null
                && !entry.grant.playerId.equals(playerId)) {
            return null;
        }
        if (entry.grant.transferId != null && transferId != null
                && !entry.grant.transferId.equals(transferId)) {
            return null;
        }
        return entry.grant;
    }

    /** 清掉过期令牌（定期调用，防止长期不用的条目堆积）。 */
    public void purge(long nowMillis) {
        Iterator<Map.Entry<String, Entry>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            if (nowMillis > it.next().getValue().expiresAtMillis) {
                it.remove();
            }
        }
    }

    /** 作废某个玩家所有未用的令牌（下线时调用）。 */
    public void revokePlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        entries.entrySet().removeIf(entry -> playerId.equals(entry.getValue().grant.playerId));
    }

    public int size() {
        return entries.size();
    }

    public void clear() {
        entries.clear();
    }
}
