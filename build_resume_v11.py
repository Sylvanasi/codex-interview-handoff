# -*- coding: utf-8 -*-
"""Build 简历-v11.docx.

v11 相对 v10 只做结构性整理，不动任何一条 bullet 的正文：

1. 深圳巴士整条（标题 + 4 条 bullet）从「外包交付」分组挪到「蓝凌软件交付部」分组，排在该组最前。
   依据：szbus/archive/listener 下 8 个 SzbusArchive*Listener 的 javadoc 日期是 2020-12 ~ 2021-05。
2. 项目经历所有条目标题去掉日期，只留「客户 ｜ 项目名」；两个分组大标题的时间保留。

头部、概述、技能栏 7 组、工作经历、教育背景全部沿用 v10。

用法：
    D:/develop/python/python.exe build_resume_v11.py
"""

import os

import build_resume_v7 as base
import build_resume_v9 as v9
import build_resume_v10 as v10

OUT_DOCX = os.path.join(os.path.dirname(os.path.abspath(__file__)), "简历-v11.docx")
DOC_TITLE = "吴枫 - Java 后端开发 - 个人简历 v11"


def no_date(entry):
    """条目去日期：只保留标题与 bullet。"""
    title, _date, bullets = entry
    return (title, "", bullets)


# 深圳巴士：v9 重写过的标题与 4 条 bullet，正文一字不动
SHENZHEN_BUS = (
    "深圳巴士集团 ｜ OA 合同归档与采购附件对接",
    "",
    v9.PROJECTS_RECENT[0][2],
)

# 外包期分组：去掉深圳巴士，其余原序（标题去日期）
PROJECTS_RECENT = [
    no_date(v9.PROJECTS_RECENT[1]),   # 深圳市融资租赁（集团） ｜ 智慧办公系统二期
    no_date(v9.PROJECTS_RECENT[2]),   # 深圳市融资租赁（集团） ｜ OA 待办推送钉钉工作台
    no_date(v9.PROJECTS_RECENT[3]),   # 罗格朗（惠州） ｜ OA 费控与多系统集成
    no_date(v9.PROJECTS_RECENT[4]),   # 其他交付项目 ｜ EOP 主数据与合同费控
]

# 蓝凌期分组：深圳巴士排最前，其后原序（标题去日期）
PROJECTS_EARLY = [
    SHENZHEN_BUS,                     # 深圳巴士集团 ｜ OA 合同归档与采购附件对接
    no_date(base.PROJECTS_EARLY[0]),  # 深圳能源集团 ｜ 协同办公与合同管理系统
    no_date(base.PROJECTS_EARLY[1]),  # 康佳集团 ｜ 集团门户与多系统集成
    no_date(base.PROJECTS_EARLY[2]),  # 深圳市融资租赁（集团） ｜ 智慧办公系统一期
]


def build():
    base.OUT_DOCX = OUT_DOCX
    base.DOC_TITLE = DOC_TITLE
    base.CONTACT_1 = v10.CONTACT_1
    base.SKILLS = v10.SKILLS
    base.PROJECTS_RECENT = PROJECTS_RECENT
    base.PROJECTS_EARLY = PROJECTS_EARLY
    return base.build()


if __name__ == "__main__":
    build()
