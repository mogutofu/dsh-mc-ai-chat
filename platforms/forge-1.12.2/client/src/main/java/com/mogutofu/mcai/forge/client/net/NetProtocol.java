package com.mogutofu.mcai.forge.client.net;

/**
 * 网络协议常量（客户端副本）。
 *
 * <p>必须与服务端 {@code com.mogutofu.mcai.forge.net.NetProtocol} 完全一致：
 * 通道名、协议版本、消息索引、分块大小、附件上限。改这里就要同步改服务端那份。</p>
 */
public final class NetProtocol {

    /** 通道名 mcai:net。 */
    public static final String CHANNEL = "mcai:net";

    /** 协议版本字符串（日志用；1.12.2 的 SimpleImpl 不做自动版本协商）。 */
    public static final String VERSION = "1";

    /** 协议版本号（ClientHello 里带，服务端比对）。 */
    public static final int PROTOCOL_VERSION = 1;

    /** 单块原始字节上限：24KB。 */
    public static final int CHUNK_SIZE = 24 * 1024;

    /** 1.12.2 单个自定义载荷包的安全上限。 */
    public static final int MAX_PACKET_BYTES = 32767;

    /** 截图附件上限 8MB。 */
    public static final int MAX_SCREENSHOT_BYTES = 8 * 1024 * 1024;

    /** 文件附件上限 16MB。 */
    public static final int MAX_FILE_BYTES = 16 * 1024 * 1024;

    /** 服务端等待客户端附件的最长时间（客户端只用于日志）。 */
    public static final long ATTACHMENT_TIMEOUT_MS = 15_000L;

    /** AttachmentRequest.kind 取值。 */
    public static final byte KIND_SCREENSHOT = 0;
    public static final byte KIND_FILE = 1;

    private NetProtocol() {
    }

    public static String megabytes(int bytes) {
        return (bytes / (1024 * 1024)) + "MB";
    }
}
