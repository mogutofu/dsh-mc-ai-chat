package com.mogutofu.mcai.forge.net;

/**
 * 网络协议常量（1.12.2 副本）。
 *
 * <p>服务端模组（mcai）与客户端模组（mcai_client）各自持有一份完全相同的拷贝：
 * 通道名、协议版本、消息索引、分块大小、附件上限必须两侧一致。改这里就要同步改
 * {@code platforms/forge-1.12.2/client/src/main/java/com/mogutofu/mcai/forge/client/net/NetProtocol.java}。</p>
 *
 * <p>1.12.2 的 SimpleImpl 没有 1.13+ 的 {@code newSimpleChannel(name, version, ...)} 版本协商，
 * 通道名还是普通字符串；协议版本放在 {@link ClientHello#protocolVersion} 里由服务端校验。</p>
 */
public final class NetProtocol {

    /** 通道名 mcai:net。 */
    public static final String CHANNEL = "mcai:net";

    /** 协议版本字符串（日志用；1.12.2 的 SimpleImpl 不做自动版本协商）。 */
    public static final String VERSION = "1";

    /** 协议版本号（ClientHello 里带，服务端比对）。 */
    public static final int PROTOCOL_VERSION = 1;

    /** 单块原始字节上限：24KB，留足余量低于 1.12.2 自定义载荷/原版的单包上限。 */
    public static final int CHUNK_SIZE = 24 * 1024;

    /** 1.12.2 单个自定义载荷包的安全上限。 */
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
