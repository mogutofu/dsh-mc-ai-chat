package com.mogutofu.mcai;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@code /ai <内容>} —— 权限等级 0，所有玩家一视同仁。
 *
 * <p>指令本身只做三件很快的事：查开关、查冷却、把请求丢给后台线程。
 * 真正等 AI 回复在 {@link AiHttpClient} 里，绝对不占服务端主线程。</p>
 */
@Mod.EventBusSubscriber(modid = McaiMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AiCommandHandler {

    private AiCommandHandler() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // 没有 require(...)，就是权限等级 0：普通玩家也能用，op 也不会多出什么能力。
        dispatcher.register(Commands.literal("ai")
                .executes(AiCommandHandler::usage)
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(AiCommandHandler::run)));

        McaiMod.LOGGER.info("[mcai] 已注册 /ai 指令");
    }

    private static int usage(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(
                () -> Component.literal("用法：/ai <内容>").withStyle(ChatFormatting.GRAY), false);
        return 0;
    }

    private static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            // 控制台没有 UUID/名字可用，协议里也没法塞个“服务端”
            source.sendFailure(Component.literal("这个指令只能在游戏里用。"));
            return 0;
        }

        if (!McaiConfig.enabled()) {
            player.sendSystemMessage(Component.literal("[小铜] AI 未开启").withStyle(ChatFormatting.RED));
            return 0;
        }

        String message = StringArgumentType.getString(context, "message").trim();
        if (message.isEmpty()) {
            player.sendSystemMessage(Component.literal("用法：/ai <内容>").withStyle(ChatFormatting.GRAY));
            return 0;
        }

        if (!RateLimiter.tryAcquire(player.getUUID(), McaiConfig.rateLimitSeconds())) {
            player.sendSystemMessage(Component.literal("慢一点，刚问过啦").withStyle(ChatFormatting.GRAY));
            return 0;
        }

        // 契约里这条提示是可选的，给玩家一个“收到了、在等”的反馈
        player.sendSystemMessage(Component.literal("[小铜] 正在输入…").withStyle(ChatFormatting.DARK_GRAY));

        AiHttpClient.ask(source.getServer(), player, message);
        return 1;
    }
}
