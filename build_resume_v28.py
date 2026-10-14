# -*- coding: utf-8 -*-
"""Build 简历-v28.docx.

v28 相对 v27 做一次合并：
  1. 删掉蓝凌期的「深圳市融资租赁（集团） ｜ 智慧办公系统一期」整条；
  2. 把它的 4 条 bullet 整体挪到「深圳能源集团 ｜ 驻场二开交付」下面，接在
     「版本发布与现场部署」之后，合成一条 5 条的条目；
  3. 「跳转流程配置与移动端」里的「中集集成阶段」四个字删掉。

其余条目、技能栏、工作经历、教育背景一律不动。

用法：
    D:/develop/python/python.exe build_resume_v28.py
"""

import os

import build_resume_v27 as v27

OUT_DOCX = os.path.join(os.path.dirname(os.path.abspath(__file__)), "简历-v28.docx")
DOC_TITLE = "吴枫 - Java 后端开发 - 个人简历 v28"

# v27.PROJECTS_EARLY = [康佳集团（空）, 深圳能源集团, 融资租赁一期, 深圳巴士集团]
_kj, _ny, _p1, _bus = v27.PROJECTS_EARLY

# 一期那 4 条整体搬过来，顺手删掉「中集集成阶段」
_p1_bullets = [b.replace("中集集成阶段", "") for b in _p1[2]]

# 能源：只留「版本发布与现场部署」，后面接一期的 4 条
NY = (
    _ny[0],
    _ny[1],
    [_ny[2][0]] + _p1_bullets,
)

PROJECTS_EARLY = [
    _kj,
    NY,
    _bus,
]


def build():
    v27.OUT_DOCX = OUT_DOCX
    v27.DOC_TITLE = DOC_TITLE
    v27.PROJECTS_EARLY = PROJECTS_EARLY
    return v27.build()


if __name__ == "__main__":
    build()
