package com.wufeng.lab.dto;

/**
 * 换 token 接口的返回体。
 *
 * 用 record 而不是普通的 class：record 是 Java 16 正式引入的不可变数据载体，
 * 编译器自动生成全参构造、访问器（名字就是字段名，没有 get 前缀）、equals、hashCode、toString。
 * 它适合 DTO（只用来传数据、不该被修改的对象）。
 *
 * 面试点：record 不能继承别的类、字段都是 final，所以不适合做实体类（Entity）。
 */
public record TokenView(String token, long expiresIn) {
}
