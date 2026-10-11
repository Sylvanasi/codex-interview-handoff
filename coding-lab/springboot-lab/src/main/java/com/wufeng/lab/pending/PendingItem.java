package com.wufeng.lab.pending;

/**
 * 一条待办。
 *
 * 用 record 而不是手写类：record 天生不可变、equals/hashCode/toString 自动生成，
 * 适合这种"只有数据、没有行为"的载体。（JDK 16 转正，面试问"Java 17 新特性"可以拿它举例。）
 */
public record PendingItem(String system, String id, String title, String url) {
}
