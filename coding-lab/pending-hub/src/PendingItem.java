/** 一条待办。只读数据结构，不要改。 */
public final class PendingItem {

    private final String systemCode;
    private final String title;
    private final long dueAt;

    public PendingItem(String systemCode, String title, long dueAt) {
        this.systemCode = systemCode;
        this.title = title;
        this.dueAt = dueAt;
    }

    public String systemCode() {
        return systemCode;
    }

    public String title() {
        return title;
    }

    public long dueAt() {
        return dueAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PendingItem other)) {
            return false;
        }
        return dueAt == other.dueAt
                && systemCode.equals(other.systemCode)
                && title.equals(other.title);
    }

    @Override
    public int hashCode() {
        int result = systemCode.hashCode();
        result = 31 * result + title.hashCode();
        result = 31 * result + Long.hashCode(dueAt);
        return result;
    }

    @Override
    public String toString() {
        return systemCode + ":" + title;
    }
}
