# -*- coding: utf-8 -*-
"""Build 简历-v14.docx.

v14 相对 v13 只改「罗格朗（惠州）」这一条：标题换成「OA 预算与事前费用申请」，
6 条 bullet 收敛成 4 条（删 A8 / CRM，凭证归档挪到最后）。其余一律不动。

用法：
    D:/develop/python/python.exe build_resume_v14.py
"""

import os

import build_resume_v7 as base
import build_resume_v10 as v10
import build_resume_v11 as v11
import build_resume_v13 as v13

OUT_DOCX = os.path.join(os.path.dirname(os.path.abspath(__file__)), "简历-v14.docx")
DOC_TITLE = "吴枫 - Java 后端开发 - 个人简历 v14"

LEGRAND = (
    "罗格朗（惠州） ｜ OA 预算与事前费用申请",
    "",
    [
        "负责<b>成本中心模块</b>：成本中心模块开发、包括页面与后端，并设计<b>大 / 小管理员二级权限</b>。",
        "负责<b>费用事前申请单</b>：按经办人带出成本中心与预算数据，"
        "并实现批准金额不高于申请金额、报销金额不高于批准金额的<b>逐级约束校验</b>。",
        "负责<b>预算占用与释放的跨系统协同</b>：审批通过时本地记录预算占用并推送预算系统，"
        "<b>推送失败回滚本地占用</b>；驳回与废弃状态分别触发对应释放调用；"
        "<b>释放调用失败落表</b>，由<b>定时任务</b>扫描未成功的记录补偿重推，超阈值转人工",
        "负责<b>报销凭证归档链路</b>：流程结束监听器生成 <b>XML</b> 凭证文件，"
        "经自研 <b>SFTP 工具</b>上传至指定服务器。",
    ],
)

# 只替换 v13 外包期分组里的第三条（罗格朗），其余保持 v13
PROJECTS_RECENT = v13.PROJECTS_RECENT[:2] + [LEGRAND] + v13.PROJECTS_RECENT[3:]


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
