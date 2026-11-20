package com.mogutofu.mcai.bukkit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 一次「等附件」的会话：玩家执行 {@code /ai screenshot|file} 时创建，HTTP 收到字节、
 * 客户端中止（ABORT）或 15 秒超时后移除。
 *
 * <p>每玩家同一时刻只有一个 pending（避免玩家连点把上传配错）。HTTP 线程读、
 * 主线程提交/清理，方法都加了 synchronized。</p>
 */
public final class PendingUploads {

    /** 等附件超时（毫秒）；和模组版 15 秒一致。 */
    static final long TIMEOUT_MS = 15_000L;

    /** 一条等待中的附件会话。 */
    static final class Pending {
        final UUID playerId;
        final String playerName;
        final UUID transferId;
        final String text;
        final boolean image;
        final long createdAt;

        Pending(UUID playerId, String playerName, UUID transferId, String text, boolean image, long createdAt) {
            this.playerId = playerId;
            this.playerName = playerName == null ? "" : playerName;
            this.transferId = transferId;
            this.text = text == null ? "" : text;
            this.image = image;
            this.createdAt = createdAt;
        }
    }

    private final Map<UUID, Pending> byPlayer = new HashMap<UUID, Pending>();
    private final Map<UUID, Pending> byTransfer = new HashMap<UUID, Pending>();

    /** 登记一条新会话，顺手把该玩家上一条顶掉。 */
    synchronized Pending add(UUID playerId, String playerName, UUID transferId, String text,
                             boolean image, long now) {
        Pending old = byPlayer.remove(playerId);
        if (old != null) {
            byTransfer.remove(old.transferId);
        }
        Pending pending = new Pending(playerId, playerName, transferId, text, image, now);
        byPlayer.put(playerId, pending);
        byTransfer.put(transferId, pending);
        return pending;
    }

    synchronized Pending byTransfer(UUID transferId) {
        return transferId == null ? null : byTransfer.get(transferId);
    }

    /** 原子地取走一条会话（HTTP 完成 / ABORT / 超时时用，保证只处理一次）。 */
    synchronized Pending take(UUID transferId) {
        if (transferId == null) {
            return null;
        }
        Pending pending = byTransfer.remove(transferId);
        if (pending != null) {
            byPlayer.remove(pending.playerId);
        }
        return pending;
    }

    synchronized Pending removeByPlayer(UUID playerId) {
        Pending pending = byPlayer.remove(playerId);
        if (pending != null) {
            byTransfer.remove(pending.transferId);
        }
        return pending;
    }

    /** 取出所有超时会话（并移除）。 */
    synchronized List<Pending> expired(long now) {
        List<Pending> out = new ArrayList<Pending>();
        Iterator<Map.Entry<UUID, Pending>> it = byPlayer.entrySet().iterator();
        while (it.hasNext()) {
            Pending pending = it.next().getValue();
            if (now - pending.createdAt >= TIMEOUT_MS) {
                it.remove();
                byTransfer.remove(pending.transferId);
                out.add(pending);
            }
        }
        return out;
    }

    synchronized void clear() {
        byPlayer.clear();
        byTransfer.clear();
    }

    synchronized int size() {
        return byPlayer.size();
    }
}
