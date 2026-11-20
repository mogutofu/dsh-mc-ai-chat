package com.mogutofu.mcai.forge;

import com.mogutofu.mcai.forge.net.NetProtocol;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 记录哪些玩家发过 {@code ClientHello}，也就是装了 mcai-client。
 *
 * <p>协议版本不一致的 hello 会被忽略（记一条 warn）；玩家登出时清理，服务端重启自然清空。</p>
 */
public final class ClientPresence {

    private static final Map<UUID, Integer> HELLO = new ConcurrentHashMap<>();

    private ClientPresence() {
    }

    public static void markHello(EntityPlayerMP player, int protocolVersion) {
        if (player == null) {
            return;
        }
        if (protocolVersion != NetProtocol.PROTOCOL_VERSION) {
            McaiMod.LOGGER.warn("[mcai] 玩家 {} 的 mcai-client 协议版本 {} 与服务端 {} 不一致，忽略",
                    player.getName(), protocolVersion, NetProtocol.PROTOCOL_VERSION);
            return;
        }
        UUID playerId = player.getUniqueID();
        Integer previous = HELLO.put(playerId, protocolVersion);
        if (previous == null) {
            McaiMod.LOGGER.info("[mcai] 玩家 {} 已安装 mcai-client（协议 {}），支持截图/文件附件",
                    player.getName(), protocolVersion);
        }
    }

    public static boolean hasClient(UUID playerId) {
        return playerId != null && HELLO.containsKey(playerId);
    }

    public static void remove(UUID playerId) {
        if (playerId != null) {
            HELLO.remove(playerId);
        }
    }

    public static void clear() {
        HELLO.clear();
    }
}
