import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 自动验收脚本：跑这个类，看自己的实现是否满足契约。不要改这个文件。 */
public class HubLab {

    private static final Duration UPSTREAM_TIMEOUT = Duration.ofMillis(250);
    private static final List<String> ALL_CODES = List.of("OA", "ERP", "HR", "FIN");

    public static void main(String[] args) {
        System.out.println("=== 统一待办聚合实验室（Pending Hub Lab）===");
        System.out.println("单系统超时设定：" + UPSTREAM_TIMEOUT.toMillis() + "ms");
        System.out.println("健康场景上游耗时：OA 120ms / ERP 150ms / HR 90ms / FIN 110ms");
        System.out.println("故障场景上游：ERP 抛异常(150ms) / HR 卡死(1200ms)");

        baselineSerial();
        level2Healthy();
        level2Faulty();
        level3Optional();

        int fails = Check.summary();
        System.out.println();
        System.out.println(fails == 0
                ? "全部通过。把源码 + 本次输出交给我 review，我会像面试官一样追问。"
                : "还有 " + fails + " 项未通过，先自己修，别急着看答案。");
    }

    private static void baselineSerial() {
        Check.section("第 1 关：串行基线（已提供实现，只观察数字）");
        HubSnapshot snap = new SerialPendingAggregator().aggregate("u-serial", healthyClients());
        System.out.println("  串行聚合耗时：" + snap.costMillis() + "ms，待办 " + snap.items().size() + " 条");
        System.out.println("  各上游耗时：" + snap.latencyMillis());
        Check.that("串行版本耗时 >= 400ms（4 个上游相加，约 470ms）", snap.costMillis() >= 400);
        Check.flush();
    }

    private static void level2Healthy() {
        Check.section("第 2 关-A：健康场景（并发后应 ≈ 最慢的那个上游）");
        List<UpstreamClient> clients = healthyClients();
        HubSnapshot snap = null;
        try (PendingAggregator aggregator = new PendingAggregator(UPSTREAM_TIMEOUT)) {
            snap = run(aggregator, "u-healthy", clients);
        }
        if (snap != null) {
            System.out.println("  聚合结果：" + snap);
        }
        Check.that("4 个系统全部返回，共 8 条待办", snap != null && snap.items().size() == 8);
        Check.that("无降级系统", snap != null && snap.degradedSystems().isEmpty());
        Check.that("整体耗时 < 300ms（串行约 470ms）", snap != null && snap.costMillis() < 300);
        Check.that("4 个上游都有耗时埋点", snap != null && snap.latencyMillis().keySet().containsAll(ALL_CODES));
        Check.that("待办顺序与上游注册顺序一致（OA,OA,ERP,ERP,HR,HR,FIN,FIN）",
                snap != null && matchesOrder(snap.items(), List.of("OA", "ERP", "HR", "FIN")));
        Check.flush();
    }

    private static void level2Faulty() {
        Check.section("第 2 关-B：故障场景（1 个抛异常 + 1 个卡死超时）");
        List<UpstreamClient> clients = faultyClients();
        HubSnapshot snap = null;
        try (PendingAggregator aggregator = new PendingAggregator(UPSTREAM_TIMEOUT)) {
            snap = run(aggregator, "u-faulty", clients);
        }
        if (snap != null) {
            System.out.println("  聚合结果：" + snap);
        }
        List<String> degraded = new ArrayList<>();
        if (snap != null) {
            degraded.addAll(snap.degradedSystems());
        }
        Collections.sort(degraded);
        Check.that("恰好 ERP（抛异常）与 HR（超时）被降级",
                snap != null && degraded.equals(List.of("ERP", "HR")));
        Check.that("健康系统 OA、FIN 的 4 条待办仍然返回",
                snap != null && snap.items().size() == 4 && onlyFrom(snap.items(), List.of("OA", "FIN")));
        Check.that("不等卡死的上游，整体耗时 < 500ms", snap != null && snap.costMillis() < 500);
        Check.that("失败/超时的上游也有耗时埋点", snap != null && snap.latencyMillis().keySet().containsAll(ALL_CODES));
        Check.that("待办顺序仍按上游注册顺序（OA,OA,FIN,FIN）",
                snap != null && matchesOrder(snap.items(), List.of("OA", "FIN")));
        Check.flush();
    }

    private static void level3Optional() {
        Check.section("第 3 关（可选）：同一用户 5s 内重复进入门户，不重复打上游");

        List<UpstreamClient> cacheClients = healthyClients();
        HubSnapshot first = null;
        HubSnapshot second = null;
        try (PendingAggregator aggregator = new PendingAggregator(UPSTREAM_TIMEOUT)) {
            first = run(aggregator, "u-cache", cacheClients);
            second = run(aggregator, "u-cache", cacheClients);
        }
        Check.that("[可选] 第二次命中缓存：上游总调用次数仍为 4", fetchTotal(cacheClients) == 4);
        Check.that("[可选] 第二次请求耗时 < 80ms", second != null && second.costMillis() < 80);
        Check.that("[可选] 两次返回的待办一致",
                first != null && second != null && first.items().equals(second.items()));

        List<UpstreamClient> degradeClients = faultyClients();
        try (PendingAggregator aggregator = new PendingAggregator(UPSTREAM_TIMEOUT)) {
            run(aggregator, "u-degrade", degradeClients);
            run(aggregator, "u-degrade", degradeClients);
        }
        Check.that("[可选] 降级结果不进缓存：第二次仍然打上游（总调用次数 8）",
                fetchTotal(degradeClients) == 8);
        Check.flush();
    }

    private static HubSnapshot run(PendingAggregator aggregator, String userId, List<UpstreamClient> clients) {
        try {
            return aggregator.aggregate(userId, clients);
        } catch (Throwable t) {
            System.out.println("  [ERROR] aggregate 抛异常：" + t);
            return null;
        }
    }

    private static List<UpstreamClient> healthyClients() {
        return List.of(
                new SimulatedUpstream("OA", 120, SimulatedUpstream.Mode.OK),
                new SimulatedUpstream("ERP", 150, SimulatedUpstream.Mode.OK),
                new SimulatedUpstream("HR", 90, SimulatedUpstream.Mode.OK),
                new SimulatedUpstream("FIN", 110, SimulatedUpstream.Mode.OK));
    }

    private static List<UpstreamClient> faultyClients() {
        return List.of(
                new SimulatedUpstream("OA", 120, SimulatedUpstream.Mode.OK),
                new SimulatedUpstream("ERP", 150, SimulatedUpstream.Mode.THROW),
                new SimulatedUpstream("HR", 1200, SimulatedUpstream.Mode.HANG),
                new SimulatedUpstream("FIN", 110, SimulatedUpstream.Mode.OK));
    }

    private static boolean matchesOrder(List<PendingItem> items, List<String> systems) {
        List<String> expected = new ArrayList<>();
        for (String code : systems) {
            expected.add(code);
            expected.add(code);
        }
        List<String> actual = new ArrayList<>();
        for (PendingItem item : items) {
            actual.add(item.systemCode());
        }
        return actual.equals(expected);
    }

    private static boolean onlyFrom(List<PendingItem> items, List<String> systems) {
        for (PendingItem item : items) {
            if (!systems.contains(item.systemCode())) {
                return false;
            }
        }
        return true;
    }

    private static int fetchTotal(List<UpstreamClient> clients) {
        int total = 0;
        for (UpstreamClient client : clients) {
            total += ((SimulatedUpstream) client).fetchCount();
        }
        return total;
    }
}
