# -*- coding: utf-8 -*-
"""Render a DOCX to page PNGs for visual QA.

Uses LibreOffice headless (the documented manual conversion path, with a unique
user profile so it can run without a desktop session) and rasterizes the PDF
with pypdfium2. Poppler's pdftoppm is not available in this environment.
"""

import os
import shutil
import subprocess
import sys
import uuid

import pypdfium2 as pdfium


def add_libreoffice_to_path():
    """PATH is reset in child processes here, so inject it inside Python."""
    candidates = [
        os.path.join(
            os.environ.get("LOCALAPPDATA", ""), "Programs", "LibreOffice", "program"
        ),
        r"C:\Program Files\LibreOffice\program",
        r"C:\Program Files (x86)\LibreOffice\program",
    ]
    for directory in candidates:
        exe = os.path.join(directory, "soffice.exe")
        if os.path.isfile(exe):
            os.environ["PATH"] = directory + os.pathsep + os.environ.get("PATH", "")
            return exe
    raise FileNotFoundError("soffice.exe not found in any known LibreOffice location")


def convert_to_pdf(docx_path, out_dir):
    soffice = add_libreoffice_to_path()
    os.makedirs(out_dir, exist_ok=True)
    profile = os.path.join(out_dir, f"lo_profile_{uuid.uuid4().hex[:8]}")
    os.makedirs(profile, exist_ok=True)
    profile_uri = "file:///" + profile.replace("\\", "/")
    cmd = [
        soffice,
        "--headless",
        "--norestore",
        "--nologo",
        f"-env:UserInstallation={profile_uri}",
        "--convert-to",
        "pdf",
        "--outdir",
        out_dir,
        docx_path,
    ]
    # soffice writes UTF-8 but the default console code page here is GBK, so
    # capture raw bytes and decode defensively instead of using text=True.
    proc = subprocess.run(cmd, capture_output=True, timeout=300)
    out = (proc.stdout or b"").decode("utf-8", errors="replace").strip()
    err = (proc.stderr or b"").decode("utf-8", errors="replace").strip()
    print(f"[soffice] exit={proc.returncode}")
    if out:
        print(f"[soffice] {out[:400]}")
    if err:
        print(f"[soffice:err] {err[:400]}")
    pdf_path = os.path.join(
        out_dir, os.path.splitext(os.path.basename(docx_path))[0] + ".pdf"
    )
    if not os.path.isfile(pdf_path):
        raise RuntimeError(f"conversion produced no PDF at {pdf_path}")
    shutil.rmtree(profile, ignore_errors=True)
    return pdf_path


def rasterize(pdf_path, out_dir, scale=2.2):
    pdf = pdfium.PdfDocument(pdf_path)
    pages = len(pdf)
    for idx in range(pages):
        out = os.path.join(out_dir, f"page-{idx + 1}.png")
        pdf[idx].render(scale=scale).to_pil().save(out)
        print(f"[render] {out}")
    print(f"[render] pages={pages} pdf={pdf_path}")
    return pages


def main():
    docx_path = os.path.abspath(sys.argv[1])
    out_dir = os.path.abspath(sys.argv[2]) if len(sys.argv) > 2 else os.path.dirname(docx_path)
    pdf_path = convert_to_pdf(docx_path, out_dir)
    rasterize(pdf_path, out_dir)


if __name__ == "__main__":
    main()
