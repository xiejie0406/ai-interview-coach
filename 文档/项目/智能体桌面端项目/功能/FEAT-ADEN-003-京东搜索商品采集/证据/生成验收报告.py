#!/usr/bin/env python3
"""从真实验收结果生成展示报告；不推断或修改场景结果。

默认输入：本脚本同目录「验收结果.json」；默认输出：上级目录 verification-report.html。
输入结构：
  title: 可选报告标题
  environment: 字符串、列表或对象，记录实际工具/隔离/样本/清理情况
  versions: 字符串、列表或对象，记录构建、安装路径、哈希等
  userDecision: 待确认 / 接受 / 有条件接受 / 不接受；或含 decision、reason 的对象
  limitations: 可选总体限制（字符串或列表）
  items: 恰好 UAT-JD-13 至 UAT-JD-24 的 12 项，顺序可任意
    id, goal, steps, expected, actual, result, screenshotStatus, evidence, limitations
    可选 input、entry、executedAt、history、networkConsole，原样作为文本展示
    result: Pass / Fail / Blocked / NotRun / NotApplicable
    screenshotStatus: 待截图 / 已截图 / 截图阻塞 / 不适用
    evidence: 路径字符串，或 {path, label?, description?} 对象的数组。
      相对路径以输入 JSON 所在目录为基准；允许绝对本地路径或 HTTPS 链接。
      仅实际存在的 PNG/JPEG/WebP/GIF 文件展示缩略图；默认嵌入其真实字节。
      HTML、SVG、日志等只提供链接，不执行或内嵌未知内容。

本脚本不创建示例「验收结果.json」，不将技术测试结果扩写为整个 UAT 的 Pass。
缺失证据以红色提示保留，不生成占位图，不悄悄更改用户输入状态。
"""
from __future__ import annotations

import argparse
import base64
import html
import json
import os
from collections import Counter
from datetime import datetime
from pathlib import Path
from urllib.parse import quote, urlparse

RESULTS = ("Pass", "Fail", "Blocked", "NotRun", "NotApplicable")
RESULT_LABELS = {"Pass": "通过", "Fail": "失败", "Blocked": "阻塞", "NotRun": "未执行", "NotApplicable": "不适用"}
SCREENSHOTS = ("待截图", "已截图", "截图阻塞", "不适用")
EXPECTED_IDS = tuple(f"UAT-JD-{number}" for number in range(13, 25))
IMAGE_TYPES = {".png": "image/png", ".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".webp": "image/webp", ".gif": "image/gif"}
REQUIRED = ("id", "goal", "steps", "expected", "actual", "result", "screenshotStatus", "evidence", "limitations")


def escape(value: object) -> str:
    return html.escape(str(value), quote=True)


def rich_text(value: object) -> str:
    """只渲染纯文本和结构；JSON 中的 HTML/页面原文不会执行。"""
    if isinstance(value, list):
        return "<ul>" + "".join(f"<li>{rich_text(item)}</li>" for item in value) + "</ul>" if value else '<p class="muted">未提供补充内容。</p>'
    if isinstance(value, dict):
        return '<dl class="metadata">' + "".join(f"<dt>{escape(key)}</dt><dd>{rich_text(item)}</dd>" for key, item in value.items()) + "</dl>"
    if value is None or value == "":
        return '<p class="muted">未填写。</p>'
    return '<p class="preserve">' + escape(value) + "</p>"


def validate(data: object) -> dict:
    if not isinstance(data, dict):
        raise ValueError("输入必须为 JSON 对象")
    for name in ("environment", "versions", "userDecision", "items"):
        if name not in data:
            raise ValueError(f"缺少共同字段 {name}")
    items = data["items"]
    if not isinstance(items, list) or len(items) != 12:
        raise ValueError("items 必须包含全部 12 项 UAT；未执行项也必须保留")
    ids = []
    for item in items:
        if not isinstance(item, dict):
            raise ValueError("每个 UAT 必须为对象")
        for field in REQUIRED:
            if field not in item:
                raise ValueError(f"{item.get('id', '未知项')} 缺少 {field}")
        ids.append(item["id"])
        if item["result"] not in RESULTS:
            raise ValueError(f"{item['id']} 结果不是规范枚举")
        if item["screenshotStatus"] not in SCREENSHOTS:
            raise ValueError(f"{item['id']} 截图状态不是规范枚举")
        if not isinstance(item["evidence"], list):
            raise ValueError(f"{item['id']} evidence 必须为数组")
    if len(set(ids)) != len(ids) or set(ids) != set(EXPECTED_IDS):
        raise ValueError("UAT ID 必须与 UAT-JD-13 至 UAT-JD-24 一一对应，不能缺失或重复")
    return data


def relative_link(path: Path, output: Path) -> str:
    try:
        relative = os.path.relpath(path, output.parent).replace("\\", "/")
        return quote(relative, safe="/:")
    except ValueError:
        return path.as_uri()


def render_evidence(item: dict, source: Path, output: Path, embed: bool) -> tuple[str, list[str], int]:
    rendered, issues = [], []
    images = 0
    for index, entry in enumerate(item["evidence"], 1):
        if isinstance(entry, str):
            entry = {"path": entry}
        if not isinstance(entry, dict) or not isinstance(entry.get("path"), str) or not entry["path"]:
            raise ValueError(f"{item['id']} 第 {index} 个 evidence 缺少有效 path")
        raw = entry["path"]
        label = entry.get("label") or Path(raw).name or f"证据 {index}"
        caption = entry.get("description", "")
        parsed = urlparse(raw)
        if parsed.scheme.lower() in ("https", "http"):
            rendered.append(f'<div class="evidence-file"><a href="{escape(raw)}" target="_blank" rel="noopener noreferrer">{escape(label)}</a>{rich_text(caption) if caption else ""}<small>外部证据链接；生成器未访问或验证其内容。</small></div>')
            continue
        if parsed.scheme and not (len(parsed.scheme) == 1 and len(raw) > 2 and raw[1] == ":"):
            issues.append(f"拒绝不支持的证据协议：{raw}")
            rendered.append(f'<p class="integrity">证据链接协议不支持：{escape(label)}</p>')
            continue
        path = Path(raw)
        if not path.is_absolute():
            path = source.parent / path
        path = path.resolve()
        if not path.is_file():
            issues.append(f"证据文件缺失：{raw}")
            rendered.append(f'<div class="integrity"><strong>证据文件缺失</strong><p>{escape(label)} — {escape(raw)}</p></div>')
            continue
        href = relative_link(path, output)
        mime = IMAGE_TYPES.get(path.suffix.lower())
        if mime:
            images += 1
            src = f"data:{mime};base64,{base64.b64encode(path.read_bytes()).decode('ascii')}" if embed else href
            rendered.append(f'<figure><a href="{escape(href)}" target="_blank" rel="noopener"><img src="{escape(src)}" alt="{escape(label)}" loading="lazy" /></a><figcaption><a href="{escape(href)}" target="_blank" rel="noopener">{escape(label)} · 查看原图</a>{rich_text(caption) if caption else ""}</figcaption></figure>')
        else:
            rendered.append(f'<div class="evidence-file"><a href="{escape(href)}">{escape(label)}</a><small>{path.stat().st_size:,} 字节</small>{rich_text(caption) if caption else ""}</div>')
    if item["screenshotStatus"] == "已截图" and images == 0:
        issues.append("截图状态为已截图，但没有关联实际存在的本地图片")
    if item["result"] == "Pass" and item["screenshotStatus"] in ("待截图", "截图阻塞"):
        issues.append("结果为 Pass，但必需截图尚未完成；请按真实证据修正输入状态")
    if not rendered:
        rendered.append('<p class="muted">本项尚未关联证据；原因与未执行情况请见实际结果和限制。</p>')
    return '<div class="evidence-grid">' + "".join(rendered) + "</div>", issues, images


def generate(data: dict, source: Path, output: Path, embed: bool = True) -> str:
    items = sorted(data["items"], key=lambda item: EXPECTED_IDS.index(item["id"]))
    counts = Counter(item["result"] for item in items)
    screenshot_counts = Counter(item["screenshotStatus"] for item in items)
    rows, panels, integrity = [], [], []
    total_images = 0
    for item in items:
        ident = item["id"]
        result = item["result"]
        evidence, issues, image_count = render_evidence(item, source, output, embed)
        total_images += image_count
        integrity.extend(f"{ident}：{issue}" for issue in issues)
        rows.append(f'<tr><td><a href="#{escape(ident)}">{escape(ident)}</a></td><td>{escape(item["goal"])}</td><td><span class="badge {result}">{RESULT_LABELS[result]} · {result}</span></td><td>{escape(item["screenshotStatus"])}</td></tr>')
        optional = "".join(f'<div class="item-section"><h3>{label}</h3>{rich_text(item[key])}</div>' for key, label in (("input", "前置、角色与输入"), ("entry", "页面或操作入口"), ("executedAt", "实际执行时间"), ("networkConsole", "网络与 Console"), ("history", "问题与复验历史")) if key in item)
        issue_panel = '<div class="integrity"><strong>证据一致性问题</strong>' + rich_text(issues) + "</div>" if issues else ""
        panels.append(f'''<details class="uat {result}" id="{escape(ident)}" open>
<summary><span>{escape(ident)} · {escape(item['goal'])}</span><span class="badge {result}">{RESULT_LABELS[result]} · {result}</span></summary>
<div class="uat-body"><p class="screenshot-state">截图状态：<strong>{escape(item['screenshotStatus'])}</strong> · 已关联实际图片 {image_count} 张</p>
{issue_panel}{optional}<div class="two-column"><section><h3>实际步骤 / 计划步骤说明</h3>{rich_text(item['steps'])}</section><section><h3>预期结果</h3>{rich_text(item['expected'])}</section></div>
<section><h3>实际结果</h3>{rich_text(item['actual'])}</section><section><h3>限制与未覆盖项</h3>{rich_text(item['limitations'])}</section><section><h3>截图与其他证据</h3>{evidence}</section></div></details>''')
    title = data.get("title", "京东详情页采集 · 安装版验收报告")
    stats = "".join(f'<div class="stat {result}"><strong>{counts[result]}</strong><span>{RESULT_LABELS[result]} / {result}</span></div>' for result in RESULTS)
    screenshot_stats = " · ".join(f"{name} {screenshot_counts[name]}" for name in SCREENSHOTS)
    attention = [item for item in items if item["result"] in ("Fail", "Blocked", "NotRun")]
    attention_html = "<ul>" + "".join(f'<li><a href="#{escape(item["id"])}">{escape(item["id"])} · {escape(item["goal"])}</a> — {RESULT_LABELS[item["result"]]}</li>' for item in attention) + "</ul>" if attention else "<p>输入记录中没有失败、阻塞或未执行项。技术结果不替代用户接受决定。</p>"
    integrity_html = '<section class="integrity"><h2>证据一致性检查发现问题</h2>' + rich_text(integrity) + "<p>上述提示没有自动改变任何输入结果；需更新真实证据后重新生成。</p></section>" if integrity else '<p class="muted">静态一致性检查：12 个 ID、结果枚举、截图关联路径已核对。此检查不等于截图内容回读或报告视觉检查。</p>'
    generated = datetime.now().astimezone().isoformat(timespec="seconds")
    return f'''<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src 'self' data:; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none';"><title>{escape(title)}</title>
<style>
:root{{font-family:'Microsoft YaHei','PingFang SC',system-ui,sans-serif;color:#24352e;background:#f2f5f3;font-size:15px;line-height:1.65}}*{{box-sizing:border-box}}body{{margin:0}}main{{max-width:1320px;margin:auto;padding:36px 28px 64px}}h1{{font-size:30px;line-height:1.35;margin:8px 0 16px}}h2{{font-size:21px;margin:0 0 16px}}h3{{font-size:16px;margin:20px 0 8px}}p{{margin:8px 0}}a{{color:#205cac;text-underline-offset:3px;overflow-wrap:anywhere}}a:focus-visible,summary:focus-visible{{outline:3px solid #2b6ad0;outline-offset:4px}}header,.card,.uat{{background:#fff;border:1px solid #d6e1da;border-radius:14px;margin-bottom:22px}}header,.card{{padding:26px}}.eyebrow{{color:#547264;font-size:12px;font-weight:700;letter-spacing:.12em}}.muted,small{{color:#53665d}}small{{display:block}}.stats{{display:grid;grid-template-columns:repeat(5,1fr);gap:12px;margin:18px 0}}.stat{{padding:18px;border:1px solid #d6e1da;border-radius:12px;background:#fff}}.stat strong{{font-size:32px;display:block}}.stat span{{font-size:13px}}.badge{{display:inline-block;padding:4px 10px;border-radius:7px;font-size:13px;white-space:nowrap;font-weight:700}}.Pass.badge{{color:#175335;background:#e1f1e6}}.Fail.badge{{color:#8d1f25;background:#fce6e7}}.Blocked.badge{{color:#80570c;background:#fff0c9}}.NotRun.badge{{color:#435977;background:#e8edf6}}.NotApplicable.badge{{color:#555;background:#ededed}}.table-scroll{{overflow:auto}}table{{width:100%;border-collapse:collapse;text-align:left}}th,td{{padding:12px;border-bottom:1px solid #dce3df;vertical-align:top}}th{{background:#f4f7f5}}.metadata{{display:grid;grid-template-columns:minmax(110px,180px) minmax(0,1fr);gap:8px 20px;margin:0}}dt{{font-weight:700}}dd{{margin:0;min-width:0}}dd p{{margin:0}}.preserve{{white-space:pre-wrap;overflow-wrap:anywhere}}.two-column{{display:grid;grid-template-columns:1fr 1fr;gap:26px}}summary{{cursor:pointer;padding:20px 24px;display:flex;justify-content:space-between;gap:20px;font-weight:700}}summary::before{{content:'▾';color:#547264}}details:not([open]) summary::before{{content:'▸'}}summary>span:first-of-type{{flex:1}}.uat-body{{padding:0 24px 24px}}.screenshot-state{{border-bottom:1px solid #e2e8e4;padding-bottom:14px}}.evidence-grid{{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:16px}}figure{{margin:0;background:#f8faf9;border:1px solid #dce3df;border-radius:10px;overflow:hidden}}figure img{{display:block;width:100%;height:250px;object-fit:contain;background:#e9eeeb}}figcaption{{padding:12px;font-size:13px}}.evidence-file{{padding:16px;background:#f5f8f6;border:1px solid #dce3df;border-radius:10px}}.integrity{{padding:18px;border:2px solid #b54837;background:#fff2ee;color:#762b20;border-radius:10px;margin:16px 0;overflow-wrap:anywhere}}ul{{padding-left:24px}}li{{margin:6px 0}}.footer{{font-size:12px;color:#53665d}}@media(max-width:760px){{main{{padding:16px}}header,.card{{padding:18px}}h1{{font-size:24px}}.stats{{grid-template-columns:repeat(2,1fr)}}.two-column{{grid-template-columns:1fr;gap:8px}}.metadata{{grid-template-columns:1fr;gap:4px}}dd{{margin-bottom:12px}}summary{{flex-wrap:wrap;padding:16px}}.uat-body{{padding:0 16px 18px}}}}@media print{{body{{background:white}}main{{max-width:none;padding:0}}header,.card,.uat{{break-inside:avoid}}a{{color:#222}}figure img{{height:210px}}}}
</style></head><body><main>
<header><div class="eyebrow">FEAT-ADEN-003 · 执行证据</div><h1>{escape(title)}</h1><p>逐项展示真实输入记录。场景执行结果、用户接受决定、上线就绪为不同事实。</p><p><strong>用户决定：</strong></p>{rich_text(data['userDecision'])}<p class="muted">报告生成时间：{escape(generated)}</p></header>
<section class="card"><h2>执行概览 · 共 12 项</h2><div class="stats">{stats}</div><p>截图状态：{escape(screenshot_stats)}</p><p>关联实际图片共 {total_images} 次（同图支持多项时分别列示）。</p>{integrity_html}<h3>失败、阻塞和未执行项</h3>{attention_html}</section>
<section class="card"><h2>环境与版本</h2><div class="two-column"><section><h3>实际环境、数据和工具</h3>{rich_text(data['environment'])}</section><section><h3>版本与构建</h3>{rich_text(data['versions'])}</section></div><h3>共同限制</h3>{rich_text(data.get('limitations', '未提供共同限制；以各项限制为准。'))}</section>
<section class="card"><h2>全部验收项</h2><div class="table-scroll"><table><thead><tr><th>ID</th><th>目标</th><th>执行结果</th><th>截图状态</th></tr></thead><tbody>{''.join(rows)}</tbody></table></div></section>
{''.join(panels)}<footer class="card footer"><h2>输入与生成方式</h2><p>唯一输入：<a href="{escape(relative_link(source, output))}">{escape(source.name)}</a>；生成脚本：<a href="{escape(relative_link(Path(__file__).resolve(), output))}">生成验收报告.py</a>。</p><p>结果变化先更新原始验收记录，再重新生成报告。图片来自存在的实际文件；缺失路径明确提示。生成器只做静态结构检查，视觉回读、图片内容核对和用户决定由执行记录另行证明。</p><p>所有 12 项均默认展开，失败或未执行项不会被筛除。报告包含的原始页面文字以纯文本编码，不运行源站脚本。</p></footer>
</main></body></html>'''


def main() -> None:
    directory = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--input", type=Path, default=directory / "验收结果.json")
    parser.add_argument("--output", type=Path, default=directory.parent / "verification-report.html")
    parser.add_argument("--relative-images", action="store_true", help="实际图片用相对路径；默认嵌入真实图片字节以便单文件阅读")
    args = parser.parse_args()
    source = args.input.resolve()
    output = args.output.resolve()
    data = validate(json.loads(source.read_text(encoding="utf-8-sig")))
    report = generate(data, source, output, embed=not args.relative_images)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(report, encoding="utf-8")
    counts = Counter(item["result"] for item in data["items"])
    print(json.dumps({"output": str(output), "items": 12, "results": {key: counts[key] for key in RESULTS}, "visualInspection": "NotRun（需实际打开报告检查）"}, ensure_ascii=False))


if __name__ == "__main__":
    main()
