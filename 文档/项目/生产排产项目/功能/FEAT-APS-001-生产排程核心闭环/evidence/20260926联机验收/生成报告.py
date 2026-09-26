"""从本轮清单、逐项结果及接口回执生成唯一 HTML 展示。"""
from pathlib import Path
import json
import html
import collections
from datetime import datetime

root = Path(__file__).parent
read = lambda name: json.loads((root / name).read_text(encoding='utf-8-sig'))
escape = lambda value: html.escape(str(value))
results = {r['id']: r for r in read('逐项结果.json')}
rows = []
for line in (root / '验收清单.md').read_text(encoding='utf-8-sig').splitlines():
    if not line.startswith('| LIVE-'):
        continue
    cells = [s.strip() for s in line.split('|')]
    ident = cells[1]
    rows.append(results.get(ident, dict(id=ident, target=cells[2], input=cells[3],
        steps=cells[4], expected=cells[5], result='NotRun', actual='待执行；以清单最新记录为准。',
        screenshotStatus=cells[8], shots=[], issue='尚无完整判定证据', at='', url='')))
assert len({r['id'] for r in rows}) == len(rows)
counts = collections.Counter(r['result'] for r in rows)
sample = read('样本标识.json')
receipts = read('接口回执.json')
artifacts = read('制品摘要.json') if (root/'制品摘要.json').exists() else []
sections = []
for row in rows:
    pictures = ''
    for shot in row['shots']:
        assert (root / shot).exists(), shot
        pictures += f'<figure><a href="{escape(shot)}" target="_blank"><img src="{escape(shot)}" alt="{escape(shot)}"></a><figcaption>{escape(shot)} · 上轮实际截图，仅证明当时状态；点击查看原图</figcaption></figure>'
    fields = [('输入/前置', row['input']), ('操作步骤', row['steps']), ('预期', row['expected']),
              ('实际结果', row['actual']), ('截图状态', row['screenshotStatus']), ('问题与限制', row['issue']),
              ('记录时间（UTC）', row['at']), ('页面/操作入口', row.get('url', '')),
              ('证据基线', row.get('baselineNote','')), ('本次续验', row.get('continuationResult',''))]
    detail = ''.join(f'<dt>{escape(k)}</dt><dd>{escape(v)}</dd>' for k,v in fields)
    sections.append(f'<section id="{row["id"]}"><h2>{row["id"]} · {escape(row["target"])}</h2><span class="badge {row["result"]}">{row["result"]}</span><dl>{detail}</dl><p><a href="接口回执.json">完整接口请求/响应</a> · <a href="样本标识.json">样本及业务对象标识</a></p><div class="images">{pictures}</div></section>')
stats = ' / '.join(f'{s} {counts[s]}' for s in ['Pass','Fail','Blocked','NotRun','NotApplicable'])
summary = ''.join(f'<tr><td><a href="#{r["id"]}">{r["id"]}</a></td><td>{escape(r["target"])}</td><td>{r["result"]}</td><td>{escape(r["screenshotStatus"])}</td></tr>' for r in rows)
failed = [r for r in receipts if r['status'] >= 400 or isinstance(r['data'],dict) and isinstance(r['data'].get('code'),int) and r['data']['code'] >= 400]
network = ''.join(f'<li>{escape(r["method"])} {escape(r["path"])}：HTTP {r["status"]}；{escape(str(r["data"])[:450])}</li>' for r in failed)
page = f'''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>生产排产系统 · 真实联机验收报告</title>
<style>body{{margin:0;background:#f1f5f9;color:#16283b;font:16px/1.7 system-ui,"Microsoft YaHei",sans-serif}}main{{max-width:1150px;margin:auto;padding:32px 24px}}header,section{{background:white;border:1px solid #dae2ea;border-radius:12px;padding:28px;margin-bottom:22px}}h1{{font-size:30px;margin:6px 0}}h2{{font-size:21px}}a{{color:#125da3}}table{{width:100%;border-collapse:collapse}}td,th{{text-align:left;border-bottom:1px solid #ddd;padding:12px}}.badge{{display:inline-block;border-radius:6px;padding:3px 12px;background:#e6edf4;font-weight:bold}}.Pass{{background:#d8f6e6;color:#106340}}.Fail{{background:#ffe0e0;color:#94232d}}.Blocked,.NotRun{{background:#fff0cd;color:#76500d}}dl{{display:grid;grid-template-columns:160px 1fr;gap:10px}}dt{{font-weight:600}}dd{{margin:0;overflow-wrap:anywhere}}.images{{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:15px}}figure{{margin:0}}img{{width:100%;border:1px solid #ced8e2}}figcaption,small{{color:#52677a}}pre{{white-space:pre-wrap;overflow-wrap:anywhere}}.note{{background:#fff5da;padding:15px;border-left:4px solid #bb850a}}@media(max-width:600px){{main{{padding:12px}}dl{{display:block}}dd{{margin-bottom:14px}}td,th{{padding:5px;font-size:13px}}}}</style><main>
<header><small>FEAT-APS-001 · 2026-09-26 · Codex 技术执行</small><h1>生产排产系统真实联机验收</h1><p class="note">用户决定：待确认。此报告使用真实工程、真实接口、独立 Worker 和隔离 MySQL 中的合成样本；不代表生产业务验证或上线接受。</p><p><b>{len(rows)} 项：</b>{stats}</p><p>前端 4176 · API 18081 · MySQL 33307 · 站点 APS_PREVIEW。UAT260926：2 种产品、3 张订单共 90 EA、6 个工序任务、2 台设备和 2 名人员。业务时间回放 2026-09-25 08:00—17:00（Asia/Shanghai），数据保留供复查。</p><p>通道：Codex 内置浏览器已有管理员会话，用户批准继续使用。内核与视口：{escape(sample.get('browserInfo',{}))}。不是独立无头验收；仅业务库、端口与样本确认隔离，未宣称浏览器 Cookie 完全隔离。</p><p><a href="验收清单.md">清单及执行记录</a> · <a href="逐项结果.json">逐项结构化结果</a> · <a href="排程独立复算.json">排程复算</a> · <a href="制品摘要.json">制品 SHA-256</a> · <a href="运行基线差异.json">实际运行基线</a> · <a href="数据库回读-当前状态.txt">数据库当前状态</a></p></header>
<section><h2>逐项索引</h2><table><thead><tr><th>ID</th><th>业务目标</th><th>结果</th><th>截图状态</th></tr></thead><tbody>{summary}</tbody></table></section>
{''.join(sections)}
<section><h2>缺陷、修复与复验保留</h2><p>首次发布失败：MySQL 默认 CURRENT_TIMESTAMP 按 +08 写入，APS 按 UTC 读取，误判主数据比输入快 8 小时。修复 APS 独立 Druid 连接初始化为 SET time_zone='+00:00'。定向测试 5 项通过（包含真实 MySQL 临时表默认审计时间探针）。原失败截图和回执保留；最终发布结果见 LIVE-05。</p><p><a href="LIVE-05-失败-时区误判.png">原始失败截图</a> · <a href="utc-regression.log">定向测试日志</a> · <a href="utc-regression-first-failed.log">测试首次配置错误记录</a> · <a href="utc-build-isolated.log">隔离 API 构建</a> · <a href="utc-worker-build2.log">Worker 构建</a> · <a href="repair-uat-audit-utc.sql">仅本次样本 audit 时间恢复脚本</a></p><p>标准 JAR 被共享进程占用，首次重打包失败；随后以独立文件名构建成功，临时 POM 内容已恢复并校验摘要一致。独立构建的当前平台还需要新的JWT密钥模块，曾出现账号校验成功但签发令牌失败。现已回到稳定平台基线，仅替换通过测试的APS MySQL模块；逐类核对APS业务代码完全一致、仅配置类变化，包条目差异见运行基线差异.json。没有新增或更改账号、密码、JWT密钥；隔离若依库仅补两个空表。原标准构建输出已恢复为可运行JAR。</p></section>
<section><h2>Network / Console 与证据边界</h2><p>记录 {len(receipts)} 次真实 API 调用。HTTP 200 中的业务 code 500/401 不计成功。以下包含录入参数试错、设计中的负面测试以及实际缺陷，逐项结论以对应场景判定为准。</p><ul>{network}</ul><p>Console 摘要若已采集，见本目录“浏览器日志.json”；未采集部分不据此宣称零错误。截图支持页面可见结果，持久化与算法判断另由 API/数据库/独立复算支撑。</p></section>
<section><h2>未覆盖与数据保留</h2><p>本轮不替代原 UAT-01～12 全部复杂场景。未覆盖多角色/跨车间权限、跨日共享批、返工补产、故障接管、大规模性能、ERP/MES/WMS/QMS 外部联动。新路线录入主要通过 API，页面回读不证明所有新增表单正确。用户/业务负责人尚未给出接受决定。</p><p>业务库写入前备份：target/aps-live-preview/business-before-live-uat-20260926.sql；audit修复前另有 business-before-utc-audit-repair.sql。备份不在报告公开嵌入。保留样本与结果，不自动清理；恢复需先停止隔离 API/Worker，并核对无后续用户新增数据，不能直接覆盖之后的业务。</p><p>生成依据：验收清单.md、逐项结果.json、接口回执.json、样本标识.json；通过同目录生成报告.py生成，HTML 不作为第二套手工事实源。生成时间 {datetime.now().isoformat(timespec='seconds')}。最终报告图像与链接检查记录见验收清单。</p></section></main></html>'''
isolation = '''<section><h2>本次续验：环境归属与证据基线</h2>
<p class="note">累计 4 Pass / 1 Fail / 5 Blocked 是保留的场景执行结论；本次新增通过 0。10 项没有重新执行业务流程，旧截图不能证明当前共享源码版本已通过。用户已反馈重新登录，当前阻塞不再仅归因于登录。</p>
<table><tr><th>对象</th><th>核查事实</th><th>结论</th></tr>
<tr><td>代码/前端</td><td>同一 main 工作区；4176 代理到 18081，但从共享 admin-web 源码热更新</td><td>源码未冻结；HEAD 不包含全部未跟踪内容</td></tr>
<tr><td>API</td><td>18081 / PID 20400，任务目录独立 JAR，SHA-256 与前次一致</td><td>API 制品已单独保存</td></tr>
<tr><td>Worker</td><td>独立进程；JAR 哈希与前次一致，但仍位于共享 target 构建目录</td><td>不能保证后续构建不覆盖</td></tr>
<tr><td>数据库</td><td>33307；aps_preview_ruoyi 与 aps_preview_business；3 订单、6 任务、1 FEASIBLE 候选，执行单/报工/产出均 0</td><td>本地测试实例独立；未验证专用账号权限边界</td></tr>
<tr><td>缓存</td><td>API PID 20400 与共享 API PID 30752 均建立到 Redis 6379 的连接；APS 配置 DB 0</td><td>共享缓存与会话命名空间</td></tr>
<tr><td>浏览器</td><td>内置浏览器，多个页面使用 127.0.0.1；标签页独立，没有 Cookie 独立性证据</td><td>端口不同不证明会话隔离</td></tr></table>
<p>本次只读核查，没有重建工程、重启服务、修改业务数据或执行 Git 写操作。前次修复和构建动作保留在下方历史记录；不属于本次隔离核查动作。</p>
<p>恢复续验需要明确串行窗口并冻结共享改动，或批准并落实独立工作目录/制品、前端版本、Redis 与浏览器会话隔离。现有《并行开发与验收隔离方案》仍为 Draft，尚未实施；没有据此自动迁移混合修改。</p>
<p>清单一致性修正：原 Markdown 中 LIVE-02/03/04 停留在 NotRun，已按上轮实际 JSON 同步为 Pass；没有新跑这些场景。原清单和报告已保存。</p>
<p><a href="环境归属核查.json">进程、端口、制品与源码哈希</a> · <a href="数据库回读-隔离核查.txt">本次只读数据库回查</a> · <a href="隔离核查前-验收清单.md">修正前清单</a> · <a href="隔离核查前-逐项结果.json">上轮逐项结果</a> · <a href="隔离核查前-verification-report.html">上轮报告</a></p></section>'''
page = page.replace('<p><b>10 项：</b>', '<p><b>累计原场景 10 项：</b>')
page = page.replace('<section><h2>逐项索引</h2>', isolation + '<section><h2>逐项索引（保留原执行结论）</h2>')
page = page.replace('内置浏览器已有管理员会话，用户批准继续使用', '内置浏览器上轮管理员会话，用户批准使用；本次仅打开证据报告')
(root/'verification-report.html').write_text(page,encoding='utf-8')
print(json.dumps({'count':len(rows),'counts':dict(counts),'images':sum(len(r['shots']) for r in rows),'report':str(root/'verification-report.html')},ensure_ascii=False))
