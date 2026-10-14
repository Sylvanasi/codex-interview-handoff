# -*- coding: utf-8 -*-
"""Build 简历-v30.docx.

v30 相对 v29 只加一条：外包期「其他交付项目」里新增
「深圳地铁运营 · 竹云单点登录」，插在「深圳地铁运营 · 组织架构与人员同步」之后。
其余一律不动。

依据：shentie 仓库 src/com/landray/kmss/szmc/sso/
  TbsiSSOOauthFilter（authorize → getToken → getUserInfo 三段、PC/移动端分流、
  findByLoginName 映射、doAutoLogin 建会话、跳回前清 state）
  TbsiSsoCfg（clientId / clientSecret / 平台地址 / 登出地址可配置）

用法：
    D:/develop/python/python.exe build_resume_v30.py
"""

import os

import build_resume_v25 as v25
import build_resume_v29 as v29

OUT_DOCX = os.path.join(os.path.dirname(os.path.abspath(__file__)), "简历-v30.docx")
DOC_TITLE = "吴枫 - Java 后端开发 - 个人简历 v30"

NEW_SSO = (
    "<b>深圳地铁运营 · 竹云单点登录</b>：对接竹云统一用户平台的 OAuth2 授权码流程，"
    "过滤器拦截未登录请求跳转授权端点，回跳后用 code 换 token、再调用户信息接口取回登录名，"
    "映射到 OA 本地账号并自动登录建立会话；PC 与移动端分流处理，"
    "OA 登出后跳转竹云主页做统一登出"
)


def insert_sso(bullets):
    """在深铁组织同步那条之后插一条。"""
    out = []
    for b in bullets:
        out.append(b)
        if "组织架构与人员同步" in b:
            out.append(NEW_SSO)
    return out


# v25.PROJECTS_RECENT = [融资租赁二期, 钉钉工作台, 罗格朗, 其他交付项目]
_recent = list(v25.PROJECTS_RECENT)
_other = _recent[3]

OTHER = (_other[0], _other[1], insert_sso(_other[2]))

PROJECTS_RECENT = [
    _recent[0],
    _recent[1],
    _recent[2],
    OTHER,
]


def build():
    v25.PROJECTS_RECENT = PROJECTS_RECENT
    v29.OUT_DOCX = OUT_DOCX
    v29.DOC_TITLE = DOC_TITLE
    return v29.build()


if __name__ == "__main__":
    build()
