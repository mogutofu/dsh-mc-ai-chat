package com.mogutofu.mcai.neoforge;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** 指令注册入口：先摘掉原版的 /weather、/vote，再注册 mcai 自己的。 */
public final class McaiCommands {

    private McaiCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        // 原版 /weather 要求 OP（permission 2），普通玩家用不了；把原版节点拿掉换成我们自己的。
        try {
            dispatcher.getRoot().getChildren().removeIf(node ->
                    "weather".equals(node.getName()) || "vote".equals(node.getName()));
            McaiMod.LOGGER.info("[mcai] 已替换原版 /weather（原版需要 OP，现在普通玩家也能发起天气投票）");
        } catch (Throwable broken) {
            McaiMod.LOGGER.warn("[mcai] 替换原版 /weather 失败（普通玩家可能仍受原版权限限制）：{}", broken.toString());
        }

        AiCommand.register(dispatcher);
        WeatherVote.register(dispatcher);
        McaiMod.LOGGER.info("[mcai] 已注册 /ai、/weather 与 /vote");
    }
}
