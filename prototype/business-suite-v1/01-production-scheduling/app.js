(() => {
  'use strict';
  const U = window.UI;
  const STORAGE_KEY = 'business-suite-production-v1.2';
  const clone = value => JSON.parse(JSON.stringify(value));
  const esc = value => U.escape(String(value ?? ''));
  const icon = name => U.icon(name);
  const money = value => Number(value).toLocaleString('zh-CN');
  const pages = [
    ['schedule', '排程工作台', 'chart-gantt'], ['orders', '生产订单', 'clipboard-list'],
    ['resources', '资源与日历', 'factory'], ['validation', '数据校验', 'shield-check'],
    ['versions', '模拟与发布', 'git-branch'], ['execution', '现场执行', 'activity'], ['rules', '排程规则', 'sliders-horizontal']
  ];
  const initial = {
    resources: [
      { id:'CNC-01', name:'立式加工中心', type:'铣削', active:true, efficiency:96 },
      { id:'CNC-02', name:'立式加工中心', type:'铣削', active:true, efficiency:94 },
      { id:'CNC-03', name:'五轴加工中心', type:'铣削', active:true, efficiency:98 },
      { id:'LATHE-01', name:'数控车床', type:'车削', active:true, efficiency:95 },
      { id:'LATHE-02', name:'数控车床', type:'车削', active:true, efficiency:92 },
      { id:'GRIND-01', name:'精密磨床', type:'磨削', active:true, efficiency:97 }
    ],
    orders: [
      { id:'MO-260910-001', product:'伺服电机端盖', customer:'科锐自动化', qty:240, type:'铣削', hours:4, due:12, priority:'紧急', material:true, family:'铝合金', selected:true },
      { id:'MO-260910-002', product:'减速器壳体', customer:'博远机器人', qty:180, type:'铣削', hours:6, due:18, priority:'高', material:true, family:'铝合金', selected:true },
      { id:'MO-260910-003', product:'精密传动轴', customer:'恒川精工', qty:360, type:'车削', hours:5, due:12, priority:'高', material:true, family:'钢件', selected:true },
      { id:'MO-260910-004', product:'联轴器法兰', customer:'科锐自动化', qty:120, type:'车削', hours:4, due:20, priority:'普通', material:true, family:'钢件', selected:true },
      { id:'MO-260910-005', product:'导轨滑块', customer:'博远机器人', qty:320, type:'磨削', hours:5, due:24, priority:'普通', material:true, family:'钢件', selected:true },
      { id:'MO-260910-006', product:'散热支架', customer:'禾芯电子', qty:500, type:'铣削', hours:4, due:24, priority:'普通', material:true, family:'铝合金', selected:true },
      { id:'MO-260910-007', product:'气缸连接座', customer:'恒川精工', qty:200, type:'铣削', hours:3, due:30, priority:'普通', material:true, family:'铝合金', selected:true },
      { id:'MO-260910-008', product:'滚珠丝杆轴套', customer:'瑞泽工业', qty:160, type:'车削', hours:4, due:30, priority:'普通', material:true, family:'铜件', selected:true },
      { id:'MO-260910-009', product:'轻量化底板', customer:'禾芯电子', qty:280, type:'铣削', hours:5, due:36, priority:'高', material:false, family:'铝合金', selected:false },
      { id:'MO-260910-010', product:'定位销组件', customer:'瑞泽工业', qty:400, type:'磨削', hours:3, due:36, priority:'普通', material:true, family:'钢件', selected:true }
    ],
    tasks: [
      {id:'OP-001',order:'MO-260910-001',resource:'CNC-01',start:0,hours:4,locked:true},
      {id:'OP-002',order:'MO-260910-002',resource:'CNC-01',start:3,hours:6,locked:false},
      {id:'OP-003',order:'MO-260910-003',resource:'LATHE-01',start:0,hours:5,locked:false},
      {id:'OP-004',order:'MO-260910-004',resource:'LATHE-02',start:4,hours:4,locked:false},
      {id:'OP-005',order:'MO-260910-005',resource:'GRIND-01',start:0,hours:5,locked:false},
      {id:'OP-006',order:'MO-260910-006',resource:'CNC-02',start:6,hours:4,locked:false},
      {id:'OP-007',order:'MO-260910-007',resource:'CNC-03',start:12,hours:3,locked:false},
      {id:'OP-008',order:'MO-260910-008',resource:'LATHE-01',start:16,hours:4,locked:false}
    ],
    rule:'due', keepLocked:true, draftName:'交期优先 · 调整方案', draftUpdated:'09-10 09:42',
    versions:[], publishedId:'V001', execution:{}, events:[{time:'09:20',text:'计划员 林悦 创建了当前排程草稿。'}], serial:10
  };
  const baseline = clone(initial.tasks);
  baseline.find(t => t.id === 'OP-002').resource = 'CNC-02';
  baseline.find(t => t.id === 'OP-002').start = 0;
  initial.versions = [{id:'V001',name:'生产基线 · 9 月 10 日',time:'09-10 08:50',status:'published',tasks:baseline}];
  let state = U.load(STORAGE_KEY, initial);
  if (!state || !Array.isArray(state.orders)) state = clone(initial);
  let page = location.hash.slice(1) || 'schedule';
  let query = '', orderStatus = 'all', resourceFilter = 'all', executionFilter = 'all', compareId = state.publishedId;
  const content = document.getElementById('content');
  const getOrder = id => state.orders.find(o => o.id === id);
  const getResource = id => state.resources.find(r => r.id === id);
  const now = () => new Date().toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit'});
  const stamp = () => '09-10 ' + now();
  const timeLabel = (value,end=false) => {
    let day = Math.floor(value / 12), hour = value % 12 + 8;
    if(end && value > 0 && value % 12 === 0){day--;hour=20;}
    return `09/${10+day} ${String(hour).padStart(2,'0')}:00`;
  };
  const inputTime = value => {
    const day=Math.floor(value/12), hour=value%12+8;
    return `2026-09-${String(10+day).padStart(2,'0')}T${String(hour).padStart(2,'0')}:00`;
  };
  const toSlot = value => {
    const m=/^2026-09-(\d{2})T(\d{2}):(\d{2})$/.exec(value);
    if(!m || Number(m[3]) !== 0 || Number(m[2]) < 8 || Number(m[2]) >= 20) return NaN;
    return (Number(m[1])-10)*12 + Number(m[2])-8;
  };
  const persist = () => U.save(STORAGE_KEY,state);
  const record = text => { state.events.unshift({time:now(),text});state.events=state.events.slice(0,50); };
  const dirty = () => {state.draftUpdated=stamp();persist();};
  const badge = (text,type='neutral') => `<span class="badge ${type}">${esc(text)}</span>`;
  const btn = (label,action,ic='arrow-right',type='secondary',extra='') => `<button class="btn ${type}" data-action="${action}" title="${esc(label)}" ${type.includes('icon-btn')?'aria-label="'+esc(label)+'"':''} ${extra}>${icon(ic)}${type.includes('icon-btn')?'':label}</button>`;
  const title = (name,description,actions='') => `<div class="page-heading"><div><h1 class="page-title">${name}</h1><p class="page-description">${description}</p></div><div class="actions">${actions}</div></div>`;
  const metric = (label,value,foot) => `<div class="metric"><div class="metric-label">${label}</div><div class="metric-value">${value}</div><div class="metric-foot">${foot}</div></div>`;
  const published = () => state.versions.find(v => v.id === state.publishedId);
  function issues(tasks=state.tasks){
    const list=[];
    for(const t of tasks){
      const o=getOrder(t.order),r=getResource(t.resource);
      if(!o || !r){list.push({level:'error',title:'关联数据缺失',detail:t.id+' 缺少订单或设备。',task:t.id});continue;}
      if(!o.material)list.push({level:'error',title:'物料未齐套',detail:`${o.product} · ${o.id} 已进入计划，铝板原料尚未到料。`,order:o.id});
      if(!r.active)list.push({level:'error',title:'设备已停用',detail:`${r.id} 上仍有 ${o.product} 的排程任务。`,task:t.id});
      if(r.type!==o.type)list.push({level:'error',title:'设备能力不匹配',detail:`${o.product} 需要${o.type}设备，当前为${r.type}。`,task:t.id});
      if(t.start<0 || t.start+t.hours>36 || t.start%12+t.hours>12)list.push({level:'error',title:'超出设备工作日历',detail:`${o.product} 跨越 20:00 下班时间或超出 3 天计划范围。`,task:t.id});
      if(t.start+t.hours>o.due)list.push({level:'warning',title:'预计交期延迟',detail:`${o.product} 计划完成于 ${timeLabel(t.start+t.hours,true)}，承诺交期 ${timeLabel(o.due,true)}。`,task:t.id});
    }
    for(let i=0;i<tasks.length;i++)for(let j=i+1;j<tasks.length;j++){
      const a=tasks[i],b=tasks[j];
      if(a.resource===b.resource && a.start<b.start+b.hours && b.start<a.start+a.hours)list.push({level:'error',title:'设备工时重叠',detail:`${a.resource} · ${getOrder(a.order)?.product} 与 ${getOrder(b.order)?.product} 的计划区间冲突。`,task:b.id});
    }
    for(const o of state.orders){
      if(!tasks.some(t=>t.order===o.id))list.push({level:'warning',title:o.material?'订单尚未排入计划':'待排订单缺料',detail:`${o.id} · ${o.product}${o.material?'，可加入下一轮排程。':'，铝板原料待到料，自动排程将暂时跳过。'}`,order:o.id});
    }
    return list;
  }
  function counts(tasks=state.tasks){
    const errors=issues(tasks).filter(i=>i.level==='error').length;
    const late=tasks.filter(t=>t.start+t.hours>getOrder(t.order)?.due).length;
    const hours=tasks.reduce((s,t)=>s+t.hours,0);
    const cap=state.resources.filter(r=>r.active).length*36;
    return {errors,late,hours,util:cap?Math.round(hours/cap*100):0,scheduled:tasks.length,pending:state.orders.length-tasks.length};
  }
  function render(){
    if(!pages.some(p=>p[0]===page))page='schedule';
    const current=pages.find(p=>p[0]===page);
    document.getElementById('breadcrumb-current').textContent=current[1];
    const c=counts();
    document.getElementById('navigation').innerHTML=pages.map(([id,label,ic])=>`<a class="nav-item ${page===id?'active':''}" href="#${id}">${icon(ic)}<span>${label}</span>${id==='validation'&&c.errors?`<span class="nav-count">${c.errors}</span>`:''}</a>`).join('');
    content.innerHTML=({schedule:renderSchedule,orders:renderOrders,resources:renderResources,validation:renderValidation,versions:renderVersions,execution:renderExecution,rules:renderRules})[page]();
    U.icons();
  }
  function renderSchedule(){
    const c=counts(),visible=state.resources.filter(r=>resourceFilter==='all'||r.type===resourceFilter);
    const conflicted=new Set(issues().filter(i=>i.level==='error'&&i.task).map(i=>i.task));
    const pending=state.orders.filter(o=>!state.tasks.some(t=>t.order===o.id));
    return title('排程工作台','苏州工厂 / 机加工车间',btn('导出计划','export','download')+btn('生成排程','generate','sparkles','primary'))+
      `<div class="plan-info">${badge('草稿方案','info')}<strong>${esc(state.draftName)}</strong><span>更新于 ${esc(state.draftUpdated)}</span><span>·</span><span>正式版本 ${esc(state.publishedId)}</span></div>`+
      `<div class="metrics">${metric('已排生产任务',`${c.scheduled}<small> / ${state.orders.length}</small>`,`${c.pending} 个订单待排`)}${metric('计划按期完成',`${c.scheduled?Math.round((c.scheduled-c.late)/c.scheduled*100):0}<small>%</small>`,`${c.late} 个任务预计延期`)}${metric('设备计划负荷',`${c.util}<small>%</small>`,`${c.hours} 小时 / 三天可用产能`)}${metric('待处理冲突',c.errors,c.errors?'发布前需要解决':'当前方案可进入发布检查')}</div>`+
      `<div class="schedule-notice ${c.errors?'':'clear'}"><div>${icon(c.errors?'triangle-alert':'circle-check')}<span>${c.errors?`当前方案有 <strong>${c.errors} 项阻断冲突</strong>。调整任务或重新生成后，可重新校验。`:`当前方案无阻断冲突。${c.pending?`${c.pending} 个订单未排入，将在发布前列明。`:'可保存版本并发布至现场。'}`}</span></div>${btn(c.errors?'查看冲突':'模拟与发布',c.errors?'go-validation':'go-versions','arrow-right','ghost')}</div>`+
      `<section class="workbench"><div class="workbench-toolbar"><div class="workbench-heading">资源甘特图 <small>${visible.length} 台设备</small></div><div class="actions"><select aria-label="设备类型" data-filter="resource"><option value="all">全部设备</option>${['铣削','车削','磨削'].map(v=>`<option ${resourceFilter===v?'selected':''}>${v}</option>`).join('')}</select><div class="date-range">${icon('calendar-days')}09.10 周四 — 09.12 周六</div></div></div><div class="workbench-toolbar"><div class="legend"><span><i></i>铣削任务</span><span><i class="blue"></i>车削任务</span><span><i class="amber"></i>磨削任务</span><span><i class="red"></i>排程冲突</span><span>${icon('lock-keyhole')}已锁定</span></div><span class="muted" style="font-size:11px">工作日历 08:00 – 20:00</span></div>`+
      `<div class="gantt-scroll"><div class="gantt"><div class="gantt-days"><div class="gantt-label">设备 / 工作中心</div><div class="days-grid"><div>09.10 周四 <span class="muted">今天</span></div><div>09.11 周五</div><div>09.12 周六</div></div></div><div class="gantt-header"><div class="gantt-label">排程粒度 · 1 小时</div><div class="hours-grid">${Array.from({length:36},(_,i)=>`<span>${i%3===0?String(i%12+8).padStart(2,'0'):''}</span>`).join('')}</div></div>${visible.map(r=>`<div class="gantt-row ${r.active?'':'offline'}"><div class="gantt-label"><div class="resource-name">${icon('cpu')}${r.id}</div><div class="resource-meta"><span>${r.name}</span><span>${state.tasks.filter(t=>t.resource===r.id).reduce((s,t)=>s+t.hours,0)}h</span></div></div><div class="row-track">${state.tasks.filter(t=>t.resource===r.id).map(t=>{const o=getOrder(t.order);return `<button class="gantt-task ${o.type==='车削'?'blue':o.type==='磨削'?'amber':''} ${conflicted.has(t.id)?'conflict':''} ${t.locked?'locked':''}" style="left:calc(${t.start/36*100}% + 2px);width:calc(${t.hours/36*100}% - 4px)" data-action="edit-task" data-id="${t.id}" title="${esc(o.product)} · ${timeLabel(t.start)} 至 ${timeLabel(t.start+t.hours,true)}"><strong>${esc(o.product)}</strong><small>${t.locked?icon('lock-keyhole'):''}${o.qty} 件 · ${t.hours}h</small></button>`;}).join('')}</div></div>`).join('')}</div></div><div class="gantt-footer"><span>${state.tasks.length} 项任务 · ${c.hours} 工时 · 本地草稿已保存</span><span>有限产能 · 设备单任务占用 · 交期优先</span></div></section>`+
      `<div class="lower-grid"><section class="section"><div class="section-heading"><h2>待排订单 <span class="pill-count">${pending.length}</span></h2>${btn('查看全部','go-orders','arrow-up-right','ghost')}</div><div class="queue-list">${pending.length?pending.map(o=>`<div class="queue-row"><span class="queue-mark">${icon('package')}</span><div><strong>${esc(o.product)}</strong><small>${o.id} · ${o.qty} 件 · ${o.hours}h</small></div>${badge(o.material?'待排程':'缺料',o.material?'neutral':'warning')}${btn('查看','view-order','arrow-up-right','icon-btn ghost',`data-id="${o.id}" aria-label="查看${esc(o.product)}"`)}</div>`).join(''):'<div class="empty-state">所有生产订单均已排入方案</div>'}</div></section><section class="section"><div class="section-heading"><h2>设备负荷</h2><span class="muted" style="font-size:11px">三天窗口 / 36 小时</span></div>${state.resources.map(r=>{const p=Math.round(state.tasks.filter(t=>t.resource===r.id).reduce((s,t)=>s+t.hours,0)/36*100);return `<div class="util-row"><span>${r.id}</span><div class="util-bar"><i class="${p>75?'busy':''}" style="width:${Math.min(100,p)}%"></i></div><span>${p}%</span></div>`;}).join('')}</section></div>`;
  }
  function orderMatches(o){
    const scheduled=state.tasks.some(t=>t.order===o.id);
    return (!query||`${o.id} ${o.product} ${o.customer}`.toLowerCase().includes(query.toLowerCase()))&&(orderStatus==='all'||orderStatus==='scheduled'&&scheduled||orderStatus==='pending'&&!scheduled||orderStatus==='shortage'&&!o.material);
  }
  function renderOrders(){
    const list=state.orders.filter(orderMatches), selected=state.orders.filter(o=>o.selected).length;
    return title('生产订单','从订单池确定本轮计划范围，订单与排程任务保持关联。',btn('导出订单','export-orders','download')+btn('新建订单','new-order','plus','primary'))+
      `<div class="toolbar"><div class="search-field">${icon('search')}<input id="order-search" aria-label="搜索订单" placeholder="搜索订单号、产品或客户" value="${esc(query)}" data-filter="query"></div><select aria-label="订单状态" data-filter="orderStatus">${[['all','全部状态'],['scheduled','已排程'],['pending','待排程'],['shortage','物料短缺']].map(([v,l])=>`<option value="${v}" ${orderStatus===v?'selected':''}>${l}</option>`).join('')}</select><div style="flex:1"></div><span class="muted" style="font-size:12px">已选 ${selected} 项</span>${btn('排入方案','generate','chart-gantt','primary')}</div>`+
      `<div class="table-wrap"><table class="data-table"><thead><tr><th><input type="checkbox" aria-label="选中全部筛选订单" data-action="select-all" ${list.length&&list.every(o=>o.selected)?'checked':''}></th><th>生产订单 / 产品</th><th>客户</th><th>数量</th><th>工艺 / 工时</th><th>承诺交期</th><th>优先级</th><th>物料</th><th>计划状态</th><th></th></tr></thead><tbody>${list.map(o=>{const t=state.tasks.find(t=>t.order===o.id);return `<tr><td><input type="checkbox" aria-label="选择 ${o.id}" data-action="select-order" data-id="${o.id}" ${o.selected?'checked':''}></td><td><button class="order-link" data-action="view-order" data-id="${o.id}">${o.id}</button><div class="table-cell-sub">${esc(o.product)}</div></td><td>${esc(o.customer)}</td><td class="mono">${money(o.qty)}</td><td>${o.type}<div class="table-cell-sub">${o.hours} 小时</div></td><td class="mono">${timeLabel(o.due,true)}</td><td>${badge(o.priority,o.priority==='紧急'?'danger':o.priority==='高'?'warning':'neutral')}</td><td>${badge(o.material?'已齐套':'缺料',o.material?'success':'warning')}</td><td>${badge(t?'已排程':'待排程',t?'info':'neutral')}</td><td>${btn('详情','view-order','chevron-right','icon-btn ghost',`data-id="${o.id}"`)}</td></tr>`;}).join('')||'<tr><td colspan="10"><div class="empty-state">没有符合条件的订单</div></td></tr>'}</tbody></table></div><div class="table-footer"><span>共 ${list.length} 条订单 · ${list.reduce((s,o)=>s+o.qty,0).toLocaleString()} 件</span><span>数据来源：示例订单池</span></div>`;
  }
  function renderResources(){
    return title('资源与日历','机加工设备以单任务有限产能排程，工作班次为每日 08:00–20:00。',btn('查看计划','go-schedule','chart-gantt'))+
      `<div class="metrics">${metric('设备总数',state.resources.length,'3 类工艺能力')}${metric('可用设备',state.resources.filter(r=>r.active).length,'停用后将触发计划校验')}${metric('每日可用工时',state.resources.filter(r=>r.active).length*12,'12 小时 / 设备')}${metric('计划窗口','3<small> 天</small>','2026.09.10 – 2026.09.12')}</div><section class="section"><div class="section-heading"><h2>设备工作日历</h2><span class="muted" style="font-size:12px">统一班次 · 示例机加工车间</span></div><div class="table-wrap"><div class="capacity-calendar"><div class="calendar-head">设备资源</div><div class="calendar-head">09.10 周四</div><div class="calendar-head">09.11 周五</div><div class="calendar-head">09.12 周六</div>${state.resources.map(r=>`<div class="calendar-resource"><strong>${r.id}</strong><small>${r.name} · ${r.type}</small></div>${[0,1,2].map(()=>`<div><div class="shift-bar ${r.active?'':'off'}"><span>${r.active?'08:00 — 20:00':'设备停用'}</span><strong>${r.active?'12h':'0h'}</strong></div></div>`).join('')}`).join('')}</div></div></section><div class="resource-toolbar"><h2 style="font-size:14px">设备台账</h2><span class="muted" style="font-size:12px">停用设备不会删除已有任务</span></div><div class="table-wrap"><table class="data-table"><thead><tr><th>设备编码</th><th>设备名称</th><th>工艺能力</th><th>当前计划工时</th><th>设备状态</th><th>操作</th></tr></thead><tbody>${state.resources.map(r=>`<tr><td class="mono">${r.id}</td><td>${r.name}</td><td>${badge(r.type,'neutral')}</td><td>${state.tasks.filter(t=>t.resource===r.id).reduce((s,t)=>s+t.hours,0)} h</td><td>${badge(r.active?'可用':'停用',r.active?'success':'neutral')}</td><td>${btn(r.active?'停用设备':'恢复可用','toggle-resource',r.active?'pause':'play','ghost',`data-id="${r.id}"`)}</td></tr>`).join('')}</tbody></table></div>`;
  }
  function renderValidation(){
    const list=issues(), errors=list.filter(i=>i.level==='error'),warnings=list.filter(i=>i.level==='warning');
    return title('数据校验','校验范围：订单齐套、设备能力、工作日历、产能占用与交期。',btn('重新校验','recheck','refresh-cw')+btn('返回排程','go-schedule','chart-gantt','primary'))+
      `<div class="metrics">${metric('阻断问题',errors.length,'必须解决后才能发布')}${metric('风险提醒',warnings.length,'发布前展示未排订单与延期')}${metric('订单覆盖',state.orders.length,'订单、物料与工艺关系')}${metric('设备覆盖',state.resources.length,'日历与有限产能')}</div><section class="section"><div class="section-heading"><h2>检查结果</h2>${badge(errors.length?'暂不可发布':'发布条件满足',errors.length?'danger':'success')}</div>${list.length?list.sort((a,b)=>a.level.localeCompare(b.level)).map(i=>`<div class="validation-line ${i.level}">${icon(i.level==='error'?'circle-x':'triangle-alert')}<div><h3>${i.title} ${badge(i.level==='error'?'阻断':'提醒',i.level==='error'?'danger':'warning')}</h3><p>${esc(i.detail)}</p></div>${i.task?btn('调整任务','edit-task','pencil','secondary',`data-id="${i.task}"`):btn('查看订单','view-order','arrow-up-right','secondary',`data-id="${i.order}"`)}</div>`).join(''):`<div class="validation-empty">${icon('shield-check')}<h3>所有检查通过</h3><p>当前任务满足设备能力、物料和工作日历约束。</p></div>`}</section><div class="schedule-overview">${icon('info')}交期与未排订单为风险提醒；设备重叠、能力不符、未齐套的已排任务和日历越界为发布阻断。</div>`;
  }
  function renderVersions(){
    const base=state.versions.find(v=>v.id===compareId)||published(), tasks=state.tasks;
    const allIds=[...new Set([...tasks,...base.tasks].map(t=>t.id))];
    const rows=allIds.map(id=>({a:base.tasks.find(t=>t.id===id),b:tasks.find(t=>t.id===id)}));
    const changed=rows.filter(({a,b})=>!a||!b||a.start!==b.start||a.resource!==b.resource||a.hours!==b.hours);
    const c=counts();
    return title('模拟与发布','保留正式基线，逐项审查当前草稿的计划变化。',btn('保存模拟版本','save-version','save')+btn('确认发布','publish','send','primary'))+
      `<div class="plan-info">${badge('当前草稿','info')}<strong>${esc(state.draftName)}</strong><span>对比</span>${badge(base.id,base.id===state.publishedId?'success':'neutral')}<strong>${esc(base.name)}</strong></div><div class="version-strip">${state.versions.map(v=>`<button class="version-choice ${base.id===v.id?'active':''}" data-action="compare-version" data-id="${v.id}">${icon(v.id===state.publishedId?'circle-check':'git-branch')}<span><strong>${v.id} · ${esc(v.name)}</strong><small>${v.time} · ${v.id===state.publishedId?'现场生效':v.status==='published'?'历史发布':'模拟快照'}</small></span></button>`).join('')}</div><div class="compare-summary"><div>计划任务<strong>${base.tasks.length} → ${tasks.length}</strong></div><div>变化任务<strong>${changed.length}</strong></div><div>设备调换<strong>${changed.filter(r=>r.a&&r.b&&r.a.resource!==r.b.resource).length}</strong></div><div>当前阻断<strong style="color:${c.errors?'#b25a4f':'#167d63'}">${c.errors}</strong></div></div>`+
      `<section class="section"><div class="section-heading"><h2>任务差异</h2><span class="muted" style="font-size:12px">时间基于 08:00–20:00 工作日历</span></div><div class="table-wrap"><table class="data-table"><thead><tr><th>任务 / 产品</th><th>变化</th><th>基线设备</th><th>草稿设备</th><th>基线开始</th><th>草稿开始</th><th>操作</th></tr></thead><tbody>${changed.map(({a,b})=>`<tr><td>${esc(getOrder((b||a).order)?.product)}<div class="table-cell-sub">${(b||a).id}</div></td><td>${badge(!a?'新增':!b?'移除':'调整',!a?'success':!b?'danger':'warning')}</td><td class="mono">${a?.resource||'—'}</td><td class="mono">${b?.resource||'—'}</td><td class="mono">${a?timeLabel(a.start):'—'}</td><td class="mono">${b?timeLabel(b.start):'—'}</td><td>${b?btn('查看','edit-task','arrow-up-right','icon-btn ghost',`data-id="${b.id}"`):'—'}</td></tr>`).join('')||'<tr><td colspan="7"><div class="empty-state">当前草稿与所选版本无排期差异</div></td></tr>'}</tbody></table></div></section><section class="section" style="margin-top:28px"><div class="section-heading"><h2>计划操作记录</h2></div>${state.events.slice(0,8).map(e=>`<div class="event-row"><time>${e.time}</time><span>${esc(e.text)}</span></div>`).join('')}</section>`;
  }
  function executionStatus(task){return state.execution[task.id]?.status||'ready';}
  function renderExecution(){
    const plan=published(),list=plan.tasks.filter(t=>executionFilter==='all'||executionStatus(t)===executionFilter);
    const meta={ready:['待开工','neutral'],running:['进行中','info'],paused:['已暂停','warning'],done:['已完工','success']};
    return title('现场执行',`正式计划 ${plan.id} · ${plan.name}`,btn('查看正式基线','go-versions','git-branch'))+
      `<div class="metrics">${metric('待开工',plan.tasks.filter(t=>executionStatus(t)==='ready').length,'准备设备与工件')}${metric('正在生产',plan.tasks.filter(t=>executionStatus(t)==='running').length,'开工状态已记录')}${metric('暂停任务',plan.tasks.filter(t=>executionStatus(t)==='paused').length,'保留已完成数量')}${metric('已完工',plan.tasks.filter(t=>executionStatus(t)==='done').length,'按订单数量完工')}</div><div class="toolbar"><div class="segmented">${[['all','全部'],['ready','待开工'],['running','进行中'],['paused','已暂停'],['done','已完工']].map(([id,l])=>`<button class="tab ${executionFilter===id?'active':''}" data-action="execution-filter" data-id="${id}">${l}</button>`).join('')}</div></div><div class="execute-list">${list.map(t=>{const o=getOrder(t.order),s=executionStatus(t),e=state.execution[t.id];return `<article class="execute-item"><div class="execute-item-head"><span class="mono muted" style="font-size:11px">${t.id}</span>${badge(meta[s][0],meta[s][1])}</div><h3>${esc(o.product)}</h3><p>${o.id}</p><div class="execute-meta"><div>生产设备<strong>${t.resource}</strong></div><div>计划数量<strong>${o.qty} 件</strong></div><div>计划开始<strong>${timeLabel(t.start)}</strong></div><div>计划完成<strong>${timeLabel(t.start+t.hours,true)}</strong></div></div>${s==='paused'?`<div class="inline-warning">暂停原因：${esc(e?.reason||'现场等待')}</div>`:''}${s==='done'?`<p>已记录 ${o.qty} 件 · ${e?.time||''} 完工</p>`:''}<div class="execute-actions">${s==='ready'?btn('开工','start-task','play','primary',`data-id="${t.id}"`):s==='running'?btn('暂停','pause-task','pause','secondary',`data-id="${t.id}"`)+btn('完工','complete-task','check','primary',`data-id="${t.id}"`):s==='paused'?btn('恢复生产','resume-task','play','primary',`data-id="${t.id}"`):badge('生产已完成','success')}</div></article>`;}).join('')||'<div class="empty-state">当前没有此状态的生产任务</div>'}</div>`;
  }
  function renderRules(){
    return title('排程规则','确定本轮演示排程的排序目标，硬约束始终独立校验。',btn('保存规则','save-rules','save','primary'))+
      `<div class="rules-layout"><section class="section"><div class="section-heading"><h2>排程优先目标</h2></div>${[['due','交期优先','按承诺交期从近到远安排，同交期时优先处理紧急订单。'],['priority','订单优先级','先安排紧急与高优先级订单，再按交期排序，可能增加普通订单延期。'],['short','短工时优先','先安排标准工时较短的任务，便于演示多订单的快速周转。']].map(([v,l,d])=>`<label class="rule-option"><input type="radio" name="rule" value="${v}" ${state.rule===v?'checked':''}><span><strong>${l}</strong><p>${d}</p></span></label>`).join('')}<div class="section-heading" style="margin-top:28px"><h2>计划稳定性</h2></div><label class="rule-option"><input type="checkbox" id="keep-locks" ${state.keepLocked?'checked':''}><span><strong>保留人工锁定任务</strong><p>保留锁定任务的设备与开始时间，仅调整其余已选订单。现场已开工任务始终保留。</p></span></label><div class="section-heading" style="margin-top:28px"><h2>固定硬约束</h2></div>${['同一台设备同一时间仅执行一个任务','设备工艺能力必须匹配订单工序','已排任务必须满足物料齐套','单个任务必须完整落在每日 08:00–20:00 班次内'].map(t=>`<div class="validation-line success" style="padding:13px 0">${icon('check')}<div><h3>${t}</h3></div>${badge('必选','success')}</div>`).join('')}</section><aside class="rules-facts"><h3>当前示例模型</h3><p>行业：离散机加工<br>排程对象：订单的单道关键工序<br>资源：设备<br>计划粒度：1 小时<br>计划范围：3 个工作日</p><h3 style="margin-top:25px">演示算法</h3><p>根据所选目标对订单排序，在匹配设备上寻找最早可用时间。跳过缺料订单，保留锁定任务并检查有限产能。</p><h3 style="margin-top:25px">方案边界</h3><p>示例模型不计算多工序前后置、人员、工装和换型时间。规则用于讨论排程工作流，正式算法需结合真实工艺数据验证。</p></aside></div>`;
  }
  function taskEditor(id){
    const t=state.tasks.find(t=>t.id===id);if(!t)return;
    const o=getOrder(t.order),running=['running','paused','done'].includes(executionStatus(t));
    U.drawer(`${o.product} · 工序任务`, `<div class="plan-info">${badge(t.id,'info')}${badge(o.priority,o.priority==='紧急'?'danger':'neutral')}${t.locked?badge('已锁定','neutral'):''}</div><dl class="detail-grid"><div><dt>生产订单</dt><dd>${o.id}</dd></div><div><dt>客户</dt><dd>${esc(o.customer)}</dd></div><div><dt>工艺要求</dt><dd>${o.type} · ${o.family}</dd></div><div><dt>计划数量</dt><dd>${o.qty} 件</dd></div><div><dt>标准工时</dt><dd>${o.hours} 小时</dd></div><div><dt>承诺交期</dt><dd>${timeLabel(o.due,true)}</dd></div></dl>${running?'<div class="inline-warning">该任务已进入现场执行，当前排期已冻结。</div>':''}<div class="form-stack"><label class="field"><span class="field-label">生产设备</span><select id="task-resource" ${running?'disabled':''}>${state.resources.map(r=>`<option value="${r.id}" ${r.id===t.resource?'selected':''}>${r.id} · ${r.name} · ${r.type}${r.active?'':'（停用）'}</option>`).join('')}</select></label><label class="field"><span class="field-label">计划开始时间</span><input type="datetime-local" id="task-start" step="3600" min="2026-09-10T08:00" max="2026-09-12T19:00" value="${inputTime(t.start)}" ${running?'disabled':''}></label><div class="help-note">当前计划完成：${timeLabel(t.start+t.hours,true)}<br>修改设备或开始时间后，系统将重新检查占用冲突。</div><label class="field-check"><input type="checkbox" id="task-lock" ${t.locked?'checked':''} ${running?'disabled':''}>锁定设备与排期，后续排程保留此任务</label><div class="field-error" id="task-error"></div></div>`,btn('关闭','close-drawer','x')+(running?'':btn('保存调整','save-task','check','primary',`data-id="${t.id}"`)));
    U.icons();
  }
  function viewOrder(id){
    const o=getOrder(id);if(!o)return;const t=state.tasks.find(t=>t.order===id);
    U.drawer('生产订单详情',`<div class="plan-info">${badge(o.id,'info')}${badge(t?'已排程':'待排程',t?'success':'neutral')}</div><h2 style="font-size:20px;margin:20px 0">${esc(o.product)}</h2><dl class="detail-grid"><div><dt>客户</dt><dd>${esc(o.customer)}</dd></div><div><dt>数量</dt><dd>${o.qty} 件</dd></div><div><dt>关键工序</dt><dd>${o.type}</dd></div><div><dt>标准工时</dt><dd>${o.hours} 小时</dd></div><div><dt>承诺交期</dt><dd>${timeLabel(o.due,true)}</dd></div><div><dt>优先级</dt><dd>${o.priority}</dd></div><div><dt>原料类别</dt><dd>${o.family}</dd></div><div><dt>物料状态</dt><dd>${badge(o.material?'已齐套':'原料未到齐',o.material?'success':'warning')}</dd></div></dl>${!o.material?'<div class="inline-warning">铝板原料短缺。标记示例到料后，该订单可进入排程范围。</div>':''}${t?`<section class="section"><h3 style="font-size:13px">当前草稿排期</h3><p class="detail-events">${t.resource}<br>${timeLabel(t.start)} — ${timeLabel(t.start+t.hours,true)}<br>${t.locked?'设备与时间已锁定':'允许调整'}</p></section>`:''}`,btn('关闭','close-drawer','x')+(!o.material?btn('标记示例到料','material-arrived','package-check','primary',`data-id="${o.id}"`):t?btn('调整任务','edit-task','pencil','primary',`data-id="${t.id}"`):btn('选中并排程','schedule-order','chart-gantt','primary',`data-id="${o.id}"`)));
    U.icons();
  }
  function generateDialog(){
    const selected=state.orders.filter(o=>o.selected),eligible=selected.filter(o=>o.material);
    U.modal('生成演示排程',`<div class="form-stack"><div class="selection-summary">已选 ${selected.length} 个订单 · 可排 ${eligible.length} 个 · 缺料跳过 ${selected.length-eligible.length} 个</div><label class="field"><span class="field-label">本轮排序目标</span><select id="generate-rule">${[['due','交期优先'],['priority','订单优先级'],['short','短工时优先']].map(([v,l])=>`<option value="${v}" ${state.rule===v?'selected':''}>${l}</option>`).join('')}</select></label><label class="field-check"><input type="checkbox" id="generate-lock" ${state.keepLocked?'checked':''}>保留人工锁定任务</label><div class="help-note">将为已选订单寻找匹配设备的最早可用区间。未选订单的现有任务保留；现场已开工任务始终保留。生成结果保存为草稿。</div><div class="field-error" id="generate-error"></div></div>`,btn('取消','close-modal','x')+btn('生成草稿','run-generate','sparkles','primary'));
    U.icons();
  }
  function generate(){
    const selected=state.orders.filter(o=>o.selected&&o.material);
    if(!selected.length){document.getElementById('generate-error').textContent='请先在生产订单中选择至少一个物料齐套的订单。';return;}
    state.rule=document.getElementById('generate-rule').value;state.keepLocked=document.getElementById('generate-lock').checked;
    const ids=new Set(selected.map(o=>o.id));
    const retained=state.tasks.filter(t=>!ids.has(t.order)||(state.keepLocked&&t.locked)||['running','paused','done'].includes(executionStatus(t)));
    const assigned=clone(retained), priority={'紧急':0,'高':1,'普通':2};
    selected.sort((a,b)=>state.rule==='priority'?priority[a.priority]-priority[b.priority]||a.due-b.due:state.rule==='short'?a.hours-b.hours||a.due-b.due:a.due-b.due||priority[a.priority]-priority[b.priority]);
    let skipped=0;
    for(const o of selected){
      if(assigned.some(t=>t.order===o.id))continue;
      let best=null;
      for(const r of state.resources.filter(r=>r.active&&r.type===o.type)){
        for(let start=0;start+o.hours<=36;start++){
          if(start%12+o.hours>12)continue;
          if(assigned.some(t=>t.resource===r.id&&start<t.start+t.hours&&t.start<start+o.hours))continue;
          if(!best||start<best.start)best={resource:r.id,start};
          break;
        }
      }
      if(!best){skipped++;continue;}
      const old=state.tasks.find(t=>t.order===o.id);
      assigned.push({id:old?.id||`OP-${String(o.id.split('-').pop()).padStart(3,'0')}`,order:o.id,resource:best.resource,start:best.start,hours:o.hours,locked:false});
    }
    state.tasks=assigned;state.draftName=({'due':'交期优先','priority':'优先级优先','short':'短工时优先'})[state.rule]+' · 演示排程';
    record(`生成演示草稿：${assigned.length} 项任务，${skipped} 个订单受产能限制未排入。`);dirty();U.closeModal();navigate('schedule');U.toast(`已生成 ${assigned.length} 项排程任务${skipped?'，'+skipped+' 个订单未排入':''}`);
  }
  function newOrder(){
    U.modal('新建生产订单',`<div class="form-grid"><label class="field span-full"><span class="field-label">产品名称</span><input id="new-product" maxlength="40" placeholder="例如：精密连接板"></label><label class="field"><span class="field-label">客户</span><input id="new-customer" maxlength="30" value="科锐自动化"></label><label class="field"><span class="field-label">生产数量</span><input id="new-qty" type="number" min="1" max="100000" value="100"></label><label class="field"><span class="field-label">关键工序</span><select id="new-type"><option>铣削</option><option>车削</option><option>磨削</option></select></label><label class="field"><span class="field-label">标准工时（小时）</span><input id="new-hours" type="number" min="1" max="12" value="3"></label><label class="field"><span class="field-label">承诺交期</span><select id="new-due"><option value="12">09/10 20:00</option><option value="24">09/11 20:00</option><option value="36" selected>09/12 20:00</option></select></label><label class="field"><span class="field-label">优先级</span><select id="new-priority"><option>普通</option><option>高</option><option>紧急</option></select></label><label class="field-check span-full"><input id="new-material" type="checkbox" checked>原料已齐套</label><div id="new-error" class="field-error span-full"></div></div>`,btn('取消','close-modal','x')+btn('创建订单','create-order','plus','primary'));U.icons();
  }
  function saveVersionDialog(){
    U.modal('保存模拟版本',`<div class="form-stack"><label class="field"><span class="field-label">方案名称</span><input id="version-name" maxlength="40" value="${esc(state.draftName)}"></label><div class="help-note">保存当前 ${state.tasks.length} 项任务的独立快照，可在版本页与当前草稿比较。现场生效版本保持为 ${state.publishedId}。</div><div id="version-error" class="field-error"></div></div>`,btn('取消','close-modal','x')+btn('保存版本','confirm-version','save','primary'));U.icons();
  }
  function publishDialog(){
    const c=counts(),changedRunning=published().tasks.some(t=>['running','paused','done'].includes(executionStatus(t))&&!state.tasks.some(d=>d.id===t.id&&d.resource===t.resource&&d.start===t.start&&d.hours===t.hours));
    if(c.errors||changedRunning){U.modal('当前方案暂不可发布',`<div class="inline-warning">${changedRunning?'当前草稿改变了已进入现场执行的任务。请保留其原设备和排期后重试。':`检测到 ${c.errors} 项阻断冲突，请先修复设备占用、能力或日历问题。`}</div>`,btn('关闭','close-modal','x')+btn('查看数据校验','publish-validation','shield-check','primary'));U.icons();return;}
    if(!state.tasks.length){U.toast('请先生成至少一个任务的排程方案。');return;}
    U.modal('确认发布计划',`<div class="plan-info">${badge('待发布','warning')}<strong>${esc(state.draftName)}</strong></div><dl class="detail-grid"><div><dt>计划任务</dt><dd>${c.scheduled} 项</dd></div><div><dt>占用工时</dt><dd>${c.hours} 小时</dd></div><div><dt>未排订单</dt><dd>${c.pending} 项</dd></div><div><dt>预计延期</dt><dd>${c.late} 项</dd></div></dl>${c.pending||c.late?`<div class="inline-warning">本次仅发布上述已排任务。${c.pending} 个未排订单继续保留在订单池，${c.late} 个任务预计延期。</div>`:''}<label class="field-check"><input type="checkbox" id="publish-confirm">我已核对设备占用、未排订单与交期影响</label><div id="publish-error" class="field-error"></div><div class="help-note">发布后将生成新的正式快照，现场执行使用该快照。既有开工与完工记录保留。</div>`,btn('取消','close-modal','x')+btn('发布至现场','confirm-publish','send','primary'));U.icons();
  }
  function csv(rows){return '\ufeff'+rows.map(row=>row.map(v=>`"${String(v??'').replace(/"/g,'""')}"`).join(',')).join('\r\n');}
  function exportPlan(){const rows=[['任务','订单','产品','数量','设备','开始时间','完成时间','工时','锁定'],...state.tasks.map(t=>{const o=getOrder(t.order);return[t.id,o.id,o.product,o.qty,t.resource,timeLabel(t.start),timeLabel(t.start+t.hours,true),t.hours,t.locked?'是':'否'];})];U.download('生产排程-当前草稿.csv',csv(rows),'text/csv;charset=utf-8');}
  function navigate(id){page=id;if(location.hash!=='#'+id)location.hash=id;render();document.getElementById('sidebar').classList.remove('open');}
  function applyExecution(id,status,reason=''){
    const t=published().tasks.find(t=>t.id===id);if(!t)return;
    if(status==='running'){
      if(!getResource(t.resource)?.active){U.toast(`${t.resource} 已停用，请先恢复设备。`);return;}
      if(!getOrder(t.order)?.material){U.toast('订单原料未齐套，当前不能开工。');return;}
      const other=published().tasks.find(v=>v.resource===t.resource&&v.id!==t.id&&executionStatus(v)==='running');
      if(other){U.toast(`${t.resource} 正在执行 ${getOrder(other.order).product}，请先暂停或完工。`);return;}
    }
    state.execution[id]={status,time:now(),reason};
    const draft=state.tasks.find(v=>v.id===id);if(draft)draft.locked=true;
    record(`${getOrder(t.order).product}：${({running:'开工 / 恢复',paused:'暂停',done:'完工'})[status]}${reason?'（'+reason+'）':''}。`);persist();U.closeModal();render();
  }
  document.addEventListener('click',event=>{
    const target=event.target.closest('[data-action]');if(!target)return;
    const a=target.dataset.action,id=target.dataset.id;
    if(a.startsWith('go-')){navigate(a.slice(3));return;}
    if(a==='close-modal')U.closeModal();
    else if(a==='close-drawer')U.closeDrawer();
    else if(a==='export')exportPlan();
    else if(a==='export-orders')U.download('生产订单.csv',csv([['订单','产品','客户','数量','工艺','工时','交期','优先级','物料'],...state.orders.filter(orderMatches).map(o=>[o.id,o.product,o.customer,o.qty,o.type,o.hours,timeLabel(o.due,true),o.priority,o.material?'齐套':'缺料'])]),'text/csv;charset=utf-8');
    else if(a==='generate')generateDialog();
    else if(a==='run-generate')generate();
    else if(a==='edit-task'){U.closeDrawer();taskEditor(id);}
    else if(a==='view-order')viewOrder(id);
    else if(a==='save-task'){
      const t=state.tasks.find(t=>t.id===id),start=toSlot(document.getElementById('task-start').value);
      if(!Number.isFinite(start)||start<0||start>=36){document.getElementById('task-error').textContent='请选择 09/10–09/12 的 08:00–19:00 整点时间。';return;}
      t.resource=document.getElementById('task-resource').value;t.start=start;t.locked=document.getElementById('task-lock').checked;
      record(`人工调整 ${getOrder(t.order).product} 至 ${t.resource}，${timeLabel(t.start)} 开始。`);dirty();U.closeDrawer();render();U.toast(counts().errors?'调整已保存，仍有阻断冲突待处理。':'调整已保存，校验无阻断冲突。');
    }
    else if(a==='select-order'){getOrder(id).selected=target.checked;persist();render();}
    else if(a==='select-all'){state.orders.filter(orderMatches).forEach(o=>o.selected=target.checked);persist();render();}
    else if(a==='material-arrived'){getOrder(id).material=true;getOrder(id).selected=true;record(`${getOrder(id).product} 标记示例到料，已加入待排范围。`);dirty();U.closeDrawer();render();U.toast('已标记示例到料，可参与下一轮排程。');}
    else if(a==='schedule-order'){getOrder(id).selected=true;persist();U.closeDrawer();generateDialog();}
    else if(a==='new-order')newOrder();
    else if(a==='create-order'){
      const product=document.getElementById('new-product').value.trim(),customer=document.getElementById('new-customer').value.trim(),qty=Number(document.getElementById('new-qty').value),hours=Number(document.getElementById('new-hours').value);
      if(!product||!customer||!Number.isInteger(qty)||qty<1||qty>100000||!Number.isInteger(hours)||hours<1||hours>12){document.getElementById('new-error').textContent='请填写产品与客户；数量为 1–100000 的整数，工时为 1–12 的整数。';return;}
      state.serial++;const oid='MO-260910-'+String(state.serial).padStart(3,'0');
      state.orders.push({id:oid,product,customer,qty,hours,type:document.getElementById('new-type').value,due:Number(document.getElementById('new-due').value),priority:document.getElementById('new-priority').value,material:document.getElementById('new-material').checked,family:'通用件',selected:true});record(`创建生产订单 ${oid}：${product} ${qty} 件。`);dirty();query='';orderStatus='all';U.closeModal();render();U.toast('生产订单已创建，已加入本轮选中范围。');
    }
    else if(a==='toggle-resource'){
      const r=getResource(id);
      if(r.active&&published().tasks.some(t=>t.resource===id&&['running','paused'].includes(executionStatus(t)))){U.toast('该设备有执行中的任务，请先完成现场生产。');return;}
      r.active=!r.active;record(`${r.id} ${r.active?'恢复可用':'设为停用'}，已有排程已重新校验。`);dirty();render();U.toast(r.active?'设备已恢复可用。':'设备已停用，已有任务需要重新校验。');
    }
    else if(a==='recheck'){render();U.toast(`校验完成：${counts().errors} 项阻断问题。`);}
    else if(a==='save-version')saveVersionDialog();
    else if(a==='confirm-version'){
      const name=document.getElementById('version-name').value.trim();if(!name){document.getElementById('version-error').textContent='请输入方案名称。';return;}
      const vid='V'+String(state.versions.length+1).padStart(3,'0');state.versions.push({id:vid,name,time:stamp(),status:'simulation',tasks:clone(state.tasks)});record(`保存模拟版本 ${vid}：${name}。`);persist();compareId=vid;U.closeModal();render();
    }
    else if(a==='compare-version'){compareId=id;render();}
    else if(a==='publish')publishDialog();
    else if(a==='publish-validation'){U.closeModal();navigate('validation');}
    else if(a==='confirm-publish'){
      if(!document.getElementById('publish-confirm').checked){document.getElementById('publish-error').textContent='请先确认已核对计划影响。';return;}
      if(counts().errors){U.closeModal();publishDialog();return;}
      const vid='V'+String(state.versions.length+1).padStart(3,'0');state.versions.push({id:vid,name:state.draftName,time:stamp(),status:'published',tasks:clone(state.tasks)});state.publishedId=vid;compareId=vid;record(`发布计划 ${vid} 至现场，共 ${state.tasks.length} 项任务。`);persist();U.closeModal();render();U.toast(`计划 ${vid} 已发布，现场执行已更新。`);
    }
    else if(a==='execution-filter'){executionFilter=id;render();}
    else if(a==='start-task'||a==='resume-task')applyExecution(id,'running');
    else if(a==='pause-task'){
      U.modal('暂停生产任务',`<label class="field"><span class="field-label">暂停原因</span><select id="pause-reason"><option>设备调整</option><option>等待物料</option><option>质量复检</option><option>交接班</option></select></label>`,btn('取消','close-modal','x')+btn('记录暂停','confirm-pause','pause','primary',`data-id="${id}"`));U.icons();
    }
    else if(a==='confirm-pause')applyExecution(id,'paused',document.getElementById('pause-reason').value);
    else if(a==='complete-task'){
      const t=published().tasks.find(v=>v.id===id),o=getOrder(t.order);
      U.modal('确认生产完工',`<p style="font-size:13px;line-height:1.8">${esc(o.product)} · ${o.id}<br>本次按计划数量 <strong>${o.qty} 件</strong>记录整批完工。</p><div class="help-note">完工记录保留在当前工作区，任务状态将变为已完工。</div>`,btn('取消','close-modal','x')+btn('确认完工','confirm-complete','check','primary',`data-id="${id}"`));U.icons();
    }
    else if(a==='confirm-complete')applyExecution(id,'done');
    else if(a==='save-rules'){state.rule=document.querySelector('input[name="rule"]:checked').value;state.keepLocked=document.getElementById('keep-locks').checked;record('更新排程排序目标与锁定保留规则。');persist();U.toast('规则已保存，下次生成排程时生效。');}
  });
  document.addEventListener('change',event=>{
    const filter=event.target.dataset.filter;if(!filter)return;
    if(filter==='resource')resourceFilter=event.target.value;
    if(filter==='orderStatus')orderStatus=event.target.value;
    if(filter==='query')query=event.target.value;
    render();
  });
  document.addEventListener('input',event=>{
    if(event.target.dataset.filter!=='query')return;
    const cursor=event.target.selectionStart;query=event.target.value;render();const field=document.getElementById('order-search');field.focus();field.setSelectionRange(cursor,cursor);
  });
  window.addEventListener('hashchange',()=>{page=location.hash.slice(1);render();document.getElementById('sidebar').classList.remove('open');});
  persist();render();
})();
