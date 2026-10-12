# -*- coding: utf-8 -*-
"""Build 简历-v10.docx.

v10 相对 v9 的唯一改动：重写「专业技能」栏 —— 10 组压成 7 组。
逐行决定见 简历-v10-这版改了什么.md。

个人概述、工作经历、项目经历、教育背景全部沿用 v9（逐字未动）。

用法：
    D:/develop/python/python.exe build_resume_v10.py
"""

import os

import build_resume_v7 as base
import build_resume_v9 as v9

OUT_DOCX = os.path.join(os.path.dirname(os.path.abspath(__file__)), "简历-v10.docx")
DOC_TITLE = "吴枫 - Java 后端开发 - 个人简历 v10"

# 第 2 行：头部联系方式（去掉"年龄 30"，补回"8 年经验"）
CONTACT_1 = "Java 后端开发 ｜ 电话 17338701320 ｜ 8 年经验"

# v10 的技能栏：10 组 → 7 组
SKILLS = [
    ("并发与锁",
     "能独立完成接口聚合场景的并发调用与超时降级；能定位并改造共享资源上的锁粒度问题"),
    ("缓存与 Redis",
     "token 与热点数据的周期复用与过期策略；SETNX 原子占位 + TTL 防止重复提交与重复推送；"
     "了解穿透 / 击穿 / 雪崩的区别与对策"),
    ("数据库",
     "MySQL / Oracle / SQL Server；熟悉 MySQL 索引原理与慢 SQL 优化"),
    ("框架",
     "Spring Boot / Spring MVC / MyBatis / Hibernate"),
    ("接口集成与故障处理",
     "能独立完成异构系统接口对接，处理超时、重试与失败降级；"
     "能设计接口幂等与对账方案，处理重复提交与漏推"),
    ("企业系统集成",
     "OAuth2 单点登录（授权码流程 + 过滤器拦截 + 票据校验与会话建立）、金蝶 sHR、致远 A8、CRM、"
     "SAP（XML 凭证 + SFTP + 回执轮询）、钉钉开放平台（待办推送与状态回调）、EOP 供应商主数据同步"),
    ("工程与交付",
     "Linux、Git、Maven、Docker；版本发布与补丁包交付；"
     "需求说明书、开发说明文档、工作量评估与验收交付物输出"),
]


def build():
    base.OUT_DOCX = OUT_DOCX
    base.DOC_TITLE = DOC_TITLE
    base.CONTACT_1 = CONTACT_1
    base.SKILLS = SKILLS
    base.PROJECTS_RECENT = v9.PROJECTS_RECENT   # v9 重写过的四条，原样带过来
    # PROJECTS_EARLY（深圳能源 / 康佳 / 融资租赁一期）与 OVERVIEW、JOBS 保持 v9 原文
    return base.build()


if __name__ == "__main__":
    build()
