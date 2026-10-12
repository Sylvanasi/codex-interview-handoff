# -*- coding: utf-8 -*-
"""Build 简历-v13.docx.

v13 相对 v12 只改「深圳市融资租赁（集团） ｜ OA 待办推送钉钉工作台」这一条：
SSO 那条提到最前并扩写，中间保持原文，最后一条删掉日均数字。其余一律不动。

用法：
    D:/develop/python/python.exe build_resume_v13.py
"""

import os

import build_resume_v7 as base
import build_resume_v10 as v10
import build_resume_v11 as v11
import build_resume_v12 as v12

OUT_DOCX = os.path.join(os.path.dirname(os.path.abspath(__file__)), "简历-v13.docx")
DOC_TITLE = "吴枫 - Java 后端开发 - 个人简历 v13"

DINGTALK = (
    "深圳市融资租赁（集团） ｜ OA 待办推送钉钉工作台",
    "",
    [
        "负责 <b>IDaaS 单点登录（OAuth2 授权码）对接</b>：基于<b>过滤器</b>拦截未登录请求，"
        "302 跳转 IDaaS 授权端点并携带 state 防 CSRF；用户认证后回跳带 code，"
        "由<b>后端</b>用 code + client_secret 换取 token 并取回用户身份，"
        "完成 IDaaS 账号到 OA 本地账号的<b>映射</b>与本地会话建立；"
        "回调地址走<b>白名单</b>校验，票据<b>一次性消费</b>防重放",
        "负责 OA 待办与钉钉工作台集成：基于<b>钉钉开放平台待办推送</b>与<b>审批完成状态回调</b>，"
        "实现待办推送、点击跳转 OA 原审批表单、审批完成后回调同步 OA，"
        "解决用户在钉钉内无法处理 OA 审批的问题。",
        "基于<b>幂等键校验</b>避免同一待办重复推送，降低钉钉侧重复待办与用户投诉。",
    ],
)

# 只替换 v12 外包期分组里的第二条（钉钉），其余保持 v12
PROJECTS_RECENT = [v12.PROJECTS_RECENT[0], DINGTALK] + v12.PROJECTS_RECENT[2:]


def build():
    base.OUT_DOCX = OUT_DOCX
    base.DOC_TITLE = DOC_TITLE
    base.CONTACT_1 = v10.CONTACT_1
    base.SKILLS = v10.SKILLS
    base.PROJECTS_RECENT = PROJECTS_RECENT
    base.PROJECTS_EARLY = v11.PROJECTS_EARLY
    return base.build()


if __name__ == "__main__":
    build()
