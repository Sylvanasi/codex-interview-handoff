# -*- coding: utf-8 -*-
"""Build the resume PDF.

Source of truth for resume content: build_resume_docx.py imports the constants
below so the PDF and the Word file cannot drift apart.

Design is deliberately monochrome (black text, dark gray headings, light gray
rules) and uses restrained emphasis: bold marks structure and a single short
keyword per bullet, never long descriptive clauses.
"""

import os

from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT, TA_RIGHT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    BaseDocTemplate,
    Frame,
    KeepTogether,
    PageTemplate,
    Paragraph,
    Spacer,
    Table,
    TableStyle,
)

OUT_DIR = os.path.dirname(os.path.abspath(__file__))
BUILD_DIR = os.path.join(OUT_DIR, ".build")
OUT_PDF = os.path.join(OUT_DIR, "吴枫-Java开发工程师-简历.pdf")

FONT_CANDIDATES = [
    ("MSYH", r"C:\Windows\Fonts\msyh.ttc", "MSYH-Bold", r"C:\Windows\Fonts\msyhbd.ttc"),
    ("Deng", r"C:\Windows\Fonts\Deng.ttf", "Deng-Bold", r"C:\Windows\Fonts\Dengb.ttf"),
    ("SimSun", r"C:\Windows\Fonts\simsun.ttc", "SimHei", r"C:\Windows\Fonts\simhei.ttf"),
]


def register_fonts():
    last_error = None
    for normal, normal_path, bold, bold_path in FONT_CANDIDATES:
        try:
            pdfmetrics.registerFont(TTFont(normal, normal_path, subfontIndex=0))
            pdfmetrics.registerFont(TTFont(bold, bold_path, subfontIndex=0))
            pdfmetrics.registerFontFamily(
                normal, normal=normal, bold=bold, italic=normal, boldItalic=bold
            )
            return normal, bold
        except Exception as exc:  # noqa: BLE001
            last_error = exc
            continue
    raise RuntimeError(f"No usable CJK font found: {last_error}")


FONT, FONT_BOLD = register_fonts()
print(f"[fonts] body={FONT} bold={FONT_BOLD}")

BLACK = colors.HexColor("#000000")
NAVY_HEX = "#1F3864"
STEEL_HEX = "#2E5C8A"
GRAY_HEX = "#595959"
NAVY = colors.HexColor(NAVY_HEX)
STEEL = colors.HexColor(STEEL_HEX)
GRAY = colors.HexColor(GRAY_HEX)

PAGE_W, PAGE_H = A4
LM = RM = 14 * mm
TM = 11 * mm
BM = 11 * mm
AVAIL = PAGE_W - LM - RM


def style(name, **kw):
    base = dict(fontName=FONT, fontSize=9.2, leading=14.2, textColor=BLACK)
    base.update(kw)
    return ParagraphStyle(name, **base)


S_NAME = style(
    "name",
    fontName=FONT_BOLD,
    fontSize=20.5,
    leading=24,
    spaceAfter=1.5,
    textColor=NAVY,
)
S_CONTACT = style("contact", fontSize=9, leading=12.6, textColor=GRAY)
S_SECTION = style(
    "section",
    fontName=FONT_BOLD,
    fontSize=12.5,
    leading=15.5,
    spaceBefore=8.5,
    spaceAfter=4.5,
    textColor=NAVY,
)
S_GROUP = style(
    "group", fontName=FONT_BOLD, fontSize=10, leading=13, spaceBefore=4.5, spaceAfter=3
)
S_ROLE = style("role", fontName=FONT_BOLD, fontSize=9.5, leading=12.8)
S_DATE = style("date", fontSize=9, leading=12.8, alignment=TA_RIGHT, textColor=GRAY)
S_BODY = style("body", alignment=TA_LEFT, spaceAfter=2, wordWrap="CJK")
S_SKILL = style("skill", alignment=TA_LEFT, spaceAfter=2.4, wordWrap="CJK")
S_JOBDESC = style(
    "jobdesc", alignment=TA_LEFT, fontSize=9, leading=12.8, spaceAfter=4, wordWrap="CJK"
)
S_BULLET = style(
    "bullet",
    alignment=TA_LEFT,
    leftIndent=11,
    bulletIndent=1,
    spaceBefore=0,
    spaceAfter=1.8,
    wordWrap="CJK",
    bulletColor=STEEL,
)
S_BULLET.leading = 13.4


def P(text, st):
    return Paragraph(text, st)


def bullet(text):
    # U+00B7 extracts correctly through the font's ToUnicode map; U+2022/U+25CF
    # do not, which would leave "(cid:...)" in machine-readable text extraction.
    return Paragraph(text, S_BULLET, bulletText="\u00b7")


def header_row(left, right):
    """Bold title on the left, date flushed right; borderless helper table."""
    t = Table([[P(left, S_ROLE), P(right, S_DATE)]], colWidths=[AVAIL - 118, 118])
    t.setStyle(
        TableStyle(
            [
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("LEFTPADDING", (0, 0), (-1, -1), 0),
                ("RIGHTPADDING", (0, 0), (-1, -1), 0),
                ("TOPPADDING", (0, 0), (-1, -1), 0),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 0),
            ]
        )
    )
    return t


# ---------------------------------------------------------------- content ----

OVERVIEW = (
    "8 年 Java 后端开发经验，其中 5 年面向国企与大型集团客户，负责协同办公与系统集成交付。"
    "熟悉<b>蓝凌 EKP 平台二次开发</b>与 <b>LBPM 流程引擎</b>事件扩展，"
    "主导过金蝶云星瀚、SAP、钉钉、IDaaS 等多类异构系统对接，"
    "擅长跨系统一致性、幂等与重复提交治理；熟悉 <b>Java 并发编程（JUC）</b>与 "
    "<b>Redis</b> 缓存治理，能独立完成需求调研、方案设计、编码交付、联调上线"
    "与交付文档输出。"
)

# The direction label is the only bold in each row; the body carries no inline
# emphasis so the section reads as a dense list instead of keyword soup.
SKILLS = [
    (
        "Java 并发（JUC）",
        "ThreadPoolExecutor 线程池参数调优（核心线程数、阻塞队列、拒绝策略）、"
        "CompletableFuture 并行编排、CountDownLatch 多任务协同、"
        "ConcurrentHashMap 与 CopyOnWriteArrayList 并发容器、CAS 与 AtomicLong 无锁计数、"
        "synchronized 与 ReentrantLock 锁粒度调整及死锁排查",
    ),
    (
        "缓存与 Redis",
        "Redis 缓存模型与过期策略、缓存穿透/击穿/雪崩治理、"
        "SETNX 分布式锁实现防重复提交与防重复推送、Lua 脚本保证校验与写入原子性、"
        "token 与字典热点数据缓存、缓存与数据库一致性方案",
    ),
    (
        "数据库",
        "Oracle / SQL Server / MySQL；复杂多表关联与 JOIN 去重、外键改软关联、"
        "执行计划分析、慢 SQL 优化、覆盖索引与最左匹配、中间库多数据源配置、"
        "批量插入与更新",
    ),
    (
        "平台与框架",
        "蓝凌 EKP 协同办公平台二次开发：表单建模、LBPM 流程引擎事件扩展"
        "（IEventListener / EndedTaskEvent）、门户组件 Portlet、权限模型；"
        "Spring / Spring MVC / Hibernate / MyBatis；JSP 与 JSTL",
    ),
    (
        "系统集成",
        "金蝶云星瀚（REST 接口、multipart/form-data 多附件上传、token 周期性缓存与鉴权）、"
        "SAP（XML 凭证生成、SFTP 上传、回执状态轮询）、"
        "钉钉开放平台（待办推送与审批状态回调）、"
        "IDaaS 单点登录（过滤器与 token 解析）、HTTP Basic 认证、EOP 供应商主数据同步",
    ),
    (
        "搜索",
        "Elasticsearch 7.17 统一搜索（蓝凌 OneSearch）：ansj 中文分词、同义词分析器、"
        "多线程索引、附件解析阈值与相似度调优",
    ),
    (
        "工程与交付",
        "Linux、Git、Maven、Docker、SFTP；整包发布与多版本迭代管理；"
        "需求说明书、开发说明文档、工作量评估与验收交付物输出",
    ),
]

JOBS = [
    (
        "外包交付开发（承接蓝凌生态交付项目）",
        "2024.09 - 至今",
        "承接国企与大型集团客户的协同办公与系统集成交付，独立完成需求评估、开发、"
        "部署联调与交付文档输出。客户包括深圳巴士集团、深圳市融资租赁（集团）、"
        "深圳地铁运营、罗格朗（惠州）、飞的数字化。",
    ),
    (
        "深圳市蓝凌软件股份有限公司 · 交付部 ｜ Java 交付开发工程师",
        "2020.06 - 2024.09",
        "负责蓝凌 EKP 协同办公平台的国企与大型集团交付项目，覆盖模块开发、跨系统联调与"
        "上线支持。客户包括深圳能源集团、康佳集团、深圳巴士集团、深圳市融资租赁公司。",
    ),
    (
        "中软国际（华为外包）｜ Java 开发工程师",
        "2019.07 - 2020.05",
        "中国移动音频彩铃 USDP 平台纯后端开发，使用 Oracle 存储过程完成十余个 Story，"
        "上线无现网问题。",
    ),
    (
        "厦门同创空间信息技术有限公司 ｜ Java 开发工程师",
        "2018.03 - 2019.06",
        "开思时代小马物流平台，参与两个后端服务开发，负责甲方信息附件接口对接与联调。",
    ),
]

PROJECTS_RECENT = [
    (
        "深圳巴士集团 ｜ OA 合同管理与金蝶云星瀚集成",
        "2024.09 - 2025.02",
        [
            "负责采购系统附件接收接口开发，基于 <b>REST 接口</b>与 multipart/form-data "
            "多附件上传、JSON 表单解析，解决采购侧合同附件无法进入 OA 的问题。",
            "负责合同字段与附件反向推送采购系统，实现 <b>token 周期性缓存</b>与鉴权复用，"
            "避免重复换取凭证带来的额外开销。",
            "负责合同权限申请公文流程与合同申请表单字段定制，通过<b>流程事件</b>推送待办，"
            "打通合同权限申请与审批闭环。",
            "负责合同台账导出改造，修复多表左连接重复数据并按主键去重；累计交付 "
            "<b>8 个版本整包</b>及各版本补丁包，支撑客户验收上线。",
        ],
    ),
    (
        "深圳市融资租赁（集团） ｜ 智慧办公系统二期",
        "2025.01 - 2025.09",
        [
            "负责议题管理模块开发，实现议题申请表字段与<b>审批走向联动</b>、上会安排、"
            "会议议题与公文纪要联动，解决议题、会议与公文数据割裂的问题。",
            "负责会议管理改造，实现<b>会议室保管者多选</b>（保管者字段由单值改为 List）、"
            "会议信息传阅与会议室预约优化。",
            "开发会议通知监听器，基于 <b>LBPM 事件扩展</b>（IEventListener 与 "
            "EndedTaskEvent）在会议节点结束时触发平台通知，实现会议通知自动下发。",
            "负责<b>门户组件 Portlet</b> 开发，实现工作安排与上会安排门户展示、"
            "领导批示定制与公文报表。",
        ],
    ),
    (
        "深圳市融资租赁（集团） ｜ OA 待办推送钉钉工作台",
        "2025.12 - 2026.04",
        [
            "负责 OA 待办与钉钉工作台集成，实现待办推送、点击跳转 OA 原审批表单、"
            "审批完成后<b>状态回调</b>同步 OA，解决用户在钉钉内无法处理 OA 审批的问题。",
            "基于钉钉开放平台接口完成推送链路开发，迭代 V2 两个版本及 PC 链接、"
            "待阅关闭等 <b>5 个补丁包</b>，解决移动端与 PC 端链接不一致问题。",
            "通过<b>幂等键校验</b>避免同一待办重复推送，降低钉钉侧重复待办与用户投诉。",
        ],
    ),
    (
        "罗格朗（惠州） ｜ OA 费控与 SAP 集成",
        "2025.02 - 2026.04",
        [
            "负责成本中心模块开发，实现 OA 组织架构与 <b>SAP 成本中心</b>对照映射、"
            "中间库（SQL Server）数据读取与成本中心公司关联，并设计大/小管理员二级权限模型，"
            "支持权限向下带出所有子部门及人员。",
            "负责费用事前申请单开发，实现起草时按经办人带出成本中心与预算数据，"
            "以及批准金额不高于申请金额、报销金额不高于批准金额的逐级约束校验。",
            "实现<b>预算占用与释放</b>的跨系统协同：OA 仅推送表单基础数据，"
            "预算占用与释放由预算系统处理，驳回与废弃状态分别触发对应释放调用，"
            "避免重复占用。",
            "负责报销凭证模块，流程结束通过监听器归档并生成 XML 凭证文件上传至指定 "
            "<b>SFTP 服务器</b>，等待 SAP 返回状态后再决定流程是否继续流转，"
            "解决<b>跨系统状态不一致</b>；同时完成双因子登录与移动端表单适配。",
        ],
    ),
    (
        "其他交付项目 ｜ 搜索定制、合同费控与督办优化",
        "2025.11 - 2026.09",
        [
            "<b>深圳地铁运营 · 统一搜索定制</b>：基于蓝凌统一搜索与 <b>Elasticsearch</b> "
            "定制门户全局搜索，实现默认检索文档知识库与标题域，以及文本搜索仅检索标准文本"
            "分类下文档，解决法规标准类文本命中范围过大的问题。",
            "<b>飞的数字化 · 合同变更与费控</b>：实现合同变更带入<b>费控台账</b>"
            "（金额增加走从合同、金额减少走变更协议并同步更新合同金额）、"
            "合同付款权限带出与实际支付金额不高于未付金额校验，"
            "以及相对方变更与 EOP 供应商信息同步。",
            "<b>深圳巴士集团 · 督办模块优化</b>：引入 sys/task 产品包定制至 ekp/task，"
            "定位并修复平台二开引入流程后重复调用 save 引发的<b>数据库锁冲突</b>，"
            "改造 DAO 层 update 解决；同时修复<b>定时任务死循环</b>问题。",
        ],
    ),
]

PROJECTS_EARLY = [
    (
        "深圳能源集团 ｜ 协同办公与合同管理系统",
        "2020.07 - 2021.12",
        [
            "负责合同管理模块交付开发，基于 <b>EKP 表单建模</b>与自定义流程实现合同起草、"
            "审批、用印与归档，并定制合同台账查询与导出，"
            "解决合同台账与审批数据分散的问题。",
            "负责合同台账大表查询优化，通过<b>覆盖索引</b>与 JOIN 去重改造替换原多表"
            "嵌套查询，解决台账列表查询超时问题。",
            "负责组织架构与权限模型定制，基于 EKP 组织架构与角色权限实现按部门、岗位的"
            "数据可见范围控制，解决跨部门合同数据越权可见的问题。",
            "负责组织树与字典热点数据缓存，基于 <b>Redis</b> 缓存组织树与常用字典并设置"
            "过期策略，配合空值缓存防止缓存穿透，降低数据库压力。",
        ],
    ),
    (
        "康佳集团 ｜ 集团门户与多系统集成",
        "2021.12 - 2023.03",
        [
            "负责统一待办聚合门户组件开发，基于 <b>ThreadPoolExecutor 线程池</b>"
            "并发拉取多个业务系统待办并聚合展示，解决原串行拉取导致门户加载缓慢的问题。",
            "负责跨系统数据同步接口开发，基于 token 周期性缓存与 HTTP Basic 认证"
            "完成与外部系统的数据交互，解决重复换取凭证带来的性能损耗。",
            "负责接口幂等治理，基于 <b>Redis SETNX 分布式锁</b>与幂等键防止同一单据"
            "重复提交与重复同步，解决重复入账问题。",
        ],
    ),
    (
        "深圳市融资租赁（集团） ｜ 智慧办公系统一期",
        "2023.03 - 2024.09",
        [
            "负责公文管理与会议管理模块交付开发，实现收发文表单定制、会议安排与会议室预约，"
            "解决公文与会议线下流转不可追溯的问题。",
            "负责流程引擎二次开发，基于 <b>LBPM 流程引擎</b>实现会签、加签、备选人与"
            "修改办理人能力，解决多级审批与办理人变更场景下的流程流转需求。",
            "负责批量数据导入导出开发，基于 Excel 模板与<b>线程池并发分片</b>处理批量数据，"
            "解决大批量数据单线程串行导入耗时过长的问题。",
        ],
    ),
]


# ----------------------------------------------------------------- layout ----


def skills_block():
    return [
        P(f'<font color="{STEEL_HEX}"><b>{direction}</b></font>：{detail}', S_SKILL)
        for direction, detail in SKILLS
    ]


def project_block(title, date, bullets):
    """Keep a project heading with its first bullet so it cannot orphan."""
    flow = [KeepTogether([header_row(title, date), bullet(bullets[0])])]
    flow.extend(bullet(b) for b in bullets[1:])
    flow.append(Spacer(1, 2))
    return flow


def build_story():
    story = []
    story.append(P("吴枫", S_NAME))
    story.append(
        P(
            "Java 后端开发工程师 ｜ 8 年经验 ｜ 求职意向：Java 开发<br/>"
            "电话 17338701320 ｜ 邮箱 393059695@qq.com ｜ 籍贯 江西上饶",
            S_CONTACT,
        )
    )

    story.append(P("个人概述", S_SECTION))
    story.append(P(OVERVIEW, S_BODY))

    story.append(P("专业技能", S_SECTION))
    story.extend(skills_block())

    story.append(P("工作经历", S_SECTION))
    for title, date, desc in JOBS:
        story.append(header_row(title, date))
        story.append(P(desc, S_JOBDESC))

    story.append(P("项目经历", S_SECTION))
    story.append(P("2024.09 - 至今 ｜ 外包交付（承接蓝凌生态交付项目）", S_GROUP))
    for title, date, bl in PROJECTS_RECENT:
        story.extend(project_block(title, date, bl))

    story.append(P("2020.06 - 2024.09 ｜ 蓝凌软件交付部", S_GROUP))
    for title, date, bl in PROJECTS_EARLY:
        story.extend(project_block(title, date, bl))

    story.append(P("教育背景", S_SECTION))
    story.append(
        P("2018.07 ｜ 泉州师范学院 ｜ 软件工程 ｜ 本科（2014.09 - 2018.07）", S_BODY)
    )
    return story


def main():
    os.makedirs(BUILD_DIR, exist_ok=True)
    doc = BaseDocTemplate(
        OUT_PDF,
        pagesize=A4,
        leftMargin=LM,
        rightMargin=RM,
        topMargin=TM,
        bottomMargin=BM,
        title="吴枫 - Java 后端开发工程师 - 个人简历",
        author="吴枫",
    )
    frame = Frame(LM, BM, AVAIL, PAGE_H - TM - BM, id="body", showBoundary=0)
    doc.addPageTemplates([PageTemplate(id="main", frames=[frame])])
    doc.build(build_story())
    print(f"[output] {OUT_PDF}")
    render_pages()


def render_pages(scale=2.2):
    """Rasterize the built PDF so the layout can be visually inspected."""
    import pypdfium2 as pdfium

    pdf = pdfium.PdfDocument(OUT_PDF)
    for idx in range(len(pdf)):
        out = os.path.join(BUILD_DIR, f"page-{idx + 1}.png")
        pdf[idx].render(scale=scale).to_pil().save(out)
        print(f"[render] {out}")
    print(f"[render] pages={len(pdf)}")


if __name__ == "__main__":
    main()
