# -*- coding: utf-8 -*-
"""Build 简历-v29.docx.

v29 相对 v28 只改一条：「跳转流程接入」掐掉结尾的业务字段清单，
改成到「hibernate、spring、plugin 配置」就收尾。其余一律不动。

用法：
    D:/develop/python/python.exe build_resume_v29.py
"""

import os

import build_resume_v28 as v28

OUT_DOCX = os.path.join(os.path.dirname(os.path.abspath(__file__)), "简历-v29.docx")
DOC_TITLE = "吴枫 - Java 后端开发 - 个人简历 v29"

NEW_JUMP = (
    "<b>跳转流程接入</b>：第三方业务系统（租前）发起的审批流程接入 OA，"
    "新建 third/process 插件包，按 EKP 规范分层实现 Action / Service / DAO / Model / Form "
    "与 hibernate、spring、plugin 配置"
)

# v28.PROJECTS_EARLY = [康佳集团（空）, 深圳能源集团, 深圳巴士集团]
_kj, _ny, _bus = v28.PROJECTS_EARLY

NY = (
    _ny[0],
    _ny[1],
    [NEW_JUMP if b.startswith("<b>跳转流程接入</b>") else b for b in _ny[2]],
)

PROJECTS_EARLY = [
    _kj,
    NY,
    _bus,
]


def build():
    v28.OUT_DOCX = OUT_DOCX
    v28.DOC_TITLE = DOC_TITLE
    v28.PROJECTS_EARLY = PROJECTS_EARLY
    return v28.build()


if __name__ == "__main__":
    build()
