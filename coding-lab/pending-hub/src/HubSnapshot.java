import java.util.List;
import java.util.Map;

/** 一次聚合的结果。只读数据结构，不要改。 */
public final class HubSnapshot {

    private final List<PendingItem> items;
    private final List<String> degradedSystems;
    private final Map<String, Long> latencyMillis;
    private final long costMillis;

    public HubSnapshot(List<PendingItem> items,
                       List<String> degradedSystems,
                       Map<String, Long> latencyMillis,
                       long costMillis) {
        this.items = List.copyOf(items);
        this.degradedSystems = List.copyOf(degradedSystems);
        this.latencyMillis = Map.copyOf(latencyMillis);
        this.costMillis = costMillis;
    }

    /** 所有成功返回的待办，顺序由实现决定（契约要求按上游注册顺序）。 */
    public List<PendingItem> items() {
        return items;
    }

    /** 被降级（异常或超时）的上游系统编码。 */
    public List<String> degradedSystems() {
        return degradedSystems;
    }

    /** 每个上游的耗时埋点，key = 系统编码。 */
    public Map<String, Long> latencyMillis() {
        return latencyMillis;
    }

    /** 本次聚合的整体墙钟耗时。 */
    public long costMillis() {
        return costMillis;
    }

    @Override
    public String toString() {
        return "HubSnapshot{items=" + items.size()
                + ", degraded=" + degradedSystems
                + ", latency=" + latencyMillis
                + ", cost=" + costMillis + "ms}";
    }
}
