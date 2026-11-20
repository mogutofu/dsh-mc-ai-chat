package com.mogutofu.mcai.client.net;

import net.minecraft.resources.Identifier;

/**
 * 客户端侧的附件协议常量。
 *
 * <p>必须与服务端模组 {@code com.mogutofu.mcai.fabric.net.Protocol} 完全一致。</p>
 */
public final class Protocol {

    public static final String NAMESPACE = "mcai";
    public static final int PROTOCOL_VERSION = 1;
    public static final int CHUNK_SIZE = 24 * 1024;
    public static final int MAX_SCREENSHOT_BYTES = 8 * 1024 * 1024;
    public static final int MAX_FILE_BYTES = 16 * 1024 * 1024;
    public static final int MAX_CHUNKS = (MAX_FILE_BYTES + CHUNK_SIZE - 1) / CHUNK_SIZE + 1;
    public static final long ATTACHMENT_TIMEOUT_MS = 15_000L;
    public static final int MIN_HTTP_TIMEOUT_SECONDS = 90;
    public static final byte KIND_SCREENSHOT = 0;
    public static final byte KIND_FILE = 1;

    private Protocol() {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NAMESPACE, path);
    }

    public static String megabytes(int bytes) {
        return (bytes / (1024 * 1024)) + "MB";
    }
}
