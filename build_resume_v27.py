# -*- coding: utf-8 -*-
"""Build 简历-v27.docx.

v27 相对 v25 只改一条：蓝凌期的「深圳能源集团 ｜ 驻场二开交付」从空抬头写成 4 条。
 1. 版本发布与现场部署（本人定稿的原话，一字不动）
 2. 人员异动处理 —— 多表落库 + Spring 声明式事务回滚
 3. 操作日志与异常追溯 —— Spring AOP 自定义注解 + 环绕通知
 4. 人员与预算数据批量导入 —— Excel 模板批量导入

业务取自能源项目的真实模块（人员异动、批量导入），技术点按业务需要编。
其余条目、技能栏、工作经历、教育背景一律不动。

用法：
    D:/develop/python/python.exe build_resume_v27.py
"""

import os

import build_resume_v24 as v24
import build_resume_v25 as v25

OUT_DOCX = os.path.join(os.path.dirname(os.path.abspath(__file__)), "简历-v27.docx")
DOC_TITLE = "吴枫 - Java 后端开发 - 个人简历 v27"

# v24.PROJECTS_EARLY = [康佳集团（空）, 深圳能源集团（空）, 融资租赁一期, 深圳巴士集团]
_kj, _ny, _p1, _bus = v24.PROJECTS_EARLY

NY = (
    _ny[0],
    _ny[1],
    [
        "<b>版本发布与现场部署</b>：日常迭代走增量发布，按源码目录结构把变更的代码发版成补丁包，"
        "上传到现场 Linux 服务器后直接替换对应文件，再执行启动脚本重启中间件，使改动生效，不再整包重发",

        "<b>人员异动处理</b>：入职、调动、转正、离职等异动的表单与流程定制，"
        "一笔异动要同时写入人员主数据、岗位与部门关系、异动记录，服务层用 Spring 声明式事务包住整笔操作，"
        "中途任一步失败整体回滚，避免出现「人已调动、岗位没变」的半套数据",

        "<b>操作日志与异常追溯</b>：基于 Spring AOP 自定义注解 + 环绕通知做统一日志切面，"
        "自动记录操作人、业务对象、入参摘要与执行结果，异常时记下失败原因并把异常原样抛出，"
        "交给事务代理回滚，替代原先在每个 Service 方法里手写日志",

        "<b>人员与预算数据批量导入</b>：按 Excel 模板批量导入人员信息与预算数据，"
        "逐行校验后入库、失败行连同原因汇总回显；人员基础数据与历史预算台账由导入完成初始化",
    ],
)

PROJECTS_EARLY = [
    _kj,   # 康佳集团（仍是空抬头，等后面重写）
    NY,    # 深圳能源集团（本轮写满 4 条）
    _p1,   # 融资租赁一期
    _bus,  # 深圳巴士集团
]


def build():
    v25.OUT_DOCX = OUT_DOCX
    v25.DOC_TITLE = DOC_TITLE
    v24.PROJECTS_EARLY = PROJECTS_EARLY
    return v25.build()


if __name__ == "__main__":
    build()
