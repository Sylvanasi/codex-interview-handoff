# -*- coding: utf-8 -*-
"""Build 简历-v21.docx.

v21 相对 v20 只改一条：蓝凌期的「深圳市融资租赁（集团） ｜ 智慧办公系统一期」。
3 条 bullet 换成 5 条「加粗标签：功能清单」流水账，内容取自 zj 仓库一期窗口
（2023-12 ~ 2024-09）真实存在的开发：
  跳转流程接入（租前）/ 跳转流程配置与移动端（中集集成）/ 企业公文 /
  Excel 批量导入导出（POI 工具类）/ 权限与安全加固。
同时删掉原来的 LBPM 会签加签、公文与会议管理、线程池并发分片三条。

做法是覆盖 v20 模块里的常量后调用它的 build()，版式与其余条目与 v20 一致。

用法：
    D:/develop/python/python.exe build_resume_v21.py
"""

import os

import build_resume_v20 as v20

OUT_DOCX = os.path.join(os.path.dirname(os.path.abspath(__file__)), "简历-v21.docx")
DOC_TITLE = "吴枫 - Java 后端开发 - 个人简历 v21"

# v20.PROJECTS_EARLY = [康佳集团, 深圳能源集团, 融资租赁一期, 深圳巴士集团]
_kj, _ny, _p1, _bus = v20.PROJECTS_EARLY

P1 = (
    "深圳市融资租赁（集团） ｜ 智慧办公系统一期",
    _p1[1],
    [
        "<b>跳转流程接入</b>：第三方业务系统（租前）发起的审批流程接入 OA，"
        "新建 third/process 插件包，按 EKP 规范分层实现 Action / Service / DAO / Model / Form "
        "与 hibernate、spring、plugin 配置，承接项目名称、客户名称、金额与币种、业务经理、"
        "信审经理、流程节点名称等业务字段",
        "<b>跳转流程配置与移动端</b>：跳转流程与 OA 流程的关联配置、列表与编辑页、"
        "默认角色与后台设置角色；中集集成阶段补充移动端视图，支持移动端查看与办理",
        "<b>企业公文</b>：企业公文发文拟稿、拟稿时间可编辑与公文管理标签，"
        "并参与公文管理与永中在线预览的集成升级",
        "<b>Excel 批量导入导出</b>：基于 POI 封装通用导入导出工具类，"
        "实现模板生成、表头样式与行级校验，支撑业务数据批量导入与台账导出",
        "<b>权限与安全加固</b>：跳转流程模块权限设计调整（权限放宽至全员）、"
        "密码找回与用户校验链路加固，跟进修复平台安全漏洞",
    ],
)

PROJECTS_EARLY = [
    _kj,   # 康佳集团
    _ny,   # 深圳能源集团
    P1,    # 融资租赁一期（本轮重写）
    _bus,  # 深圳巴士集团
]


def build():
    v20.OUT_DOCX = OUT_DOCX
    v20.DOC_TITLE = DOC_TITLE
    v20.PROJECTS_EARLY = PROJECTS_EARLY
    return v20.build()


if __name__ == "__main__":
    build()
