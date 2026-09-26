"""生成设计稿；全部商品、数量和图片均为合成占位内容，不连接业务服务。"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
FONT = 'C:/Windows/Fonts/msyh.ttc'
BOLD = 'C:/Windows/Fonts/msyhbd.ttc'
INK, MUTED, LINE, PAPER, SOFT = '#20252B', '#505963', '#9AA3AC', '#FFFFFF', '#F0F2F4'

def font(n=22, bold=False):
    return ImageFont.truetype(BOLD if bold else FONT, n)

def text(x, y, s, size=22, bold=False, color=INK):
    d.text((x, y), s, font=font(size, bold), fill=color)

def box(x, y, w, h, fill=PAPER, radius=8):
    d.rounded_rectangle((x, y, x+w, y+h), radius=radius, fill=fill, outline=LINE, width=2)

def button(x, y, w, label, primary=False):
    box(x, y, w, 46, INK if primary else PAPER)
    f = font(20, primary)
    tw = d.textbbox((0, 0), label, font=f)[2]
    d.text((x+(w-tw)/2, y+9), label, font=f, fill=PAPER if primary else INK)

def para(x, y, s, width, size=21, color=MUTED):
    line = ''
    for ch in s:
        if ch == '\n' or d.textlength(line+ch, font=font(size)) > width:
            text(x, y, line, size, color=color)
            y += size+12
            line = '' if ch == '\n' else ch
        else:
            line += ch
    if line:
        text(x, y, line, size, color=color)
        y += size+12
    return y

def image_slot(x, y, w, h, label='商品图片占位'):
    box(x, y, w, h, SOFT)
    d.line((x+15, y+15, x+w-15, y+h-15), fill='#C7CCD2', width=2)
    d.line((x+w-15, y+15, x+15, y+h-15), fill='#C7CCD2', width=2)
    f = font(18)
    tw = d.textlength(label, font=f)
    d.rectangle((x+(w-tw)/2-7,y+h/2-17,x+(w+tw)/2+7,y+h/2+18), fill=SOFT)
    text(x+(w-tw)/2, y+h/2-14, label, 18)

def start(title, subtitle):
    global im, d
    im = Image.new('RGB', (1600, 1080), '#F7F8FA')
    d = ImageDraw.Draw(im)
    text(40, 25, title, 32, True)
    text(40, 76, subtitle, 21, color=MUTED)
    d.line((40, 118, 1560, 118), fill=LINE, width=2)

def save(name):
    text(40, 1038, 'Aden · FEAT-ADEN-003 · v0.2 · 2026-09-26 · 静态线框图，数据为示例，功能尚未实现', 19, color=MUTED)
    im.save(ROOT / name)

start('01 / 打开详情页，点击采集', '用户自己搜索和浏览；插件只采集此刻打开的商品，不接管前面的浏览流程。')
box(40, 145, 1040, 845)
text(62, 163, 'Chrome / 京东商品详情页（结构示意）', 23, True)
box(62, 208, 994, 42, SOFT)
text(80, 214, '当前页面地址：京东商品详情页', 20)
image_slot(76, 282, 405, 320, '京东页面主图')
text(515, 290, '示例商品 A / 日常双肩包', 27, True)
text(515, 348, '展示价格  ¥ 199.00', 26)
text(515, 402, '已选规格：黑色 / 标准款', 22)
text(515, 448, '店铺：示例店铺', 22)
text(515, 499, '用户按自己的习惯浏览与选择规格', 20, color=MUTED)
for i in range(4): image_slot(76+i*105, 620, 92, 75, str(i+1))
box(76, 725, 960, 222, SOFT)
text(98, 744, '商品详情与参数（已加载区域）', 23, True)
para(98, 791, '内容较长时由用户自行向下浏览。尚未加载的图片、未展开的参数会标为缺失；插件不会自动滚动、切换规格或打开其他商品。', 900)
box(1104, 145, 456, 845)
text(1126, 167, 'Aden 商品采集', 26, True)
text(1126, 209, '已连接桌面端 · 当前工作空间', 20)
box(1126, 252, 410, 56, SOFT)
text(1140, 266, '保存到：默认采集库    更换', 20)
text(1126, 335, '已识别：示例商品 A', 23, True)
text(1126, 381, '当前规格：黑色 / 标准款', 21)
text(1126, 426, '包含：商品字段、图片、内容快照', 20)
para(1126, 470, '本次只保存已加载的商品内容。采集范围可在“设置”中调整。', 408, 20)
button(1126, 563, 410, '采集当前商品', True)
text(1126, 625, '设置', 20)
box(1126, 671, 410, 150, SOFT)
text(1140, 686, '反馈示例：商品内容已保存', 22, True)
text(1140, 727, '图片 6 / 8 已保存 · 2 张失败', 20)
text(1140, 765, '查看结果    重试失败图片', 20)
button(1126, 846, 410, '查看已采集商品')
text(1126, 914, '重复采集会新增快照版本', 20, color=MUTED)
save('01-详情页采集.png')

start('02 / 采集库：查看已保存的商品和图片', '集中整理多个手动采集的商品；再次采集保留版本，查看历史内容无需重新访问京东。')
box(40, 145, 1520, 845)
box(40, 145, 205, 845, SOFT)
text(63, 168, 'ADEN', 27, True)
text(63, 244, '任务中心', 22)
box(55, 293, 174, 51, PAPER)
text(69, 305, '商品采集', 22, True)
text(69, 372, '已采集商品', 20)
text(69, 423, '回收站（2）', 20)
text(63, 913, '当前工作空间', 19)
text(270, 166, '已采集商品', 29, True)
text(270, 210, '默认采集库 · 12 个商品 · 2 个待补全', 20, color=MUTED)
button(1143, 171, 165, '手动新增')
button(1326, 171, 204, '导出所选（2）', True)
box(270, 260, 433, 47)
text(288, 271, '搜索库内标题 / 商品 ID / 标签', 20, color=MUTED)
button(717, 260, 150, '状态：全部')
text(270, 332, '已选 2 项', 21, True)
button(426, 321, 150, '移入回收站')
text(618, 332, '全选仅作用于当前页', 19, color=MUTED)
box(270, 389, 706, 452)
text(289, 404, '选择     商品 / 当前快照', 20, True)
text(699, 404, '图片 / 保存状态', 20, True)
rows = [('☑', '示例商品 A / 双肩包', '¥199.00 · 黑色 / 标准款', '6 / 8 张', '部分保存'), ('☑', '示例商品 B / 运动水杯', '¥69.00 · 白色 / 500mL', '5 / 5 张', '已保存'), ('□', '示例商品 C / 手动新增', '价格未填 · 手动补录', '2 / 2 张', '待核对')]
for i, (check, title, desc, count, status) in enumerate(rows):
    yy = 453+i*126
    d.line((271, yy-7, 975, yy-7), fill=LINE)
    box(289, yy+30, 22, 22, PAPER, 2)
    if i < 2:
        d.line((293, yy+40, 299, yy+47, 308, yy+34), fill=INK, width=3)
    image_slot(335, yy+8, 80, 80, '主图')
    text(433, yy+5, title, 20, True)
    text(433, yy+43, desc, 18, color=MUTED)
    text(720, yy+5, count, 20)
    text(720, yy+42, status, 20)
text(282, 870, '共 12 个商品 · 图中仅示意 3 条    第 1 页', 20)
text(282, 928, '移入回收站后可恢复；默认导出不包含回收站记录。', 19, color=MUTED)
box(1000, 260, 530, 695, SOFT)
text(1020, 278, '示例商品 A', 25, True)
text(1020, 320, '快照 v2 · 09-26 14:32    切换版本', 19)
text(1020, 368, '商品信息  |  图片（6/8）  |  内容快照', 19, True)
image_slot(1022, 420, 226, 166, '已保存主图')
image_slot(1262, 420, 244, 166, '详情图片')
para(1020, 610, '标题、价格、规格、参数、店铺和图片均保留采集时的内容。\n详情图片：2 张保存失败；已存图片可直接回看。', 480, 20)
button(1020, 770, 226, '重试失败图片')
button(1262, 770, 244, '补充信息')
text(1020, 844, '原始采集内容只读；补充信息单独标注。', 19)
text(1020, 896, '打开来源页    查看保存范围', 19)
save('02-采集库与商品详情.png')

start('03 / 新增、删除与导出', '这些操作只影响采集库；每一步都能看清作用对象、保存结果和图片缺失情况。')
for x in (40, 556, 1072): box(x, 145, 488, 845)
text(62, 165, 'A / 手动新增', 26, True)
text(62, 221, '商品标题 *', 21)
box(62, 259, 444, 48); text(76, 270, '输入商品标题', 20, color=MUTED)
text(62, 332, '来源链接（选填）', 21)
box(62, 370, 444, 48); text(76, 381, '粘贴链接仅作来源记录', 20, color=MUTED)
text(62, 442, '价格与规格（选填）', 21)
box(62, 480, 444, 48)
text(62, 554, '商品图片', 21)
image_slot(62, 597, 126, 110, '已上传')
image_slot(204, 597, 126, 110, '已上传')
button(346, 621, 160, '添加图片')
para(62, 747, '标记为“手动新增”，不会自动访问链接。缺失价格留空，保存前检查必填标题。', 430, 20)
button(62, 901, 150, '取消'); button(228, 901, 278, '保存到采集库', True)
text(578, 165, 'B / 移入回收站', 26, True)
text(578, 228, '将所选 2 个商品移入回收站？', 22, True)
para(578, 285, '包含这些商品的全部快照和图片。它们将从正常列表及新的默认导出中移除。', 438, 21)
box(578, 402, 444, 150, SOFT)
text(596, 419, '示例商品 A / 双肩包', 20)
text(596, 461, '示例商品 B / 运动水杯', 20)
text(596, 504, '以前已下载的文件仍会保留。', 19)
button(578, 603, 150, '取消'); button(744, 603, 278, '确认移入回收站', True)
box(578, 705, 444, 238, SOFT)
text(596, 724, '操作后：已移入回收站 · 撤销', 20, True)
para(596, 778, '回收站内可逐项或批量恢复。\n首版不自动永久清除，清理策略另行配置；再次采集时先提示恢复。', 408, 20)
text(1094, 165, 'C / 导出', 26, True)
text(1094, 225, '范围：所选 2 个商品', 22)
text(1094, 270, '版本：导出创建时的当前快照', 20)
text(1094, 327, '● Excel + 图片文件包（ZIP）', 22, True)
text(1094, 372, '○ 仅 Excel（可选嵌入已存主图）', 20)
box(1094, 430, 444, 239, SOFT)
text(1112, 448, '导出包内容', 21, True)
text(1112, 491, '商品表.xlsx', 20)
text(1112, 531, '图片 / 商品ID / 快照ID / …', 20)
text(1112, 571, '内容快照 / …', 20)
text(1112, 611, '导出说明 / 缺失清单', 20)
para(1094, 706, '示例：2 个商品、11 张已存图片、2 张缺失。导出只使用已保存内容，不重新访问京东。', 434, 20)
text(1094, 823, '输出位置：由用户选择', 20)
button(1094, 901, 150, '取消'); button(1260, 901, 278, '生成导出包', True)
save('03-新增删除与导出.png')
print('Generated 3 wireframes (1600x1080)')
