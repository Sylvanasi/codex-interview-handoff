# -*- coding: utf-8 -*-
"""Build 简历-v19.docx.

v19 相对 v18 只动项目经历 bullet 的**加粗**，不增删任何文字：
每行保留开头那个标签的加粗（位于行首，或紧跟着"："），句中/句尾的加粗全部去掉。
版式沿用 v18（教育背景在前、个人概述已删）。

用法：
    D:/develop/python/python.exe build_resume_v19.py
"""

import os
import re

import build_resume_v7 as base
import build_resume_v10 as v10
import build_resume_v17 as v17

OUT_DOCX = os.path.join(os.path.dirname(os.path.abspath(__file__)), "简历-v19.docx")
DOC_TITLE = "吴枫 - Java 后端开发 - 个人简历 v19"

EDUCATION = "2018.07 ｜ 泉州师范学院 ｜ 软件工程 ｜ 本科（2014.09 - 2018.07）"


def strip_bold(entry):
    """保留开头标签的加粗，去掉其余加粗（文字不动）。"""
    title, date, bullets = entry
    out = []
    for text in bullets:
        spans = list(re.finditer(r"<b>(.*?)</b>", text, re.S))
        keep_at = None
        if spans:
            first = spans[0]
            tail = text[first.end():]
            if first.start() == 0 or tail.startswith("：") or tail.startswith(":"):
                keep_at = first.start()
        rebuilt, pos = [], 0
        for m in spans:
            rebuilt.append(text[pos:m.start()])
            inner = m.group(1)
            rebuilt.append("<b>" + inner + "</b>" if m.start() == keep_at else inner)
            pos = m.end()
        rebuilt.append(text[pos:])
        out.append("".join(rebuilt))
    return (title, date, out)


PROJECTS_RECENT = [strip_bold(e) for e in v17.PROJECTS_RECENT]
PROJECTS_EARLY = [strip_bold(e) for e in v17.PROJECTS_EARLY]


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

    base.section_heading(doc, "教育背景", first=True)
    base.add_para(doc, EDUCATION, size=9.2, line=13.0)

    base.section_heading(doc, "专业技能")
    base.skills_block(doc)

    base.section_heading(doc, "工作经历")
    for title, date, desc in base.JOBS:
        base.add_header(doc, title, date)
        base.add_para(doc, desc, size=9, line=12.4, after=4)

    base.section_heading(doc, "项目经历")
    base.group_heading(doc, "2024.09 - 至今 ｜ 外包交付（承接蓝凌生态交付项目）")
    for title, date, bullets in PROJECTS_RECENT:
        base.add_header(doc, title, date)
        base.bullets_block(doc, bullets)

    base.group_heading(doc, "2020.06 - 2024.09 ｜ 蓝凌软件交付部")
    for title, date, bullets in PROJECTS_EARLY:
        base.add_header(doc, title, date)
        base.bullets_block(doc, bullets)

    doc.core_properties.title = DOC_TITLE
    doc.core_properties.author = "吴枫"
    doc.save(OUT_DOCX)
    print("[output] " + OUT_DOCX)
    return OUT_DOCX


if __name__ == "__main__":
    build()
