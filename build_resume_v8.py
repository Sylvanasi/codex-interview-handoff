# -*- coding: utf-8 -*-
"""Build 简历-v8.docx.

v8 相对 v7 的唯一改动：重写「专业技能」栏，按 10 组排布，把并发 / Redis / MySQL / Spring
四组提到前面并写到能承接追问的细节层级。

排版函数、个人概述、工作经历、项目经历、教育背景全部直接复用 build_resume_v7（逐字不变）。

用法：
    D:/develop/python/python.exe build_resume_v8.py
"""

import os

import build_resume_v7 as base

OUT_DOCX = os.path.join(os.path.dirname(os.path.abspath(__file__)), "简历-v8.docx")
DOC_TITLE = "吴枫 - Java 后端开发 - 个人简历 v8"

# v8 的技能栏：10 组，按"面试先问什么"排序。
# 每一组都能落到 landray 里的真实类（锚点见 简历-v8-这版改了什么.md）。
SKILLS = [
    ("Java 并发",
     "ThreadPoolExecutor 七大参数（核心线程数、最大线程数、空闲存活时间、工作队列、线程工厂、拒绝策略）"
     "与任务提交执行流程；有界队列与拒绝策略选型；Future 超时控制与单系统失败降级；"
     "synchronized 锁粒度与锁对象分析；ConcurrentHashMap 与 AtomicLong 计数；"
     "了解 CompletableFuture 并行编排、CountDownLatch 与 AQS 原理"),
    ("缓存与 Redis",
     "token 与字典缓存的周期复用与过期策略；SETNX 原子占位 + TTL 防止重复提交与重复推送；"
     "Lua 脚本保证「比对 + 删除」原子性、避免 TOCTOU 误删；空值缓存防穿透；"
     "了解穿透 / 击穿 / 雪崩的区别与对策、分布式锁续期机制"),
    ("数据库",
     "MySQL / Oracle / SQL Server；覆盖索引与最左匹配、会用 EXPLAIN 看 type/key/rows/Extra；"
     "多表 JOIN 去重、外键改软关联；事务隔离级别与行锁 / 间隙锁基础；"
     "批量插入与更新、中间库多数据源配置"),
    ("Spring 体系",
     "Spring Boot / Spring MVC / MyBatis（XML Mapper、#{} 与 ${} 的区别）；"
     "@Transactional 事务边界与失效场景（自调用、异常被吞、非 public 方法）；"
     "Bean 生命周期与循环依赖；JSP 与 JSTL"),
    ("定时任务与批处理",
     "Quartz 与平台定时任务配置；多实例部署下重复执行的防重；失败重试、补偿与告警；"
     "批处理任务幂等与断点续跑"),
    ("接口集成与故障处理",
     "HTTP 客户端连接超时与读超时设置；异常翻译（可重试 / 不可重试分类）；"
     "有限重试与退避；熔断与降级；回执状态轮询"),
    ("接口幂等与数据一致性",
     "业务单号贯穿全链路作为幂等键与对账键；Redis 占位 + 数据库唯一索引两层防重；"
     "超时按未知状态处理、先查单再决定是否重试；对账任务兜底漏推数据"),
    ("企业系统集成",
     "金蝶云星瀚（REST 接口、multipart/form-data 多附件上传、token 周期性缓存与鉴权）、"
     "SAP（XML 凭证生成、SFTP 上传、回执状态轮询）、钉钉开放平台（待办推送与审批状态回调）、"
     "IDaaS 单点登录（过滤器拦截与 token 解析）、HTTP Basic 认证、EOP 供应商主数据同步"),
    ("蓝凌 EKP 平台二次开发",
     "表单建模、LBPM 流程引擎事件扩展（IEventListener / EndedTaskEvent）、门户组件 Portlet、"
     "组织架构与权限模型"),
    ("工程与交付",
     "Linux、Git、Maven、SFTP、Docker 基础使用；版本发布与补丁包交付；"
     "需求说明书、开发说明文档、工作量评估与验收交付物输出"),
]


def build():
    base.OUT_DOCX = OUT_DOCX
    base.DOC_TITLE = DOC_TITLE
    base.SKILLS = SKILLS
    return base.build()


if __name__ == "__main__":
    build()
