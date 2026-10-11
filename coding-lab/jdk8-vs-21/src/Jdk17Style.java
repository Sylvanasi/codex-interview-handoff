import java.util.List;

/**
 * JDK 17 的写法：同样五件事，代码量和出错概率一起下降。
 * 17 是继 8 之后第一个被大规模采用的 LTS 版本，Spring Boot 3 的底线就是它。
 */
public class Jdk17Style {

    // ==========================================================
    // 1. record：一行顶 Java 8 那三十行，自动生成构造、getter、equals、hashCode、toString
    //    注意：字段是 final、不能继承别的类，所以适合 DTO，不适合当实体类。
    // ==========================================================
    public record Contract(String bizId, double amount) {
    }

    // ==========================================================
    // 2. 文本块：三段引号，换行和缩进原样保留，不用再写 \n 和 +
    // ==========================================================
    public static String buildJson(String bizId, double amount) {
        return """
                {
                  "bizId": "%s",
                  "amount": %s,
                  "source": "OA"
                }
                """.formatted(bizId, amount);
    }

    // ==========================================================
    // 3. switch 表达式：用 -> 代替冒号，不再需要 break，而且必须覆盖所有分支
    //    编译器会帮你检查"漏了某个枚举值"，老 switch 漏了只会默默走 default。
    // ==========================================================
    public static String describeStatus(int status) {
        return switch (status) {
            case 1 -> "草稿";
            case 2 -> "审批中";
            case 3, 4 -> "已归档";
            default -> "未知状态";
        };
    }

    // ==========================================================
    // 4. instanceof 模式匹配：判断和赋值合成一步，变量作用域也被限制在分支内
    // ==========================================================
    public static String describe(Object payload) {
        if (payload instanceof String s) {
            return "字符串，长度 " + s.length();
        } else if (payload instanceof Contract c) {
            return "合同对象 " + c.bizId();
        }
        return "不认识";
    }

    // ==========================================================
    // 5. 集合工具：List.of 一行建不可变列表，Stream.toList() 直接收成 List（不用再 collect）
    // ==========================================================
    public static List<String> sampleBizIds() {
        List<String> raw = List.of("HT-001", "HT-002", "  HT-003  ");
        return raw.stream()
                .map(String::strip)          // strip 认识 Unicode 空白，trim 只认识 ASCII
                .filter(s -> !s.isBlank())
                .toList();
    }
}
