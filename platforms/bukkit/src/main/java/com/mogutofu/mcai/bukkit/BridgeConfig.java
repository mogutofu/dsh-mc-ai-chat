package com.mogutofu.mcai.bukkit;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * {@code plugins/mc-ai/config.yml} 里的附件桥接配置。
 *
 * <p>只放桥接相关字段；{@code config/mcai-common.json}、{@code config/mcai-weather.json}
 * 保持原样不动（那两份是跨平台共用的）。</p>
 */
public final class BridgeConfig {

    /** 是否启用 Bukkit 附件桥接。 */
    public final boolean enabled;
    /** 传输方式；目前只支持 {@code http}。 */
    public final String transport;
    /** HTTP 上传端口（也是 INFO 下发给客户端的端口）。 */
    public final int port;
    /** 监听地址；{@code 0.0.0.0} 表示所有网卡。 */
    public final String bind;
    /** 下发给客户端的地址；空串表示客户端用自己连接服务器的地址。 */
    public final String advertiseHost;
    /** 下发给客户端的端口；0 表示用 {@link #port}。 */
    public final int advertisePort;
    /** token 有效期（秒）。 */
    public final int tokenTtlSeconds;
    /** 单次上传读取超时（秒）。 */
    public final int uploadTimeoutSeconds;
    /** 截图上限（字节）。 */
    public final int maxImageBytes;
    /** 文件上限（字节）。 */
    public final int maxFileBytes;
    /** 同时进行的上传数上限。 */
    public final int maxConcurrent;

    public static final BridgeConfig DISABLED =
            new BridgeConfig(false, "http", 25566, "0.0.0.0", "", 0, 120, 30,
                    8 * 1024 * 1024, 16 * 1024 * 1024, 2);

    public BridgeConfig(boolean enabled, String transport, int port, String bind, String advertiseHost,
                        int advertisePort, int tokenTtlSeconds, int uploadTimeoutSeconds,
                        int maxImageBytes, int maxFileBytes, int maxConcurrent) {
        this.enabled = enabled;
        this.transport = transport == null || transport.trim().isEmpty() ? "http" : transport.trim();
        this.port = clamp(port, 1, 65535, 25566);
        this.bind = bind == null || bind.trim().isEmpty() ? "0.0.0.0" : bind.trim();
        this.advertiseHost = advertiseHost == null ? "" : advertiseHost.trim();
        this.advertisePort = clamp(advertisePort, 0, 65535, 0);
        this.tokenTtlSeconds = clamp(tokenTtlSeconds, 10, 3600, 120);
        this.uploadTimeoutSeconds = clamp(uploadTimeoutSeconds, 5, 300, 30);
        this.maxImageBytes = clamp(maxImageBytes, 16 * 1024, 64 * 1024 * 1024, 8 * 1024 * 1024);
        this.maxFileBytes = clamp(maxFileBytes, 16 * 1024, 64 * 1024 * 1024, 16 * 1024 * 1024);
        this.maxConcurrent = clamp(maxConcurrent, 1, 16, 2);
    }

    public static BridgeConfig load(FileConfiguration config) {
        if (config == null) {
            return DISABLED;
        }
        return new BridgeConfig(
                config.getBoolean("attachment.enabled", true),
                config.getString("attachment.transport", "http"),
                config.getInt("attachment.port", 25566),
                config.getString("attachment.bind", "0.0.0.0"),
                config.getString("attachment.advertise-host", ""),
                config.getInt("attachment.advertise-port", 0),
                config.getInt("attachment.token-ttl-seconds", 120),
                config.getInt("attachment.upload-timeout-seconds", 30),
                megabytesToBytes(config.getInt("attachment.max-image-mb", 8), 8),
                megabytesToBytes(config.getInt("attachment.max-file-mb", 16), 16),
                config.getInt("attachment.max-concurrent", 2));
    }

    /** 实际下发给客户端的端口。 */
    public int effectiveAdvertisePort() {
        return advertisePort > 0 ? advertisePort : port;
    }

    public boolean httpTransport() {
        return "http".equalsIgnoreCase(transport);
    }

    private static int megabytesToBytes(int megabytes, int fallbackMb) {
        int mb = clamp(megabytes, 1, 64, fallbackMb);
        return mb * 1024 * 1024;
    }

    private static int clamp(int value, int min, int max, int fallback) {
        if (value < min || value > max) {
            return fallback;
        }
        return value;
    }
}
