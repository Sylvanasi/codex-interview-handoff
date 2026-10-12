# -*- coding: utf-8 -*-
"""Build the Word (.docx) version of the resume.

Reuses the exact content from build_resume.py so the DOCX and the verified PDF
cannot drift apart. Design is deliberately monochrome: black text, dark gray
headings, light gray table rules and fill.
"""

import os
import re

from docx import Document
from docx.enum.table import WD_ALIGN_VERTICAL
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_TAB_ALIGNMENT
from docx.enum.section import WD_SECTION
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor

import build_resume as content

OUT_DIR = os.path.dirname(os.path.abspath(__file__))
OUT_DOCX = os.path.join(OUT_DIR, "吴枫-Java开发工程师-简历.docx")

FONT = "微软雅黑"
BLACK = RGBColor(0x00, 0x00, 0x00)
NAVY = RGBColor(0x1F, 0x38, 0x64)
STEEL = RGBColor(0x2E, 0x5C, 0x8A)
GRAY = RGBColor(0x59, 0x59, 0x59)
GRID = "D9D9D9"
HEADER_FILL = "E8E8E8"

PAGE_W_CM = 21.0
PAGE_H_CM = 29.7
MARGIN_CM = 1.4
TOP_CM = 1.1
BOTTOM_CM = 1.1
USABLE_PT = (PAGE_W_CM - 2 * MARGIN_CM) / 2.54 * 72


def set_run(run, size, bold=False, color=BLACK, name=FONT):
    run.font.name = name
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.color.rgb = color
    rPr = run._element.get_or_add_rPr()
    rFonts = rPr.find(qn("w:rFonts"))
    if rFonts is None:
        rFonts = OxmlElement("w:rFonts")
        rPr.append(rFonts)
    for attr in ("w:ascii", "w:hAnsi", "w:eastAsia", "w:cs"):
        rFonts.set(qn(attr), name)


def segments(text):
    """Split reportlab-style '<b>..</b>' markup into (text, bold) pairs."""
    out = []
    pos = 0
    for m in re.finditer(r"<b>(.*?)</b>", text, re.S):
        if m.start() > pos:
            out.append((text[pos : m.start()], False))
        out.append((m.group(1), True))
        pos = m.end()
    if pos < len(text):
        out.append((text[pos:], False))
    return out


def add_para(
    doc,
    text="",
    size=9.2,
    bold=False,
    line=13.0,
    before=0,
    after=2,
    indent=0,
    hanging=0,
    right_tab=False,
    keep_next=False,
    bullet=False,
    color=BLACK,
    bullet_color=STEEL,
):
    p = doc.add_paragraph()
    pf = p.paragraph_format
    pf.space_before = Pt(before)
    pf.space_after = Pt(after)
    pf.line_spacing = Pt(line)
    pf.widow_control = True
    if keep_next:
        pf.keep_with_next = True
    if indent:
        pf.left_indent = Pt(indent)
    if hanging:
        pf.first_line_indent = Pt(-hanging)
    if right_tab:
        pf.tab_stops.add_tab_stop(Pt(USABLE_PT), WD_TAB_ALIGNMENT.RIGHT)
    if bullet:
        set_run(p.add_run("\u00b7 "), size, False, bullet_color)
    for chunk, is_bold in segments(text):
        if chunk:
            set_run(p.add_run(chunk), size, bold or is_bold, color)
    if not text:
        set_run(p.add_run(""), size, bold, color)
    return p


def add_header(doc, title, date, size=9.5, line=12.8):
    """Bold title on the left, gray date flushed right, joined by a right tab."""
    p = doc.add_paragraph()
    pf = p.paragraph_format
    pf.space_before = Pt(0)
    pf.space_after = Pt(0)
    pf.line_spacing = Pt(line)
    pf.keep_with_next = True
    pf.widow_control = True
    pf.tab_stops.add_tab_stop(Pt(USABLE_PT), WD_TAB_ALIGNMENT.RIGHT)
    set_run(p.add_run(title), size, True)
    set_run(p.add_run("\t"), size, True)
    set_run(p.add_run(date), size, False, GRAY)
    return p


def lighten_title_style(doc):
    """Word's built-in Title style can carry a rule/border; strip it."""
    style = doc.styles["Title"]
    style.font.color.rgb = BLACK
    pPr = style.element.get_or_add_pPr()
    for tag in ("w:pBdr", "w:shd"):
        for el in pPr.findall(qn(tag)):
            pPr.remove(el)


def setup_page(doc):
    section = doc.sections[0]
    section.page_width = Cm(PAGE_W_CM)
    section.page_height = Cm(PAGE_H_CM)
    section.left_margin = Cm(MARGIN_CM)
    section.right_margin = Cm(MARGIN_CM)
    section.top_margin = Cm(TOP_CM)
    section.bottom_margin = Cm(BOTTOM_CM)
    section.header_distance = Cm(0.8)
    section.footer_distance = Cm(0.8)
    normal = doc.styles["Normal"]
    normal.font.name = FONT
    normal.font.size = Pt(9.2)
    normal.font.color.rgb = BLACK
    rPr = normal.element.get_or_add_rPr()
    rFonts = rPr.find(qn("w:rFonts"))
    if rFonts is None:
        rFonts = OxmlElement("w:rFonts")
        rPr.append(rFonts)
    for attr in ("w:ascii", "w:hAnsi", "w:eastAsia", "w:cs"):
        rFonts.set(qn(attr), FONT)


def section_heading(doc, text, first=False):
    return add_para(
        doc,
        text,
        size=12.5,
        bold=True,
        line=15.5,
        before=0 if first else 8.5,
        after=4.5,
        keep_next=True,
        color=NAVY,
    )


def skills_block(doc):
    """Dense label-and-detail list; only the direction label is bold."""
    for direction, detail in content.SKILLS:
        p = doc.add_paragraph()
        pf = p.paragraph_format
        pf.space_before = Pt(0)
        pf.space_after = Pt(2.4)
        pf.line_spacing = Pt(13.0)
        pf.widow_control = True
        set_run(p.add_run(direction), 9.2, True, STEEL)
        set_run(p.add_run("：" + detail), 9.2, False, BLACK)


def build():
    doc = Document()
    setup_page(doc)
    lighten_title_style(doc)

    name = add_para(doc, "吴枫", size=20.5, bold=True, line=24, after=1.5, color=NAVY)
    name.style = doc.styles["Title"]
    for run in name.runs:
        set_run(run, 20.5, True, NAVY)
    add_para(
        doc,
        "Java 后端开发工程师 ｜ 8 年经验 ｜ 求职意向：Java 开发",
        size=9.2,
        line=12.6,
        after=0,
        color=GRAY,
    )
    add_para(
        doc,
        "电话 17338701320 ｜ 邮箱 393059695@qq.com ｜ 籍贯 江西上饶",
        size=9.2,
        line=12.6,
        after=2,
        color=GRAY,
    )

    section_heading(doc, "个人概述")
    add_para(doc, content.OVERVIEW, size=9.2, line=13.0, after=2)

    section_heading(doc, "专业技能")
    skills_block(doc)

    section_heading(doc, "工作经历")
    for title, date, desc in content.JOBS:
        add_header(doc, title, date)
        add_para(doc, desc, size=9, line=12.4, after=4)

    section_heading(doc, "项目经历")
    add_para(
        doc,
        "2024.09 - 至今 ｜ 外包交付（承接蓝凌生态交付项目）",
        size=10,
        bold=True,
        line=13,
        before=4.5,
        after=3,
        keep_next=True,
    )
    for title, date, bullets in content.PROJECTS_RECENT:
        add_header(doc, title, date)
        for b in bullets:
            add_para(doc, b, size=9.2, line=12.6, after=1.6, indent=11, hanging=11, bullet=True)
        add_para(doc, "", size=1, line=1, after=0)

    add_para(
        doc,
        "2020.06 - 2024.09 ｜ 蓝凌软件交付部",
        size=10,
        bold=True,
        line=13,
        before=4.5,
        after=3,
        keep_next=True,
    )
    for title, date, bullets in content.PROJECTS_EARLY:
        add_header(doc, title, date)
        for b in bullets:
            add_para(doc, b, size=9.2, line=12.6, after=1.6, indent=11, hanging=11, bullet=True)
        add_para(doc, "", size=1, line=1, after=0)

    section_heading(doc, "教育背景")
    add_para(
        doc,
        "2018.07 ｜ 泉州师范学院 ｜ 软件工程 ｜ 本科（2014.09 - 2018.07）",
        size=9.2,
        line=13.0,
    )

    doc.core_properties.title = "吴枫 - Java 后端开发工程师 - 个人简历"
    doc.core_properties.author = "吴枫"
    doc.save(OUT_DOCX)
    print(f"[output] {OUT_DOCX}")
    return OUT_DOCX


if __name__ == "__main__":
    build()
