import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 改造前的串行实现：一个上游接一个上游地等。只读代码，不要改。
 * 第 1 关就是看懂它为什么慢：4 个上游的耗时是相加的。
 */
public class SerialPendingAggregator {

    public HubSnapshot aggregate(String userId, List<UpstreamClient> clients) {
        long start = System.currentTimeMillis();
        List<PendingItem> items = new ArrayList<>();
        List<String> degraded = new ArrayList<>();
        Map<String, Long> latency = new LinkedHashMap<>();

        for (UpstreamClient client : clients) {
            long begin = System.currentTimeMillis();
            try {
                items.addAll(client.fetch(userId));
            } catch (Exception e) {
                degraded.add(client.systemCode());
            }
            latency.put(client.systemCode(), System.currentTimeMillis() - begin);
        }
        return new HubSnapshot(items, degraded, latency, System.currentTimeMillis() - start);
    }
}
