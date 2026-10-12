# -*- coding: utf-8 -*-
"""Build 简历-v12.docx.

v12 相对 v11 只改「深圳市融资租赁（集团） ｜ 智慧办公系统二期」这一条的 4 条 bullet，
其余一律不动。

用法：
    D:/develop/python/python.exe build_resume_v12.py
"""

import os

import build_resume_v7 as base
import build_resume_v10 as v10
import build_resume_v11 as v11

OUT_DOCX = os.path.join(os.path.dirname(os.path.abspath(__file__)), "简历-v12.docx")
DOC_TITLE = "吴枫 - Java 后端开发 - 个人简历 v12"

RENTAL_PHASE2 = (
    "深圳市融资租赁（集团） ｜ 智慧办公系统二期",
    "",
    [
        "改造<b>会议通知功能</b>：单场会议参会人可达上百人，改为<b>线程池并发下发</b>通知"
        "（有界队列 + 拒绝策略 + 单条失败隔离重试），替代原串行逐个推送",
        "负责<b>门户模块</b>开发：实现工作安排与上会安排的门户展示、领导批示定制与公文报表",
        "负责<b>议题管理模块</b>：实现议题申请、上会安排与公文纪要的<b>跨模块数据联动</b>；"
        "议题列表按「状态 + 时间」分页查询走<b>联合索引</b>，消除原全表扫描与额外排序",
        "负责<b>会议室预约改造</b>：实现会议室保管人多选与会议信息传阅；"
        "针对<b>同一时段并发抢同一间会议室</b>，用 <b>Redis SETNX 占位 + 过期时间</b>做分布式防重，"
        "替代原单机锁，避免多节点部署下重复预约",
    ],
)

# 只替换 v11 外包期分组里的第一条（融资租赁二期），其余保持 v11
PROJECTS_RECENT = [RENTAL_PHASE2] + v11.PROJECTS_RECENT[1:]


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
