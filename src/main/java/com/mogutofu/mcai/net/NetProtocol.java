package com.mogutofu.mcai.net;

import net.minecraft.resources.ResourceLocation;

/**
 * 网络协议常量。
 *
 * <p>服务端模组（mcai）与客户端模组（mcai_client）各自持有一份完全相同的拷贝：
 * 通道名、协议版本、消息索引、分块大小、附件上限必须两侧一致。改这里就要同步改
 * {@code mcai-build/client/src/main/java/com/mogutofu/mcai/client/net/NetProtocol.java}。</p>
 */
public final class NetProtocol {

    /** 通道名 mcai:net。 */
    public static final ResourceLocation CHANNEL = new ResourceLocation("mcai", "net");

    /** 协议版本字符串（SimpleChannel 的版本协商用）。 */
    public static final String VERSION = "1";

    /** 协议版本号（ClientHello 里带）。 */
    public static final int PROTOCOL_VERSION = 1;

    /** 单块原始字节上限。1.20.1 自定义载荷单包上限 32767，24KB + header 还能留出足够余量。 */
    public static final int CHUNK_SIZE = 24 * 1024;

    /** 1.20.1 ServerboundCustomPayloadPacket 的单包上限。 */
    public static final int MAX_PACKET_BYTES = 32767;

    /** 截图附件上限 8MB。 */
    public static final int MAX_SCREENSHOT_BYTES = 8 * 1024 * 1024;

    /** 文件附件上限 16MB。 */
    public static final int MAX_FILE_BYTES = 16 * 1024 * 1024;

    /** 分块数量上限：按 16MB / 24KB 再加一块余量。 */
    public static final int MAX_CHUNKS = (MAX_FILE_BYTES + CHUNK_SIZE - 1) / CHUNK_SIZE + 1;

    /** 服务端等待客户端附件的最长时间。 */
    public static final long ATTACHMENT_TIMEOUT_MS = 15_000L;

    /** 带附件的 HTTP 请求最短超时时间。 */
    public static final int MIN_HTTP_TIMEOUT_SECONDS = 90;

    /** AttachmentRequest.kind 取值。 */
    public static final byte KIND_SCREENSHOT = 0;
    public static final byte KIND_FILE = 1;

    private NetProtocol() {
    }

    public static String megabytes(int bytes) {
        return (bytes / (1024 * 1024)) + "MB";
    }
}
