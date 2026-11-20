package com.mogutofu.mcai.core;

import java.util.UUID;

/** 执行指令的人：游戏内玩家，或控制台。 */
public final class Actor {

    public final UUID id;
    public final String name;
    public final boolean console;
    public final boolean op;

    private Actor(UUID id, String name, boolean console, boolean op) {
        this.id = id;
        this.name = name;
        this.console = console;
        this.op = op;
    }

    public static Actor player(UUID id, String name, boolean op) {
        return new Actor(id, name, false, op);
    }

    public static Actor console() {
        return new Actor(null, "服务器控制台", true, true);
    }

    /** 统一的名字：控制台显示“服务器控制台”，玩家用游戏名。 */
    public String displayName() {
        return console ? "服务器控制台" : name;
    }
}
