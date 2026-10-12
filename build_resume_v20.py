# -*- coding: utf-8 -*-
"""Build 简历-v20.docx.

v20 相对 v19 改三处：
1. 深圳能源的条目标题 → 「深圳能源集团 ｜ 驻场二开交付」
2. 深圳巴士的条目标题 → 「深圳巴士集团 ｜ 标准模块二开定制」
3. 蓝凌期顺序调整：康佳提到深圳能源上面
4. 教育背景那一行换成「2014.09 - 2018.07 ｜ 泉州师范学院 ｜ 软件工程 ｜ 本科」

做法是覆盖 v19 模块里的常量后调用它的 build()，这样版式与文字都与 v19 一致。

用法：
    D:/develop/python/python.exe build_resume_v20.py
"""

import os

import build_resume_v19 as v19

OUT_DOCX = os.path.join(os.path.dirname(os.path.abspath(__file__)), "简历-v20.docx")
DOC_TITLE = "吴枫 - Java 后端开发 - 个人简历 v20"

EDUCATION = "2014.09 - 2018.07 ｜ 泉州师范学院 ｜ 软件工程 ｜ 本科"

# v19.PROJECTS_EARLY = [深圳能源集团, 康佳集团, 融资租赁一期, 深圳巴士集团]
_ny, _kj, _p1, _bus = v19.PROJECTS_EARLY

PROJECTS_EARLY = [
    _kj,                                                    # 康佳集团（提到最前）
    ("深圳能源集团 ｜ 驻场二开交付", _ny[1], _ny[2]),        # 换标题
    _p1,                                                    # 融资租赁一期（不动）
    ("深圳巴士集团 ｜ 标准模块二开定制", _bus[1], _bus[2]),  # 换标题
]


def build():
    v19.OUT_DOCX = OUT_DOCX
    v19.DOC_TITLE = DOC_TITLE
    v19.EDUCATION = EDUCATION
    v19.PROJECTS_EARLY = PROJECTS_EARLY
    return v19.build()


if __name__ == "__main__":
    build()
