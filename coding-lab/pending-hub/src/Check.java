import java.util.ArrayList;
import java.util.List;

/** 极简断言工具：不依赖 JUnit，直接 java 就能跑。 */
public final class Check {

    private static final List<String> SECTION_FAILS = new ArrayList<>();
    private static final List<String> ALL_FAILS = new ArrayList<>();
    private static int sectionPassed = 0;
    private static int sectionTotal = 0;
    private static int allPassed = 0;
    private static int allTotal = 0;

    private Check() {
    }

    public static void section(String name) {
        System.out.println();
        System.out.println("--- " + name + " ---");
    }

    public static void that(String desc, boolean ok) {
        sectionTotal++;
        allTotal++;
        if (ok) {
            sectionPassed++;
            allPassed++;
            System.out.println("  [PASS] " + desc);
        } else {
            SECTION_FAILS.add(desc);
            ALL_FAILS.add(desc);
            System.out.println("  [FAIL] " + desc);
        }
    }

    public static void flush() {
        System.out.println("  本节结果：" + sectionPassed + "/" + sectionTotal
                + (SECTION_FAILS.isEmpty() ? "  全部通过" : "  失败项见下"));
        for (String f : SECTION_FAILS) {
            System.out.println("    x " + f);
        }
        SECTION_FAILS.clear();
        sectionPassed = 0;
        sectionTotal = 0;
    }

    public static int summary() {
        System.out.println();
        System.out.println("=== 总判定 ===");
        System.out.println("通过 " + allPassed + " / " + allTotal + " 项");
        for (String f : ALL_FAILS) {
            System.out.println("  未通过：" + f);
        }
        return ALL_FAILS.size();
    }
}
