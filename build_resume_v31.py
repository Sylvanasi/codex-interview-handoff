# -*- coding: utf-8 -*-
"""Build 简历-v31.docx.

v31 相对 v30 只改一条：竹云单点登录那条去掉「PC 与移动端分流处理」这半句。

用法：
    D:/develop/python/python.exe build_resume_v31.py
"""

import os

import build_resume_v30 as v30

OUT_DOCX = os.path.join(os.path.dirname(os.path.abspath(__file__)), "简历-v31.docx")
DOC_TITLE = "吴枫 - Java 后端开发 - 个人简历 v31"

NEW_SSO = (
    "<b>深圳地铁运营 · 竹云单点登录</b>：对接竹云统一用户平台的 OAuth2 授权码流程，"
    "过滤器拦截未登录请求跳转授权端点，回跳后用 code 换 token、再调用户信息接口取回登录名，"
    "映射到 OA 本地账号并自动登录建立会话；OA 登出后跳转竹云主页做统一登出"
)

# v30.PROJECTS_RECENT = [融资租赁二期, 钉钉工作台, 罗格朗, 其他交付项目]
_recent = list(v30.PROJECTS_RECENT)
_other = _recent[3]

OTHER = (
    _other[0],
    _other[1],
    [NEW_SSO if "竹云单点登录" in b else b for b in _other[2]],
)

PROJECTS_RECENT = [
    _recent[0],
    _recent[1],
    _recent[2],
    OTHER,
]


def build():
    v30.PROJECTS_RECENT = PROJECTS_RECENT
    v30.OUT_DOCX = OUT_DOCX
    v30.DOC_TITLE = DOC_TITLE
    return v30.build()


if __name__ == "__main__":
    build()
