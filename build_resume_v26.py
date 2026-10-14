"""从 v25 保留原版式，仅修改会议待办同步条目。"""
from pathlib import Path
from docx import Document
ROOT = Path(__file__).resolve().parent
NEW = '改造会议待办同步：单场会议参会人可达上百人，待办同时要同步给统一数据平台，统一数据平台仅支持单人待办创建的 HTTP 接口，采用有界线程池并发同步，控制并发与排队数量，逐人记录同步结果，支持失败隔离与补偿重试。'
def build():
    doc = Document(ROOT / '简历-v25.docx')
    found = 0
    for p in doc.paragraphs:
        if '改造会议通知功能' in p.text:
            prefix = '· ' if p.text.startswith('·') else ''
            runs = p.runs
            runs[0].text = prefix + '改造'
            runs[1].text = '会议待办同步'
            runs[1].bold = True
            runs[2].text = NEW[len('改造会议待办同步'):]
            runs[2].bold = False
            for r in runs[3:]: r.text = ''
            found += 1
    assert found == 1, found
    doc.core_properties.title = '吴枫 - Java 后端开发 - 个人简历 v26'
    doc.save(ROOT / '简历-v26.docx')
if __name__ == '__main__': build()
