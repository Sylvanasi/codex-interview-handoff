import java.time.Duration;
import java.util.List;

/**
 * ★ 你要实现的就是这个类，其它文件都不用改。★
 *
 * <p>契约（HubLab 按这个契约自动验收）：
 * <ol>
 *   <li>对入参 clients 里的每个上游<b>并发</b>调用 {@code fetch(userId)}，而不是一个一个等。</li>
 *   <li>单个上游耗时超过 {@code upstreamTimeout} 就算失败，不允许拖慢整体返回。</li>
 *   <li>上游抛异常或超时：该上游的 code 进 {@code degradedSystems}，不能影响其它上游的数据返回。</li>
 *   <li>{@code items} 的顺序 == clients 的注册顺序（同一上游内部保持它自己的顺序），保证前端渲染稳定。</li>
 *   <li>{@code latencyMillis} 覆盖<b>每一个</b>上游，包括失败和超时的（这是告警和排查的依据）。</li>
 *   <li>{@code costMillis} == 本次聚合的墙钟耗时。</li>
 *   <li>{@code degradedSystems} 的内部顺序不作要求，HubLab 会先排序再比较。</li>
 * </ol>
 *
 * <p>第 3 关（可选）：同一个 userId 在 5 秒内重复请求时复用上一次结果，只缓存成功结果
 * （带降级的那次不缓存）。缓存字段自己加。
 *
 * <p>现在的方法体是空的，直接跑 HubLab 会看到一串 FAIL，这是预期结果。
 */
public class PendingAggregator implements AutoCloseable {

    private final Duration upstreamTimeout;

    public PendingAggregator(Duration upstreamTimeout) {
        this.upstreamTimeout = upstreamTimeout;
    }

    public HubSnapshot aggregate(String userId, List<UpstreamClient> clients) {
        throw new UnsupportedOperationException("第 2 关还没写：aggregate 是空的");
    }

    @Override
    public void close() {
        // 第 3 关：如果你在构造器里创建了线程池，在这里优雅关闭它。
    }
}
