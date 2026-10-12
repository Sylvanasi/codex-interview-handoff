# -*- coding: utf-8 -*-
"""Build 简历-v16.docx.

v16 相对 v15 只改「深圳巴士集团」这一条：按你 2020-2026 的真实提交日志重写成五条流水账
（合同 / 评审 / 督办 / 网上通知 / 档案集成），标题随之调整，并把整条挪到蓝凌期分组最后一位。
其余一律不动。

用法：
    D:/develop/python/python.exe build_resume_v16.py
"""

import os

import build_resume_v7 as base
import build_resume_v10 as v10
import build_resume_v11 as v11
import build_resume_v15 as v15

OUT_DOCX = os.path.join(os.path.dirname(os.path.abspath(__file__)), "简历-v16.docx")
DOC_TITLE = "吴枫 - Java 后端开发 - 个人简历 v16"

SHENZHEN_BUS = (
    "深圳巴士集团 ｜ OA 合同、评审与督办模块开发",
    "",
    [
        "负责<b>合同管理</b>：合同起草表单与流程定制、合同变更、合同台账查询与详情页、"
        "合同附件上传下载，并参与台账历史数据迁移与数据修复",
        "负责<b>评审管理</b>：评审数据汇总（迭代了四版）、评审暂存、评分统计与评分异常追踪，"
        "以及评审数据的 <b>Excel 导出</b>",
        "负责<b>督办管理</b>：督办数据导入、督办统计报表、督办待办定制；后续参与新版督办管理的开发",
        "负责<b>网上通知</b>：通知发布与展示功能的 PC 端、移动端开发与缺陷修复",
        "负责<b>档案集成</b>：归档流程挂接与归档中间表写入，以及归档附件下载问题修复",
    ],
)

# 蓝凌期分组：深圳巴士从第一位挪到最后一位
# v11.PROJECTS_EARLY = [深圳巴士, 深圳能源, 康佳, 融资租赁一期]
PROJECTS_EARLY = [
    v11.PROJECTS_EARLY[1],   # 深圳能源集团
    v11.PROJECTS_EARLY[2],   # 康佳集团
    v11.PROJECTS_EARLY[3],   # 深圳市融资租赁（集团）｜智慧办公系统一期
    SHENZHEN_BUS,            # 深圳巴士集团（挪到最后）
]


def build():
    base.OUT_DOCX = OUT_DOCX
    base.DOC_TITLE = DOC_TITLE
    base.CONTACT_1 = v10.CONTACT_1
    base.SKILLS = v10.SKILLS
    base.PROJECTS_RECENT = v15.PROJECTS_RECENT
    base.PROJECTS_EARLY = PROJECTS_EARLY
    return base.build()


if __name__ == "__main__":
    build()
