# -*- coding: utf-8 -*-
"""从简历 docx 抽取内容，生成两份右侧阅读用的 md 视图。

用法：
    D:/develop/python/python.exe sync_views.py 简历-v17.docx

生成：
    项目经历-当前版.md   —— 项目经历整段
    项目经历-蓝凌期.md   —— 只有「2020.06 - 2024.09 ｜ 蓝凌软件交付部」这一段
"""

import os
import re
import sys
import zipfile

OUT_DIR = os.path.dirname(os.path.abspath(__file__))


def paragraphs(docx_path):
    """抽出每段的纯文本，加粗片段用 ** 包裹。"""
    with zipfile.ZipFile(docx_path) as z:
        xml = z.read("word/document.xml").decode("utf-8")
    out = []
    for pm in re.finditer(r"(?s)<w:p[ >].*?</w:p>", xml):
        line = ""
        for rm in re.finditer(r"(?s)<w:r[ >].*?</w:r>", pm.group(0)):
            t = "".join(m.group(1) for m in re.finditer(r"(?s)<w:t[^>]*>(.*?)</w:t>", rm.group(0)))
            if not t:
                continue
            line += ("**" + t + "**") if "<w:b/>" in rm.group(0) else t
        if line.strip():
            out.append(line)
    return out


def to_markdown(lines):
    """把项目经历那几段转成 md：分组 -> ##，条目标题 -> ###，bullet -> - """
    md = []
    for line in lines:
        if line.startswith("·"):
            md.append("- " + line[1:].strip())
        elif re.match(r"^\*\*(20\d\d\.\d\d.*｜.*)\*\*$", line):
            md.append("## " + line.strip("*"))
        elif line.startswith("**") and line.endswith("**"):
            md.append("### " + line.strip("*"))
        elif line.strip("*").strip() == "项目经历":
            continue
        else:
            md.append(line)
    return md


def main():
    docx = sys.argv[1] if len(sys.argv) > 1 else "简历-v17.docx"
    if not os.path.isabs(docx):
        docx = os.path.join(OUT_DIR, docx)
    lines = paragraphs(docx)

    i = next(k for k, v in enumerate(lines) if "项目经历" in v and v.strip("*") == "项目经历")
    # 注意：v18 起「教育背景」挪到了最前面，所以项目经历成了最后一节，
    # 这里直接取到文件末尾，不能再按「教育背景」当结束标记。
    body = lines[i + 1:]

    k = next(k for k, v in enumerate(body) if "蓝凌软件交付部" in v)
    blue = body[k:]

    # 只有在还有 {待补} 时才提这一句
    todo = "；`{待补：…}` 是还没填的数字" if any("待补" in v for v in body) else ""
    head = "# 项目经历（简历 %s）\n\n> 从 `%s` 逐字抽出，与 Word 一致%s。\n\n---\n\n" % (
        os.path.basename(docx).replace(".docx", ""), os.path.basename(docx), todo)
    head_blue = "# 2020.06 - 2024.09 ｜ 蓝凌软件交付部\n\n> 从 `%s` 逐字抽出，只含蓝凌期这一段。\n\n---\n\n" % os.path.basename(docx)

    with open(os.path.join(OUT_DIR, "项目经历-当前版.md"), "w", encoding="utf-8") as f:
        f.write(head + "\n".join(to_markdown(body)) + "\n")
    with open(os.path.join(OUT_DIR, "项目经历-蓝凌期.md"), "w", encoding="utf-8") as f:
        f.write(head_blue + "\n".join(to_markdown(blue)) + "\n")
    print("[ok] 项目经历-当前版.md / 项目经历-蓝凌期.md 已按 %s 重新生成" % os.path.basename(docx))


if __name__ == "__main__":
    main()
