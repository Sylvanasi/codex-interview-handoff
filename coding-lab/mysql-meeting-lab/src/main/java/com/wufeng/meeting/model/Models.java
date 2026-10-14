package com.wufeng.meeting.model;
import java.util.*;

public final class Models {
    private Models() {}
    public record Audience(Set<Long> people, int sqlCount) {}
    public record QueryReport(Set<Long> people, int peopleCount, int sqlCount, double elapsedMs) {}
    public record Outcome(long personId, String status, int attempt) {}
    public record NotifyReport(List<Outcome> results, long success, long failed, long rejected, double elapsedMs) {}
    public static NotifyReport report(List<Outcome> results,long start) {
        return new NotifyReport(results,results.stream().filter(o->o.status().equals("SUCCESS")).count(),
            results.stream().filter(o->o.status().equals("FAILED")).count(),
            results.stream().filter(o->o.status().equals("REJECTED")).count(),(System.nanoTime()-start)/1_000_000.0);
    }
}
