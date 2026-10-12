# -*- coding: utf-8 -*-
"""Build 简历-v22.docx.

v22 相对 v21 只删一条：蓝凌期「深圳市融资租赁（集团） ｜ 智慧办公系统一期」里的
最后一条 bullet「权限与安全加固：…」整行去掉，该条目由 5 条变 4 条，其余不动。

用法：
    D:/develop/python/python.exe build_resume_v22.py
"""

import os

import build_resume_v21 as v21

OUT_DOCX = os.path.join(os.path.dirname(os.path.abspath(__file__)), "简历-v22.docx")
DOC_TITLE = "吴枫 - Java 后端开发 - 个人简历 v22"

# v21.PROJECTS_EARLY = [康佳集团, 深圳能源集团, 融资租赁一期, 深圳巴士集团]
_kj, _ny, _p1, _bus = v21.PROJECTS_EARLY

# 一期去掉「权限与安全加固」那一条，剩 4 条
P1 = (_p1[0], _p1[1], _p1[2][:4])

PROJECTS_EARLY = [
    _kj,   # 康佳集团
    _ny,   # 深圳能源集团
    P1,    # 融资租赁一期（删掉最后一条）
    _bus,  # 深圳巴士集团
]


def build():
    v21.OUT_DOCX = OUT_DOCX
    v21.DOC_TITLE = DOC_TITLE
    v21.PROJECTS_EARLY = PROJECTS_EARLY
    return v21.build()


if __name__ == "__main__":
    build()
