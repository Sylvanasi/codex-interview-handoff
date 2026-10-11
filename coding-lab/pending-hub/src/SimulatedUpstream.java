import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** 模拟一个上游系统：可以慢、可以抛异常、可以卡死。只读代码，不要改。 */
public class SimulatedUpstream implements UpstreamClient {

    public enum Mode {
        /** 正常返回 */
        OK,
        /** 睡够时间后抛异常，模拟上游 500 */
        THROW,
        /** 一直睡，模拟接口卡死（会被超时逻辑判定为失败） */
        HANG
    }

    private static final long HANG_SLEEP_MILLIS = 1200L;

    private final String code;
    private final long sleepMillis;
    private final Mode mode;
    private final int itemCount;
    private final AtomicInteger fetchCount = new AtomicInteger();

    public SimulatedUpstream(String code, long sleepMillis, Mode mode) {
        this(code, sleepMillis, mode, 2);
    }

    public SimulatedUpstream(String code, long sleepMillis, Mode mode, int itemCount) {
        this.code = code;
        this.sleepMillis = sleepMillis;
        this.mode = mode;
        this.itemCount = itemCount;
    }

    @Override
    public String systemCode() {
        return code;
    }

    @Override
    public List<PendingItem> fetch(String userId) throws Exception {
        fetchCount.incrementAndGet();
        sleep(mode == Mode.HANG ? HANG_SLEEP_MILLIS : sleepMillis);
        if (mode == Mode.THROW) {
            throw new IllegalStateException(code + " 上游返回 500");
        }
        List<PendingItem> items = new ArrayList<>(itemCount);
        for (int i = 0; i < itemCount; i++) {
            items.add(new PendingItem(code, code + "-待办-" + (i + 1), System.currentTimeMillis() + 3_600_000L));
        }
        return items;
    }

    /** 被真正调用过几次（第 3 关用来验证缓存是否生效）。 */
    public int fetchCount() {
        return fetchCount.get();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
