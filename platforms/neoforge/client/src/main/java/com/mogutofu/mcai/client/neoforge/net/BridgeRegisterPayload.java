package com.mogutofu.mcai.client.neoforge.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 兼容用：{@code minecraft:register}（1.20.1 的通道声明包，1.20.5+ 已不是原版载荷，
 * 但 Paper 仍然把它当 {@code DiscardedPayload} 解析并登记客户端监听的通道）。
 *
 * <p>为什么需要它：Paper 的 {@code CraftPlayer#sendPluginMessage} 只发给「客户端声明过」
 * 的通道。1.21 起原版不再自动发 REGISTER，所以桥接客户端要自己补一个，
 * 否则插件发的 INFO/REQUEST 会被静默丢弃。载荷格式：以 0 分隔的通道名（UTF-8）。</p>
 */
public record BridgeRegisterPayload(byte[] data) implements CustomPacketPayload {

    public static final Type<BridgeRegisterPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("minecraft", "register"));

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
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
