# -*- coding: utf-8 -*-
"""Build 简历-v25.docx.

v25 相对 v24 只删一条：外包期「深圳市融资租赁（集团） ｜ 智慧办公系统二期」里的
「会议室预约改造：…」整行去掉，该条目由 4 条变 3 条，其余一律不动。

用法：
    D:/develop/python/python.exe build_resume_v25.py
"""

import os

import build_resume_v23 as v23
import build_resume_v24 as v24

OUT_DOCX = os.path.join(os.path.dirname(os.path.abspath(__file__)), "简历-v25.docx")
DOC_TITLE = "吴枫 - Java 后端开发 - 个人简历 v25"

# v23.PROJECTS_RECENT = [融资租赁二期, 钉钉工作台, 罗格朗, 其他交付项目]
_erqi, _dd, _lgl, _other = v23.PROJECTS_RECENT

P2 = (
    _erqi[0],
    _erqi[1],
    [b for b in _erqi[2] if "会议室预约改造" not in b],
)

PROJECTS_RECENT = [
    P2,      # 融资租赁二期（删掉会议室预约那一条）
    _dd,     # 钉钉工作台
    _lgl,    # 罗格朗
    _other,  # 其他交付项目
]


def build():
    v23.PROJECTS_RECENT = PROJECTS_RECENT
    v24.OUT_DOCX = OUT_DOCX
    v24.DOC_TITLE = DOC_TITLE
    return v24.build()


if __name__ == "__main__":
    build()
