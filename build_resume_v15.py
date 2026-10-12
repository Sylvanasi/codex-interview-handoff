# -*- coding: utf-8 -*-
"""Build 简历-v15.docx.

v15 相对 v14 只改「其他交付项目」这一条：标题去掉后缀，3 条 bullet 重排
（飞的压成一条流水账、新增深铁组织同步、深圳巴士督办保留）。其余一律不动。

用法：
    D:/develop/python/python.exe build_resume_v15.py
"""

import os

import build_resume_v7 as base
import build_resume_v10 as v10
import build_resume_v11 as v11
import build_resume_v14 as v14

OUT_DOCX = os.path.join(os.path.dirname(os.path.abspath(__file__)), "简历-v15.docx")
DOC_TITLE = "吴枫 - Java 后端开发 - 个人简历 v15"

OTHER_DELIVERIES = (
    "其他交付项目",
    "",
    [
        "负责<b>飞的数字化 · 费控与相对方模块</b>：合同变更带入费控台账、付款权限过滤与金额校验、"
        "通用付款关联采购事前申请、相对方变更与 EOP 同步、费控与财务工作台页面组件开发",
        "<b>深圳地铁运营 · 组织架构与人员同步</b>：组织与人员主数据由大数据平台统一维护，"
        "基于<b>分页拉取 + 按 lastUpdateTime 增量同步</b>拉到 OA；"
        "全量初始化改为<b>分片 + 线程池并发同步</b>，单分片失败只重跑该片",
        "<b>深圳巴士集团 · 督办模块优化</b>：引入 sys/task 产品包定制至 ekp/task，"
        "定位并修复平台二开引入流程后重复调用 save 引发的<b>数据库锁冲突</b>，"
        "改造 DAO 层 update 解决；同时修复<b>定时任务死循环</b>问题。",
    ],
)

# 只替换 v14 外包期分组里的第四条（其他交付项目），其余保持 v14
PROJECTS_RECENT = v14.PROJECTS_RECENT[:3] + [OTHER_DELIVERIES]


def build():
    base.OUT_DOCX = OUT_DOCX
    base.DOC_TITLE = DOC_TITLE
    base.CONTACT_1 = v10.CONTACT_1
    base.SKILLS = v10.SKILLS
    base.PROJECTS_RECENT = PROJECTS_RECENT
    base.PROJECTS_EARLY = v11.PROJECTS_EARLY
    return base.build()


if __name__ == "__main__":
    build()
