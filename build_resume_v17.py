# -*- coding: utf-8 -*-
"""Build 简历-v17.docx.

v17 相对 v16 是纯机械改动：把项目经历里所有 bullet 开头的「负责」二字删掉，
其余一个字不动（标点、加粗、数字占位都保持）。

用法：
    D:/develop/python/python.exe build_resume_v17.py
"""

import os

import build_resume_v7 as base
import build_resume_v10 as v10
import build_resume_v11 as v11
import build_resume_v15 as v15
import build_resume_v16 as v16

OUT_DOCX = os.path.join(os.path.dirname(os.path.abspath(__file__)), "简历-v17.docx")
DOC_TITLE = "吴枫 - Java 后端开发 - 个人简历 v17"


def strip_fuze(entry):
    """去掉该条目里每条 bullet 行首的「负责」。"""
    title, date, bullets = entry
    return (title, date,
            [b[2:].lstrip() if b.startswith("负责") else b for b in bullets])


PROJECTS_RECENT = [strip_fuze(e) for e in v15.PROJECTS_RECENT]
PROJECTS_EARLY = [strip_fuze(e) for e in v16.PROJECTS_EARLY]


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
