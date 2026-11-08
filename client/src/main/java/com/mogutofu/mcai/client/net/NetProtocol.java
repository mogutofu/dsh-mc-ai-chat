package com.mogutofu.mcai.client.net;

import net.minecraft.resources.ResourceLocation;

/**
 * 网络协议常量（mcai-client 侧拷贝）。
 *
 * <p>必须与服务端 {@code com.mogutofu.mcai.net.NetProtocol} 完全一致：
 * 通道名 mcai:net、协议版本 "1"、5 个消息索引、分块大小、附件上限。</p>
 */
public final class NetProtocol {

    public static final ResourceLocation CHANNEL = new ResourceLocation("mcai", "net");
    public static final String VERSION = "1";
    public static final int PROTOCOL_VERSION = 1;
    public static final int CHUNK_SIZE = 24 * 1024;
    public static final int MAX_PACKET_BYTES = 32767;
    public static final int MAX_SCREENSHOT_BYTES = 8 * 1024 * 1024;
    public static final int MAX_FILE_BYTES = 16 * 1024 * 1024;
    public static final int MAX_CHUNKS = (MAX_FILE_BYTES + CHUNK_SIZE - 1) / CHUNK_SIZE + 1;
    public static final long ATTACHMENT_TIMEOUT_MS = 15_000L;
    public static final int MIN_HTTP_TIMEOUT_SECONDS = 90;
    public static final byte KIND_SCREENSHOT = 0;
    public static final byte KIND_FILE = 1;

    private NetProtocol() {
    }

    public static String megabytes(int bytes) {
        return (bytes / (1024 * 1024)) + "MB";
    }
}
