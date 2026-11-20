package com.mogutofu.mcai.forge;

import com.mogutofu.mcai.core.AiSettings;
import com.mogutofu.mcai.core.WeatherVoteService;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandManager;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Map;

/**
 * Minecraft AI Chat 服务端模组（Forge 1.12.2）。
 *
 * <p>mod 本身不含 AI 能力：它把游戏里的 {@code /ai <内容>} 打包成 JSON，POST 到本机的
 * DSH 服务（默认 {@code http://127.0.0.1:8765/mc-ai}），再把回复贴回聊天栏。服务端那半由
 * {@code dsh-mc-server-ops} 插件提供。</p>
 *
 * <p>1.3.0 起还带两件事：</p>
 * <ul>
 *   <li>{@code /ai screenshot}、{@code /ai file}：装了 {@code mcai-client} 的玩家可以把截图
 *       或本地文件一起发给 AI，附件走 {@code mcai:net} 通道（只在专用服务器注册，单机按无客户端降级）；</li>
 *   <li>{@code /weather} + {@code /vote}：普通玩家也能改天气（在线人多时走投票），
 *       会顶掉原版需要 OP 的 {@code /weather}。</li>
 * </ul>
 *
 * <p>配置：{@code config/mcai-common.json}（首次启动写默认值）、
 * {@code config/mcai-weather.json}（由 DSH 插件写入，每次 /weather 重新读）。</p>
 */
@Mod(modid = McaiMod.MODID, name = "Minecraft AI Chat", version = McaiMod.VERSION,
        acceptableRemoteVersions = "*", acceptedMinecraftVersions = "[1.12.2]")
public final class McaiMod {

    public static final String MODID = "mcai";
    public static final String VERSION = "1.3.0";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    private static File configDir = new File("config");
    private static WeatherVoteService weatherService;

    public McaiMod() {
        // 1.12.2 没有 @Mod.EventBusSubscriber，事件处理器要手动挂到事件总线上
        MinecraftForge.EVENT_BUS.register(new McaiServerEvents());
        LOGGER.info("[mcai] Minecraft AI Chat 已加载，/ai 指令准备就绪");
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        File modConfigDir = event.getModConfigurationDirectory();
        if (modConfigDir != null) {
            configDir = modConfigDir;
        }
        // config/mcai-common.json 只在缺失时写一份默认值，已存在不动
        AiSettings.writeDefaultIfMissing(commonConfig());

        // 附件网络只在专用服务器注册：单机 integrated server 不支持，
        // 客户端 JVM 里由 mcai-client 模组注册同名通道，避免同一 JVM 重复注册。
        // 1.12.2 的物理端枚举是 CLIENT/SERVER（没有 1.13+ 的 DEDICATED_SERVER），
        // 客户端 JVM 里 getSide() 恒为 CLIENT，所以这里判 SERVER 就够了。
        if (FMLCommonHandler.instance().getSide() == Side.SERVER) {
            McaiNetwork.register();
        } else {
            LOGGER.info("[mcai] 当前不是专用服务器，mcai:net 通道不注册（附件仅支持专用服务器）");
        }
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        MinecraftServer server = event.getServer();

        // 原版 /weather 要求 OP（permission 2），普通玩家用不了；先把原版节点摘掉再注册自己的
        boolean replaced = removeCommand(server.getCommandManager(), "weather");
        removeCommand(server.getCommandManager(), "vote");
        if (replaced) {
            LOGGER.info("[mcai] 已替换原版 /weather（原版需要 OP，现在普通玩家也能发起天气投票）");
        } else {
            LOGGER.warn("[mcai] 没找到原版 /weather 节点，直接注册同名指令（普通玩家可能仍受原版权限限制）");
        }

        event.registerServerCommand(new AiCommand());
        event.registerServerCommand(new WeatherCommand());
        event.registerServerCommand(new VoteCommand());

        weatherService = new WeatherVoteService(new WeatherHost(server));
        LOGGER.info("[mcai] 已注册 /ai、/weather、/vote（游戏内天气投票）");
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        PendingTransfers.clear();
        ClientPresence.clear();
        if (weatherService != null) {
            weatherService.reset();
            weatherService = null;
        }
    }

    // ── 配置 / 服务访问 ────────────────────────────────────────────────

    public static File configDir() {
        return configDir;
    }

    public static File commonConfig() {
        return new File(configDir, "mcai-common.json");
    }

    public static File weatherConfig() {
        return new File(configDir, "mcai-weather.json");
    }

    /** 当前服务端的天气投票服务；服务端没起/已停时是 null。 */
    public static WeatherVoteService weatherService() {
        return weatherService;
    }

    // ── 覆盖原版指令 ────────────────────────────────────────────────────

    /**
     * 从 1.12.2 的 {@code CommandManager}（{@code ServerCommandManager} /
     * {@code CommandHandler}）里删掉一个指令节点。
     *
     * <p>正常路径用 {@code ICommandManager#getCommands()} 返回的那张 name -> ICommand 表；
     * 万一 Forge/服务端把那东西换成不可变 Map，再反射扫一遍 Map 字段兜底。</p>
     */
    private static boolean removeCommand(ICommandManager manager, String name) {
        if (manager == null || name == null) {
            return false;
        }
        try {
            Map<String, ICommand> commands = manager.getCommands();
            if (commands != null) {
                try {
                    return commands.remove(name) != null;
                } catch (UnsupportedOperationException readOnly) {
                    LOGGER.debug("[mcai] 指令表只读，改走反射删除 {}", name);
                }
            }
        } catch (Throwable broken) {
            LOGGER.debug("[mcai] 读指令表失败（{}），改走反射", broken.toString());
        }
        for (Class<?> type = manager.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(manager);
                    if (value instanceof Map && ((Map<?, ?>) value).containsKey(name)) {
                        ((Map<?, ?>) value).remove(name);
                        return true;
                    }
                } catch (Throwable ignored) {
                    // 这个字段读不了就换下一个
                }
            }
        }
        return false;
    }
}
