package com.mogutofu.mcai.client.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 兼容用：{@code minecraft:register}（以 0 分隔的通道名）。
 *
 * <p>Paper 的 {@code CraftPlayer#sendPluginMessage} 只发给客户端声明过的通道；1.20.5 起
 * 原版不再自动发 REGISTER，桥接客户端自己补一个，服务端会当 {@code DiscardedPayload}
 * 解析并登记通道，否则插件发的 INFO/REQUEST 会被静默丢弃。</p>
 */
public record BridgeRegisterPayload(byte[] data) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BridgeRegisterPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("minecraft", "register"));

    public static final StreamCodec<FriendlyByteBuf, BridgeRegisterPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeBytes(payload.data()),
            buffer -> {
                byte[] bytes = new byte[buffer.readableBytes()];
                buffer.readBytes(bytes);
                return new BridgeRegisterPayload(bytes);
            });

    public static BridgeRegisterPayload ofChannels(String... channels) {
        StringBuilder builder = new StringBuilder();
        for (String channel : channels) {
            if (channel != null && !channel.isEmpty()) {
                builder.append(channel).append('\u0000');
            }
        }
        return new BridgeRegisterPayload(builder.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public BridgeRegisterPayload {
        data = data == null ? new byte[0] : data;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
