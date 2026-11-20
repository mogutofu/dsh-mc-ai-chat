package com.mogutofu.mcai.client.neoforge.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * {@code mcai:bridge} 的 raw 帧载荷（S→C）。
 *
 * <p>插件发来的字节就是一个完整的 mcai-bridge/1 帧，这里只负责原样搬运：
 * 解码时把可读字节全读出来，编码时再原样写回（客户端发 HELLO / ABORT / DONE 用）。</p>
 */
public record BridgePayload(byte[] data) implements CustomPacketPayload {

    public static final Type<BridgePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("mcai", "bridge"));

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
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
