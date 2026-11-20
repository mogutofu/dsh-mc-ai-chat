package com.mogutofu.mcai.fabric.net;

import net.minecraft.resources.Identifier;

/**
 * Fabric 侧的附件协议常量。
 *
 * <p>与 Forge 1.2.0 的语义完全一致，只是 Fabric 每个 payload 有自己的 ID
 * （Forge 是单通道 {@code mcai:net} + 消息索引；Fabric 是 5 个 {@code mcai:xxx} payload）。
 * 客户端模组 {@code mcai_client} 有一份等价常量，改动要两边同步。</p>
 */
public final class Protocol {

    /** 命名空间，与 Forge 的通道 {@code mcai:net} 保持同一个 namespace。 */
    public static final String NAMESPACE = "mcai";

    /** 协议版本号（ClientHello 里带）。 */
    public static final int PROTOCOL_VERSION = 1;

    /** 单块原始字节上限（24KB，与 1.2.0 一致）。 */
    public static final int CHUNK_SIZE = 24 * 1024;

    /** 截图附件上限 8MB。 */
    public static final int MAX_SCREENSHOT_BYTES = 8 * 1024 * 1024;

    /** 文件附件上限 16MB。 */
    public static final int MAX_FILE_BYTES = 16 * 1024 * 1024;

    /** 分块数量上限：16MB / 24KB 再加一块余量。 */
    public static final int MAX_CHUNKS = (MAX_FILE_BYTES + CHUNK_SIZE - 1) / CHUNK_SIZE + 1;

    /** 服务端等待客户端附件的最长时间（15 秒）。 */
    public static final long ATTACHMENT_TIMEOUT_MS = 15_000L;

    /** 带附件的 HTTP 请求最短超时时间。 */
    public static final int MIN_HTTP_TIMEOUT_SECONDS = 90;

    /** AttachmentRequest.kind 取值。 */
    public static final byte KIND_SCREENSHOT = 0;
    public static final byte KIND_FILE = 1;

    private Protocol() {
    }

    /** {@code mcai:<path>}，与 Forge 通道名同一个命名空间。 */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NAMESPACE, path);
    }

    public static String megabytes(int bytes) {
        return (bytes / (1024 * 1024)) + "MB";
    }
}
