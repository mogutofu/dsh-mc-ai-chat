package com.mogutofu.mcai.client.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * {@code mcai:bridge} 的 raw 帧载荷（S→C / C→S 都用它，帧里自带类型）。
 *
 * <p>插件发来的字节就是一个完整的 mcai-bridge/1 帧；这里只做原样搬运。</p>
 */
public final class BridgePayload implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BridgePayload> TYPE =
            new CustomPacketPayload.Type<>(McaiIds.of("bridge"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BridgePayload> STREAM_CODEC =
            StreamCodec.of((buffer, payload) -> buffer.writeBytes(payload.data),
                    buffer -> {
                        byte[] bytes = new byte[buffer.readableBytes()];
                        buffer.readBytes(bytes);
                        return new BridgePayload(bytes);
                    });

    public final byte[] data;

    public BridgePayload(byte[] data) {
        this.data = data == null ? new byte[0] : data;
    }

    @Override
    public CustomPacketPayload.Type<BridgePayload> type() {
        return TYPE;
    }
}
