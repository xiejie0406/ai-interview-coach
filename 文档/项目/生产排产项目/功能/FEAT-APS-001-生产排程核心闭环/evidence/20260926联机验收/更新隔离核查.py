"""保留前次证据，更新隔离核查结论；不连接任何业务服务。"""
from pathlib import Path
import json
import shutil
from datetime import datetime, timezone

root = Path(__file__).parent
for name in ['逐项结果.json', '验收清单.md', 'verification-report.html']:
    backup = root / ('隔离核查前-' + name)
    if not backup.exists():
        shutil.copy2(root / name, backup)
rows = json.loads((root / '隔离核查前-逐项结果.json').read_text(encoding='utf-8-sig'))
now = datetime.now(timezone.utc).isoformat()
blocker = 'API 18081 与共享 API 8081 均连接 Redis 6379，配置为 DB 0；同一 127.0.0.1 浏览器环境没有 Cookie 隔离证明。前端仍从共享工作区热更新，Worker JAR 仍位于共享构建目录。并行写入验收前提未满足；本次仅做只读核查，未重建、重启或改动共享服务。'
for r in rows:
    r['historyResult'] = r['result']
    r['baselineNote'] = '保留上轮实际执行结果及当时截图，不等于当前共享源码版本重新验收通过。'
    r['reviewAt'] = now
    r['continuationResult'] = 'Blocked'
    if r['id'] in ['LIVE-05','LIVE-06','LIVE-07','LIVE-08','LIVE-09']:
        r['issue'] = blocker
        r['actual'] = r['actual'].replace('待用户重新登录后复验实际发布', '实际发布复验尚未执行，当前续验受共享环境阻塞')
        if r['id'] in ['LIVE-06','LIVE-07','LIVE-08']:
            r['screenshotStatus'] = '待截图：并行环境隔离不足'
    elif r['id'] == 'LIVE-10':
        r['baselineNote'] += ' 本次只读源码仍可见工作台手填计划 UUID；没有新的连续操作截图，原 Fail 未关闭。'
    if r['shots']:
        r['screenshotStatus'] = '历史已截图；本次续验待截图（环境阻塞）'
(root / '逐项结果.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2),encoding='utf-8')
intro = '''# 2026-09-26 真实联机验收清单

执行者：Codex；用户决定：待确认。当前为部分执行及隔离核查报告。
样本：UAT260926 合成数据，2 产品、3 订单（40/30/20 EA）、6 工序任务、2 设备、2 人员；回放 2026-09-25 08:00—17:00 Asia/Shanghai。
通道：Codex 内置浏览器；上轮已登录会话，非无头。本次仅打开报告，未进行业务页面写操作。
版本：后端当前任务 JAR 哈希与前次一致；前端源码未冻结，不能将旧截图算作当前源码版本的新通过证据。详见[环境归属核查](环境归属核查.json)。
累计原场景结论：Pass 4 / Fail 1 / Blocked 5 / NotRun 0 / NotApplicable 0。本次续验：新增通过 0，10 项均未重跑；原失败和原通过保留，不覆盖历史。

## 本次续验阻塞

''' + blocker + '''

当前数据库回读：3 订单、6 任务、1 FEASIBLE 非正式计划，执行单、报工、产出批均为 0。见[只读回读](数据库回读-隔离核查.txt)。
恢复条件：明确一个暂停共享改动的串行验收窗口，或另行批准并落实版本冻结、独立资源与会话隔离。已有隔离方案为 Draft，不视作工作树迁移或新资源授权。

| ID | 目标与关联 | 前置/数据 | 计划步骤 | 预期 | 证据 | 结果 | 截图状态 |
|---|---|---|---|---|---|---|---|
'''
for r in rows:
    cols = [r['id'],r['target'],r['input'],r['steps'],r['expected'],'接口回执、逐项记录及历史截图',r['result'],r['screenshotStatus']]
    intro += '| ' + ' | '.join(x.replace('|','／') for x in cols) + ' |\n'
intro += '\n## 逐项记录\n\n'
for r in rows:
    intro += f"### {r['id']} · {r['result']}\n\n上轮执行时间：{r['at']}；本次核查时间：{now}。\n\n{r['actual']}\n\n{r['baselineNote']}\n\n遗留：{r['issue']}\n\n"
    intro += '证据：' + '、'.join(f'[{s}]({s})' for s in r['shots']) + '；[接口回执](接口回执.json)。\n\n'
intro += '''## 变更及边界

隔离核查发现原 Markdown 清单 LIVE-02/03/04 停留在 NotRun，但已执行 JSON 与 HTML 为 Pass；现从结构化结果同步清单，保留[原清单](隔离核查前-验收清单.md)、[原结果](隔离核查前-逐项结果.json)及[原报告](隔离核查前-verification-report.html)，不倒填执行时间。
首次发布的时区误判、定向修复及 5 项测试是上轮事实；本次没有发布成功的新证据。SQL 首次只读回查因使用不存在的 input_captured_at 列失败，改为实际列后成功；该失败未涉及写入。
最初清单是在规范更新后补存，不能倒称在最初录入前建立。多角色、跨日共享批、返工补产、故障接管、大规模性能及外部系统联动未覆盖。
截图缺口按行注明；历史截图仅支持当时对应状态。报告视觉及一致性检查见[报告页面核对](报告页面核对.json)。
'''
(root / '验收清单.md').write_text(intro,encoding='utf-8')
entry = root.parent.parent / '验收记录.md'
text = entry.read_text(encoding='utf-8-sig')
text = text.replace('后续链路等待有效登录会话', '后续链路受共享 Redis、浏览器会话隔离不足及未冻结前端版本阻塞；已有通过仅保留其原执行基线，不算作当前版本的新通过')
entry.write_text(text,encoding='utf-8')
print('已保存前次证据并同步逐项清单；未修改业务代码与服务。')
