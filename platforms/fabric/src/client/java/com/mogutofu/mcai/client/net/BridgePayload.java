package com.mogutofu.mcai.client.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * {@code mcai:bridge} 的 raw 帧载荷（Fabric 26.x）。
 *
 * <p>插件发来的是一个完整的 mcai-bridge/1 帧，这里只做原样搬运；客户端发 HELLO /
 * ABORT / DONE 也用它。</p>
 */
public record BridgePayload(byte[] data) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BridgePayload> TYPE =
            new CustomPacketPayload.Type<>(Protocol.id("bridge"));

    public static final StreamCodec<FriendlyByteBuf, BridgePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeBytes(payload.data()),
            buffer -> {
                byte[] bytes = new byte[buffer.readableBytes()];
                buffer.readBytes(bytes);
                return new BridgePayload(bytes);
            });

    public BridgePayload {
        data = data == null ? new byte[0] : data;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
