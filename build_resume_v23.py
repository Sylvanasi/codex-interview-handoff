# -*- coding: utf-8 -*-
"""Build 简历-v23.docx.

v23 相对 v22 只动一处加粗：外包期「深圳市融资租赁（集团） ｜ OA 待办推送钉钉工作台」里
那条「OA 待办与钉钉工作台集成：…」给开头的标题加粗（v19 洗句中加粗时，这条整行
一个加粗都不剩，现在把行首标签补回来）。文字一个字不动。

用法：
    D:/develop/python/python.exe build_resume_v23.py
"""

import os

import build_resume_v19 as v19
import build_resume_v22 as v22

OUT_DOCX = os.path.join(os.path.dirname(os.path.abspath(__file__)), "简历-v23.docx")
DOC_TITLE = "吴枫 - Java 后端开发 - 个人简历 v23"

TARGET = "OA 待办与钉钉工作台集成"


def bold_label(entry):
    """给行首标签（"标题：正文" 里的标题）补上加粗，其余文字不动。"""
    title, date, bullets = entry
    out = []
    for text in bullets:
        if text.startswith(TARGET) and "<b>" not in text:
            head, sep, tail = text.partition("：")
            text = "<b>" + head + "</b>" + sep + tail
        out.append(text)
    return (title, date, out)


# v19.PROJECTS_RECENT = [融资租赁二期, 钉钉工作台, 罗格朗, 其他交付项目]
PROJECTS_RECENT = list(v19.PROJECTS_RECENT)
PROJECTS_RECENT[1] = bold_label(v19.PROJECTS_RECENT[1])


def build():
    v19.PROJECTS_RECENT = PROJECTS_RECENT
    v22.OUT_DOCX = OUT_DOCX
    v22.DOC_TITLE = DOC_TITLE
    return v22.build()


if __name__ == "__main__":
    build()
