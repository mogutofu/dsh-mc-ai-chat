import com.mogutofu.mcai.core.Actor;
import com.mogutofu.mcai.core.WeatherSettings;
import com.mogutofu.mcai.core.WeatherVoteService;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 不依赖 Minecraft 的天气投票逻辑自测（Java 8+ 可跑）：
 *   javac -encoding UTF-8 -d out common/src/main/java/com/mogutofu/mcai/core/*.java tests/WeatherVoteSelfTest.java
 *   java -cp out WeatherVoteSelfTest
 */
public final class WeatherVoteSelfTest {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        testDirectWhenFewPlayers();
        testVoteApprove();
        testVoteReject();
        testOpAndConsoleDirect();
        testCooldown();
        testTimeout();
        System.out.println(failures == 0 ? "ALL TESTS PASSED" : failures + " TEST(S) FAILED");
        if (failures != 0) {
            System.exit(1);
        }
    }

    private static void testDirectWhenFewPlayers() throws Exception {
        FakeHost host = new FakeHost(2);
        WeatherVoteService service = new WeatherVoteService(host);
        int result = service.weather(player("A"), "rain", 60);
        check("direct result", result == 1);
        check("direct applied", "rain".equals(host.appliedType));
        check("direct broadcast", host.broadcasts.stream().anyMatch(s -> s.contains("把天气改成了下雨")));
    }

    private static void testVoteApprove() throws Exception {
        FakeHost host = new FakeHost(3);
        WeatherVoteService service = new WeatherVoteService(host);
        Actor a = player("A");
        Actor b = player("B");
        service.weather(a, "thunder", null);
        check("vote started", host.broadcasts.stream().anyMatch(s -> s.contains("需要 2 人同意")));
        check("no weather yet", host.appliedType == null);
        service.vote(b, true);
        check("vote approved", "thunder".equals(host.appliedType));
        check("approve broadcast", host.broadcasts.stream().anyMatch(s -> s.contains("投票通过")));
    }

    private static void testVoteReject() throws Exception {
        FakeHost host = new FakeHost(3);
        WeatherVoteService service = new WeatherVoteService(host);
        service.weather(player("A"), "clear", null);
        service.vote(player("B"), false);
        check("reject keeps weather", host.appliedType == null);
        check("reject broadcast", host.broadcasts.stream().anyMatch(s -> s.contains("投票被否决")));
    }

    private static void testOpAndConsoleDirect() throws Exception {
        FakeHost host = new FakeHost(5);
        WeatherVoteService service = new WeatherVoteService(host);
        Actor op = Actor.player(UUID.randomUUID(), "OP", true);
        service.weather(op, "clear", 10);
        check("op direct", "clear".equals(host.appliedType));
        host.appliedType = null;
        service.weather(Actor.console(), "rain", 10);
        check("console direct", "rain".equals(host.appliedType));
    }

    private static void testCooldown() throws Exception {
        FakeHost host = new FakeHost(1);
        WeatherVoteService service = new WeatherVoteService(host);
        Actor a = player("A");
        service.weather(a, "rain", null);
        service.weather(a, "clear", null);
        check("cooldown blocks second", "rain".equals(host.appliedType));
        check("cooldown message", host.sent.stream().anyMatch(s -> s.contains("天气改太快了")));
    }

    private static void testTimeout() throws Exception {
        File cfg = File.createTempFile("mcai-weather-test", ".json");
        cfg.deleteOnExit();
        try (PrintWriter out = new PrintWriter(cfg, "UTF-8")) {
            out.print("{\"enabled\":true,\"directThreshold\":0,\"approveNeed\":2,\"rejectNeed\":1,"
                    + "\"timeoutSeconds\":5,\"cooldownSeconds\":0}");
        }
        FakeHost host = new FakeHost(3);
        host.config = cfg;
        WeatherVoteService service = new WeatherVoteService(host);
        service.weather(player("A"), "rain", null);
        Thread.sleep(5200);
        // tick 内部每 20 次检查一次（游戏里 1 秒 20 tick）
        for (int i = 0; i < 20; i++) {
            service.tick();
        }
        check("timeout broadcast", host.broadcasts.stream().anyMatch(s -> s.contains("超时")));
    }

    // ── 测试替身 ──────────────────────────────────────────────────────

    private static Actor player(String name) {
        return Actor.player(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name, false);
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            System.out.println("  ok   " + name);
        } else {
            failures++;
            System.out.println("  FAIL " + name);
        }
    }

    private static final class FakeHost implements WeatherVoteService.Host {
        final int online;
        File config;
        String appliedType;
        final List<String> broadcasts = new ArrayList<>();
        final List<String> sent = new ArrayList<>();

        FakeHost(int online) {
            this.online = online;
        }

        @Override
        public int onlineCount() {
            return online;
        }

        @Override
        public void send(Actor actor, String message) {
            sent.add(message);
        }

        @Override
        public void broadcast(String message) {
            broadcasts.add(message);
        }

        @Override
        public void applyWeather(String type, Integer durationSeconds) {
            appliedType = type;
        }

        @Override
        public void log(String message) {
        }

        @Override
        public File configFile() {
            return config;
        }
    }
}
