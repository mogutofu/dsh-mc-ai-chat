import com.mogutofu.mcai.bridge.BridgeProtocol;
import com.mogutofu.mcai.bridge.BridgeTokens;
import com.mogutofu.mcai.bridge.BridgeUpload;

import java.util.Arrays;
import java.util.UUID;

/**
 * mcai:bridge 纯逻辑自测：帧编解码、token 一次性/TTL/绑定、上传 URL 与文件名清洗。
 *
 * <p>不依赖 Minecraft / Bukkit / 网络，直接 javac + java 跑。用法：</p>
 * <pre>
 *   javac -encoding UTF-8 -d out common/src/bridge/java/com/mogutofu/mcai/bridge/*.java tests/BridgeSelfTest.java
 *   java -cp out BridgeSelfTest
 * </pre>
 */
public final class BridgeSelfTest {

    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        protocolRoundTrip();
        protocolRejectsBadFrames();
        tokens();
        uploadHelpers();
        System.out.println();
        System.out.println(failed == 0
                ? "全部通过：" + passed + " 项"
                : "失败 " + failed + " 项（通过 " + passed + "）");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ── 协议 ─────────────────────────────────────────────────────────

    private static void protocolRoundTrip() {
        BridgeProtocol.Hello hello = BridgeProtocol.parseHello(BridgeProtocol.hello("neoforge", "1.4.0"));
        check("HELLO loader", "neoforge".equals(hello.loader));
        check("HELLO clientVersion", "1.4.0".equals(hello.clientVersion));

        BridgeProtocol.Info info = BridgeProtocol.parseInfo(
                BridgeProtocol.info(25566, "mc.example.com", "tok-123", 120, 8 * 1024 * 1024, 16 * 1024 * 1024));
        check("INFO port", info.port == 25566);
        check("INFO host", "mc.example.com".equals(info.advertiseHost));
        check("INFO token", "tok-123".equals(info.token));
        check("INFO ttl", info.ttlSeconds == 120);
        check("INFO maxImage", info.maxImageBytes == 8 * 1024 * 1024);
        check("INFO maxFile", info.maxFileBytes == 16 * 1024 * 1024);

        UUID transfer = UUID.fromString("12345678-1234-5678-9abc-def012345678");
        BridgeProtocol.Request request = BridgeProtocol.parseRequest(
                BridgeProtocol.request(transfer, BridgeProtocol.KIND_FILE, "C:\\tmp\\a b.txt", 123));
        check("REQUEST uuid", transfer.equals(request.transferId));
        check("REQUEST kind", request.kind == BridgeProtocol.KIND_FILE);
        check("REQUEST path（中文/空格/反斜杠）", "C:\\tmp\\a b.txt".equals(request.path));
        check("REQUEST maxBytes", request.maxBytes == 123);

        BridgeProtocol.Request shot = BridgeProtocol.parseRequest(
                BridgeProtocol.request(transfer, BridgeProtocol.KIND_SCREENSHOT, "", 8));
        check("REQUEST 截图 path 为空", "".equals(shot.path));
        check("REQUEST 截图 kind=0", shot.kind == BridgeProtocol.KIND_SCREENSHOT);

        BridgeProtocol.Abort abort = BridgeProtocol.parseAbort(BridgeProtocol.abort(transfer, "文件不存在"));
        check("ABORT uuid", transfer.equals(abort.transferId));
        check("ABORT reason", "文件不存在".equals(abort.reason));

        BridgeProtocol.Done doneOk = BridgeProtocol.parseDone(BridgeProtocol.done(transfer, true, ""));
        check("DONE ok", doneOk.ok && transfer.equals(doneOk.transferId));
        BridgeProtocol.Done doneFail = BridgeProtocol.parseDone(BridgeProtocol.done(transfer, false, "HTTP 413"));
        check("DONE 失败带原因", !doneFail.ok && "HTTP 413".equals(doneFail.error));

        // 空字符串 / null 也要能编解码
        BridgeProtocol.Hello empty = BridgeProtocol.parseHello(BridgeProtocol.hello(null, null));
        check("HELLO null → 空串", "".equals(empty.loader) && "".equals(empty.clientVersion));

        // 帧头：magic + 版本 + 类型
        byte[] raw = BridgeProtocol.hello("forge", "1.4.0");
        check("帧 magic", raw[0] == 'M' && raw[1] == 'C' && raw[2] == 'A' && raw[3] == 'I');
        check("帧版本=1", raw[4] == 1);
        check("帧类型=HELLO", BridgeProtocol.typeOf(raw) == BridgeProtocol.TYPE_HELLO);
        check("通道名固定", "mcai:bridge".equals(BridgeProtocol.CHANNEL));
    }

    private static void protocolRejectsBadFrames() {
        expectReject("null 帧", null);
        expectReject("太短", new byte[]{'M', 'C', 'A', 'I'});
        expectReject("magic 不对", new byte[]{'X', 'C', 'A', 'I', 1, 1});
        expectReject("版本不支持", new byte[]{'M', 'C', 'A', 'I', 9, 1});
        expectReject("类型不符", BridgeProtocol.hello("forge", "1.0"), 2);
        // 截断的 UTF：长度声明 10 但只有 2 字节
        byte[] truncated = new byte[]{'M', 'C', 'A', 'I', 1, 1, 0, 0, 0, 10, 'a', 'b'};
        expectReject("字符串截断", truncated);
        // 长度超上限
        byte[] giant = new byte[]{'M', 'C', 'A', 'I', 1, 1, 0, 1, 0, 0};
        expectReject("字符串长度超上限", giant);
    }

    private static void expectReject(String name, byte[] frame) {
        try {
            BridgeProtocol.parseHello(frame);
            check(name + " 应被拒绝", false);
        } catch (IllegalArgumentException expected) {
            check(name + " 被拒绝", true);
        }
    }

    private static void expectReject(String name, byte[] frame, int expectedType) {
        try {
            if (expectedType == 2) {
                BridgeProtocol.parseInfo(frame);
            } else {
                BridgeProtocol.parseHello(frame);
            }
            check(name + " 应被拒绝", false);
        } catch (IllegalArgumentException expected) {
            check(name + " 被拒绝", true);
        }
    }

    // ── token ────────────────────────────────────────────────────────

    private static void tokens() {
        BridgeTokens tokens = new BridgeTokens();
        UUID player = UUID.randomUUID();
        UUID transfer = UUID.randomUUID();
        long now = 1_000_000L;

        String token = tokens.issue(player, transfer, 120, now);
        check("token 长度 ≥ 32", token.length() >= 32, token.length());
        check("token 不含 padding", token.indexOf('=') < 0);

        BridgeTokens.Grant grant = tokens.consume(token, player, transfer, now + 1000);
        check("token 首次核销成功", grant != null && player.equals(grant.playerId) && transfer.equals(grant.transferId));
        check("token 只能用一次", tokens.consume(token, player, transfer, now + 1000) == null);
        check("token 用掉后表为空", tokens.size() == 0);

        String wrongPlayerToken = tokens.issue(player, transfer, 120, now);
        check("token 和玩家不匹配被拒", tokens.consume(wrongPlayerToken, UUID.randomUUID(), transfer, now + 1) == null);
        check("不匹配也作废（一次性）", tokens.consume(wrongPlayerToken, player, transfer, now + 1) == null);

        String wrongTransferToken = tokens.issue(player, transfer, 120, now);
        check("token 和 transfer 不匹配被拒",
                tokens.consume(wrongTransferToken, player, UUID.randomUUID(), now + 1) == null);

        String expired = tokens.issue(player, transfer, 10, now);
        check("token 过期被拒", tokens.consume(expired, player, transfer, now + 11_000L) == null);

        String ttlEdge = tokens.issue(player, transfer, 10, now);
        check("token 到期前最后一毫秒可用", tokens.consume(ttlEdge, player, transfer, now + 9_999L) != null);

        String revoked = tokens.issue(player, transfer, 120, now);
        tokens.revokePlayer(player);
        check("下线撤销 token", tokens.consume(revoked, player, transfer, now + 1) == null);

        tokens.issue(player, transfer, 1, now);
        tokens.issue(player, transfer, 1, now);
        check("purge 前 2 个", tokens.size() == 2);
        tokens.purge(now + 5000);
        check("purge 清掉过期的", tokens.size() == 0);

        // 每人每 token 唯一：两次签发不同
        check("两次签发不重复",
                !tokens.issue(player, transfer, 120, now).equals(tokens.issue(player, transfer, 120, now)));
    }

    // ── 上传辅助 ─────────────────────────────────────────────────────

    private static void uploadHelpers() {
        check("baseName Windows 路径", "a b.txt".equals(BridgeUpload.baseName("C:\\Users\\me\\a b.txt")));
        check("baseName Unix 路径", "c.png".equals(BridgeUpload.baseName("/tmp/c.png")));
        check("baseName 纯文件名", "x.png".equals(BridgeUpload.baseName("x.png")));
        check("baseName 空", "".equals(BridgeUpload.baseName(null)));
        check("baseName 目录结尾", "".equals(BridgeUpload.baseName("C:\\tmp\\")));

        check("URL 普通 host", "http://1.2.3.4:25566/mcai/upload"
                .equals(BridgeUpload.buildUrl("1.2.3.4", 25566)));
        check("URL IPv6 host", "http://[::1]:25566/mcai/upload"
                .equals(BridgeUpload.buildUrl("::1", 25566)));
        check("URL 带方括号 IPv6", "http://[::1]:25566/mcai/upload"
                .equals(BridgeUpload.buildUrl("[::1]", 25566)));

        check("advertise-host 为空用连接地址", "mc.example.com".equals(BridgeUpload.resolveHost("", "mc.example.com")));
        check("advertise-host 优先", "public.example.com".equals(
                BridgeUpload.resolveHost("public.example.com", "127.0.0.1")));
        check("resolveHost null", "".equals(BridgeUpload.resolveHost(null, null)));
    }

    // ── 断言 ─────────────────────────────────────────────────────────

    private static void check(String name, boolean ok) {
        check(name, ok, "");
    }

    private static void check(String name, boolean ok, Object detail) {
        if (ok) {
            passed++;
            System.out.println("PASS " + name + (detail.toString().isEmpty() ? "" : " :: " + detail));
        } else {
            failed++;
            System.out.println("FAIL " + name + (detail.toString().isEmpty() ? "" : " :: " + detail));
        }
    }

    private BridgeSelfTest() {
    }
}
