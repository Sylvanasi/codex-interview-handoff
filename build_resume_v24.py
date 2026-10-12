# -*- coding: utf-8 -*-
"""Build 简历-v24.docx.

v24 相对 v23：把蓝凌期的「康佳集团 ｜ 集团门户与多系统集成」与
「深圳能源集团 ｜ 驻场二开交付」两条的内容全部清空，只留标题抬头，
留出位置等后面统一重写。其余条目、技能栏、工作经历、教育背景一律不动。

顺带效果：全篇 {待补} 由 2 处降为 0 处（那两处都在这两条被清掉的 bullet 上）。

用法：
    D:/develop/python/python.exe build_resume_v24.py
"""

import os

import build_resume_v22 as v22
import build_resume_v23 as v23

OUT_DOCX = os.path.join(os.path.dirname(os.path.abspath(__file__)), "简历-v24.docx")
DOC_TITLE = "吴枫 - Java 后端开发 - 个人简历 v24"

# v22.PROJECTS_EARLY = [康佳集团, 深圳能源集团, 融资租赁一期, 深圳巴士集团]
_kj, _ny, _p1, _bus = v22.PROJECTS_EARLY

# 康佳、能源只留抬头，内容等后面重写
PROJECTS_EARLY = [
    (_kj[0], _kj[1], []),   # 康佳集团（内容清空）
    (_ny[0], _ny[1], []),   # 深圳能源集团（内容清空）
    _p1,                    # 融资租赁一期
    _bus,                   # 深圳巴士集团
]


def build():
    v23.OUT_DOCX = OUT_DOCX
    v23.DOC_TITLE = DOC_TITLE
    v22.PROJECTS_EARLY = PROJECTS_EARLY
    return v23.build()


if __name__ == "__main__":
    build()
