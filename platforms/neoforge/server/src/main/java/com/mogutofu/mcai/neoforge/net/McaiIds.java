package com.mogutofu.mcai.neoforge.net;

import net.minecraft.resources.Identifier;

/**
 * payload id 工厂。
 *
 * <p>26.x 用的是官方名 {@code Identifier}（1.21.x 及更早叫 {@code ResourceLocation}）。
 * 整个工程只有这里碰这个类型，万一版本间名字不同只改这一处。</p>
 */
public final class McaiIds {

    private McaiIds() {
    }

    public static Identifier of(String path) {
        return Identifier.fromNamespaceAndPath(NetProtocol.NAMESPACE, path);
    }
}
