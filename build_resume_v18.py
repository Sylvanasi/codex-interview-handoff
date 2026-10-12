# -*- coding: utf-8 -*-
"""Build 简历-v18.docx.

v18 相对 v17 的结构调整：
1. 「教育背景」从最后挪到最前（姓名/联系方式下面、专业技能上面）。
2. 删除「个人概述」整节。
其余（技能栏 7 组、工作经历、项目经历）保持 v17 原样。

排版函数全部复用 build_resume_v7，所以视觉与前几版一致。

用法：
    D:/develop/python/python.exe build_resume_v18.py
"""

import os

import build_resume_v7 as base
import build_resume_v10 as v10
import build_resume_v17 as v17

OUT_DOCX = os.path.join(os.path.dirname(os.path.abspath(__file__)), "简历-v18.docx")
DOC_TITLE = "吴枫 - Java 后端开发 - 个人简历 v18"

EDUCATION = "2018.07 ｜ 泉州师范学院 ｜ 软件工程 ｜ 本科（2014.09 - 2018.07）"


def build():
    base.SKILLS = v10.SKILLS          # skills_block 读的是 v7 模块里的全局 SKILLS

    doc = base.Document()
    base.setup_page(doc)
    base.lighten_title_style(doc)

    name = base.add_para(doc, base.NAME, size=20.5, bold=True, line=24, after=1.5, color=base.NAVY)
    name.style = doc.styles["Title"]
    for run in name.runs:
        base.set_run(run, 20.5, True, base.NAVY)

    base.add_para(doc, v10.CONTACT_1, size=9.2, line=12.6, after=2, color=base.GRAY)

    # ① 教育背景提到最前
    base.section_heading(doc, "教育背景", first=True)
    base.add_para(doc, EDUCATION, size=9.2, line=13.0)

    # ② 个人概述整节删除

    base.section_heading(doc, "专业技能")
    base.skills_block(doc)

    base.section_heading(doc, "工作经历")
    for title, date, desc in base.JOBS:
        base.add_header(doc, title, date)
        base.add_para(doc, desc, size=9, line=12.4, after=4)

    base.section_heading(doc, "项目经历")
    base.group_heading(doc, "2024.09 - 至今 ｜ 外包交付（承接蓝凌生态交付项目）")
    for title, date, bullets in v17.PROJECTS_RECENT:
        base.add_header(doc, title, date)
        base.bullets_block(doc, bullets)

    base.group_heading(doc, "2020.06 - 2024.09 ｜ 蓝凌软件交付部")
    for title, date, bullets in v17.PROJECTS_EARLY:
        base.add_header(doc, title, date)
        base.bullets_block(doc, bullets)

    doc.core_properties.title = DOC_TITLE
    doc.core_properties.author = "吴枫"
    doc.save(OUT_DOCX)
    print("[output] " + OUT_DOCX)
    return OUT_DOCX


if __name__ == "__main__":
    build()
