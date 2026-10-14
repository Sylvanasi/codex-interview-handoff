from pathlib import Path
from xml.sax.saxutils import escape
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parent/'流程图'
DIAGRAMS=[
('01-会议通知','会议通知：查询、去重、并发与结果记录',[
('管理员发布会议','选择议题，确定通知接收范围'),('查询参会人','meeting → meeting_topic → topic_attendee；IN分批与Set去重'),('提交通知任务','线程池接收后执行safeSend；submit拒绝则记录REJECTED'),('收集任务结果','已处理业务失败返回FAILED；逃出任务的异常由get包装'),('记录与补偿','push_log记录结果，查FAILED重试；现有demo仅演示一次重试')],
'业务设定基于v25。通知真实动作需明确；本地批量优劣不能由逐人sleep实验推断。'),
('02-议题列表','议题列表：从业务筛选到执行计划',[
('部门提交议题','草稿 → 审批 → 待上会 → 会议关联'),('管理员筛选列表','按状态筛选，按publish_time、id稳定倒序分页'),('建立查询基线','记录SQL、数据分布、EXPLAIN与实际耗时'),('对比索引','(status,publish_time)与时间优先索引：过滤和排序收益不同'),('分页与验收','比较覆盖与回表、offset与复合游标；以真实计划为准')],
'多状态IN不保证全局时间有序；不能承诺加(status,time)后filesort一定消失。'),
('03-SSO与待办','SSO登录与待办同步：两条独立链路',[
('浏览器访问OA','未登录 → 302到认证端；生成并绑定state'),('授权回调','返回code与state；校验state、回调地址与有效性'),('后端建立登录态','换token → 获取身份 → 映射OA账号 → 创建本地session'),('另一路：OA业务产生待办','同步到工作台，记录映射与业务幂等标识'),('处理状态事件','校验回调、去重、条件更新；点击跳转与审批完成方向需核实')],
'图为复习设计；具体平台API和事件方向查源码，不把OAuth2授权直接等同于登录协议。'),
('04-预算与归档','预算协同与流程归档：本地事务不等于跨系统事务',[
('审批通过','本地预算占用记录与远端调用需要明确事务边界'),('判断远端结果','确定成功 / 确定失败 / 超时待确认，不能一律按失败回滚'),('驳回或废弃','生成释放请求，使用业务流水关联原占用'),('持久化补偿','失败落表 → 定时认领 → 幂等重试 → 超阈值人工处理'),('独立归档链路','流程结束 → XML生成与校验 → SFTP上传 → 按协议处理回执')],
'普通数据库回滚不能撤销远端副作用；当前不新增SFTP demo。'),
('05-人员同步','组织人员同步：稳定分片、复合水位与失败重跑',[
('确定本轮范围','源平台分页能力、固定上界、全量或增量模式'),('拆分稳定分片','按明确主键范围或源平台分片，记录分片标识'),('受控并发拉取','限制并发与速率；组织依赖先处理或暂存关联'),('幂等写入与进度','UPSERT / 版本校验；提交成功后记录分片进度'),('完成与恢复','失败片单独重跑，全部覆盖后再推进全局水位')],
'仅lastUpdateTime可能遗漏同时间数据；考虑(time,id)或重叠窗口，取决于源接口能力。'),
('06-待办聚合','门户待办聚合：统一预算与单系统降级',[
('门户请求进入','记录统一deadline，提交阶段也消耗预算'),('并发查询独立系统','OA / ERP / HR / 财务；每个系统一个任务'),('收集完成结果','正常待办保留；失败、超时、拒绝按系统标记'),('预算耗尽','仍读取已完成任务，只取消未完成任务'),('返回局部结果','固定系统顺序；中断清理本次任务，不关闭共享池')],
'HTTP连接/读取超时独立配置；取消不保证阻塞调用立即停止，整体耗时需留容差。')]
for name,title,steps,note in DIAGRAMS:
 s=['<svg xmlns="http://www.w3.org/2000/svg" width="1120" height="950" viewBox="0 0 1120 950"><rect width="1120" height="950" fill="#f8fafc"/><style>text{font-family:Arial,\'PingFang SC\',sans-serif;fill:#0f172a}.title{font-size:27px;font-weight:700}.head{font-size:22px;font-weight:700}.body{font-size:19px}.small{font-size:17px;fill:#475569}</style><defs><marker id="a" markerWidth="10" markerHeight="8" refX="9" refY="4" orient="auto"><path d="M0,0 L9,4 L0,8" fill="#64748b"/></marker></defs>']
 def t(x,y,v,c):s.append(f'<text x="{x}" y="{y}" class="{c}">{escape(v)}</text>')
 t(35,48,title,'title');t(35,84,'面试复习业务设定 · 图示不是生产实现证明 · 实验结果与项目指标分开','small')
 for i,(h,b) in enumerate(steps):
  y=115+i*140
  s.append(f'<rect x="65" y="{y}" width="990" height="105" rx="14" fill="{["#dbeafe","#dcfce7","#fef3c7","#ede9fe","#e2e8f0"][i]}" stroke="#94a3b8"/>')
  t(90,y+35,f'{i+1}. {h}','head');t(90,y+75,b,'body')
  if i<4:s.append(f'<path d="M560,{y+105} L560,{y+135}" stroke="#64748b" stroke-width="2" marker-end="url(#a)"/>')
 t(35,868,'边界与追问','head')
 # wrap long note to keep readable
 for k in range(0,len(note),57):t(35,903+(k//57)*27,note[k:k+57],'small')
 s.append('</svg>');p=ROOT/(name+'.svg');p.write_text(''.join(s));ET.parse(p)
print('6 SVG generated and XML validated')
