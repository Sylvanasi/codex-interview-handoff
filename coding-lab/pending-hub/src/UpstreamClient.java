import java.util.List;

/** 一个业务系统的待办查询接口。只读接口，不要改。 */
public interface UpstreamClient {

    /** 系统编码，例如 OA / ERP。 */
    String systemCode();

    /** 拉取该用户在该系统的待办。可能慢、可能抛异常、可能一直不返回。 */
    List<PendingItem> fetch(String userId) throws Exception;
}
