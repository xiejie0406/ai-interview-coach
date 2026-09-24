(function () {
  'use strict';
  const E = UI.escape;
  const I = UI.icon;
  const showToast=UI.toast;
  UI.toast=message=>{const items=[...document.querySelectorAll('.ui-toast')];const limit=innerWidth<520?2:3;while(items.length>=limit)items.shift().remove();showToast(message);};
  const BASE = new Date(2026, 8, 14, 8, 0).getTime();
  const STORE = 'production-workbench-v2.0';
  const nav = [
    ['计划与调度', [['schedule','排程工作台','chart-no-axes-gantt'],['orders','生产订单','clipboard-list'],['conflicts','约束与异常','triangle-alert'],['batches','加工批次','layers']]],
    ['工艺与资源', [['routes','工艺路线','git-branch'],['resources','工作中心与设备','factory'],['people','人员与班次','users-round']]],
    ['执行与分析', [['execution','现场执行','circle-play'],['daily','车间每日生产表','calendar-check'],['capacity','人力累计产能','chart-no-axes-combined'],['versions','计划版本','history'],['audit','操作记录','list-checks']]]
  ];
  const names = Object.fromEntries(nav.flatMap(g=>g[1].map(n=>[n[0],n[1]])));
  const fmt = value => {
    if (value == null || !Number.isFinite(Number(value))) return '未排定';
    const d = new Date(BASE + Number(value)*60000);
    return `${d.getMonth()+1}/${d.getDate()} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`;
  };
  const toInput = value => {
    const d = new Date(BASE + Number(value)*60000);
    return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}T${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`;
  };
  let state;
  try {
    if (!window.Engine) throw new Error('排程组件未载入，请确认原型文件夹完整。');
    state = UI.load(STORE, null);
    if (!state || state.schema !== 2 || !Array.isArray(state.tasks) || !state.draft || !state.published) state = Engine.createState();
  } catch (error) {
    document.getElementById('main').innerHTML = `<div class="app-error"><h1>无法载入生产计划</h1><p>${E(error.message)}</p><button class="btn" onclick="location.reload()">重新载入</button></div>`;
    return;
  }
  let storageToken=state._writeToken||null;
  function storageCurrent(){
    const persisted=UI.load(STORE,null);
    if(persisted && (persisted._writeToken||null)!==storageToken){
      UI.modal('本地计划已在其他页面更新','<p>当前页面的修改未覆盖其他页面的新版本。可以导出当前编辑副本，然后载入最新计划。</p>',`<button class="btn" data-action="export-conflict">${I('download')}导出当前副本</button><button class="btn primary" data-action="reload-storage">${I('refresh-cw')}载入最新计划</button>`);
      return false;
    }
    return true;
  }
  const A = window.A = {
    state,view:'schedule',filters:{ganttMode:'machine',scope:'draft',day:0,days:2,search:''},undoStack:[],
    e:E,icon:I,fmt,time:v=>fmt(v).split(' ')[1]||'--:--',hours:v=>(Number(v||0)/60).toLocaleString('zh-CN',{maximumFractionDigits:2}),
    btn:(action,id,label,icon,cls='')=>`<button type="button" class="btn ${E(cls)}" data-action="${E(action)}" data-id="${E(id||'')}">${icon?I(icon):''}${E(label)}</button>`,
    badge:(label,tone='neutral')=>`<span class="badge ${E(tone)}">${E(label)}</span>`,
    table:(headers,rows)=>`<div class="table-wrap"><table class="data-table"><thead><tr>${headers.map(h=>`<th scope="col">${E(h)}</th>`).join('')}</tr></thead><tbody>${rows.length?rows.map(row=>`<tr>${row.map(c=>`<td>${c==null?'':c}</td>`).join('')}</tr>`).join(''):`<tr><td colspan="${headers.length}"><div class="empty-state">没有符合条件的记录</div></td></tr>`}</tbody></table></div>`,
    empty:(title,text='')=>`<div class="empty-state">${I('inbox')}<h3>${E(title)}</h3>${text?`<p>${E(text)}</p>`:''}</div>`,
    csv:(filename,headers,rows)=>{
      const cell=v=>{let str=String(v==null?'':v);if(/^[=+@\-\t\r]/.test(str))str="'"+str;return '"'+str.replace(/"/g,'""')+'"';};
      UI.download(filename,'\ufeff'+[headers,...rows].map(r=>r.map(cell).join(',')).join('\r\n'),'text/csv;charset=utf-8');
    },
    audit:(type,message,taskId)=>{A.state.events.push({id:'EV-'+Date.now()+'-'+Math.random().toString(36).slice(2,6),at:new Date().toISOString(),type,message,taskId});},
    commit:(message)=>{if(!storageCurrent())return false;A.state._writeToken=Date.now()+'-'+Math.random().toString(36).slice(2);storageToken=A.state._writeToken;const saved=UI.save(STORE,A.state);A.render();if(message)UI.toast(message);if(!saved)UI.toast('浏览器未允许本地保存，本次修改仅保留在当前页面。');return true;},
    go:view=>{if(location.hash.slice(1)===view){A.view=view;A.render();}else location.hash=view;},
    render:render,task:openTask,
    assignments:()=>A.filters.scope==='published'?A.state.published.assignments:A.state.draft.assignments,
    heading:(title,subtitle='',actions='')=>`<div class="page-heading"><div><h1 class="page-title">${E(title)}</h1>${subtitle?`<p class="page-description">${E(subtitle)}</p>`:''}</div><div class="actions">${actions}</div></div>`
  };
  const resource = id => A.state.resources.find(r=>r.id===id);
  const task = id => A.state.tasks.find(t=>t.id===id);
  const rname = id => resource(id)?.name || id || '无';
  const clone = value => JSON.parse(JSON.stringify(value));
  function issues(assignments=A.assignments()) {return Engine.validate(A.state,assignments)||[];}
  function takeUndo() {A.undoStack.push({assignments:clone(A.state.draft.assignments),revision:A.state.revision,locks:A.state.tasks.map(t=>({id:t.id,lock:t.lock,lockAssignment:t.lockAssignment?clone(t.lockAssignment):null}))});if(A.undoStack.length>10)A.undoStack.shift();}
  function setDraft(assignments){A.state.draft={assignments,basedOn:A.state.published.id,revision:A.state.revision};}
  function pageTitle(title,sub,actions=''){return A.heading(title,sub,actions);}
  function stat(icon,label,value,unit,foot,tone=''){return `<div class="stat"><div class="stat-label">${I(icon)}${E(label)}</div><div class="stat-value ${tone}">${E(value)}<small>${E(unit)}</small></div><div class="stat-foot">${E(foot)}</div></div>`;}

  Views.schedule = function () {
    const assignments=A.assignments(), result=Engine.metrics(A.state,assignments), errors=issues(assignments).filter(x=>x.severity!=='warning');
    const late=(result.orders||[]).filter(o=>o.late>0).length;
    const stale=A.state.draft.revision!==A.state.revision||A.state.draft.basedOn!==A.state.published.id;
    const viewMode=A.filters.ganttMode;
    return pageTitle('排程工作台','2026 年 9 月 · 订单、工艺任务与人机资源',
      A.btn('run-plan','','生成排程','wand-sparkles','primary')+A.btn('review-publish','','审查并发布','send'))+
      `<div class="metric-strip">${stat('clipboard-list','生产订单',A.state.orders.length,'单',`${A.state.tasks.length} 道工序任务`)}${stat('calendar-clock','预计延期',late,'单',late?'查看瓶颈与可调整窗口':'当前已排订单均可按期',late?'warning-text':'')}${stat('users-round','已排人工时',A.hours(result.personHours*60),'人时','按人员实际参与阶段统计')}${stat('circle-check','约束问题',errors.length,'项',`${result.unplanned||0} 道任务尚未排定`,errors.length?'error-text':'success-text')}</div>`+
      (stale&&A.filters.scope==='draft'?`<div class="notice warning">${I('refresh-cw')}<span>资源或订单已更新，当前候选需要重新排程或校验。</span>${A.btn('run-plan','','更新候选','refresh-cw','small')}</div>`:'')+
      `<div class="plan-controls"><div class="actions"><div class="segmented" role="group" aria-label="甘特图视角">${[['machine','设备','cpu'],['person','人员','users-round'],['workshop','车间工序','factory']].map(([id,label,icon])=>`<button class="tab ${viewMode===id?'active':''}" aria-pressed="${viewMode===id}" data-action="gantt-mode" data-id="${id}">${I(icon)} ${label}</button>`).join('')}</div></div><div class="actions"><label class="sr-only" for="plan-scope">计划版本</label><select class="control" id="plan-scope"><option value="draft" ${A.filters.scope==='draft'?'selected':''}>当前候选</option><option value="published" ${A.filters.scope==='published'?'selected':''}>正式计划 ${E(A.state.published.id)}</option></select>${A.btn('undo-plan','','撤销调整','undo-2','small')}</div></div>`+
      `<div class="gantt-frame"><div class="gantt-toolbar"><div class="actions"><button class="icon-btn" data-action="gantt-prev" aria-label="前一天" title="前一天">${I('chevron-left')}</button><button class="icon-btn" data-action="gantt-next" aria-label="后一天" title="后一天">${I('chevron-right')}</button><strong style="font-size:12px">${E(fmt(A.filters.day*1440).split(' ')[0])} — ${E(fmt((A.filters.day+A.filters.days-1)*1440).split(' ')[0])}</strong><button class="btn small" data-action="gantt-today">起始日</button><label class="sr-only" for="gantt-days">显示天数</label><select class="control" id="gantt-days"><option value="1" ${A.filters.days===1?'selected':''}>1 天</option><option value="2" ${A.filters.days===2?'selected':''}>2 天</option><option value="5" ${A.filters.days===5?'selected':''}>5 天</option></select></div><label class="search-field">${I('search')}<input id="gantt-search" type="search" placeholder="订单 / 产品 / 工序" aria-label="筛选甘特任务" value="${E(A.filters.search)}"></label></div>${gantt(assignments)}<div class="gantt-footer"><div class="legend"><span><b></b>加工</span><span><b class="amber"></b>共享批次</span><span><b class="blue"></b>检验 / 人工</span><span><b class="gray"></b>非工作时段</span></div><span>${A.filters.scope==='published'?'正式计划 '+E(A.state.published.id):'候选基于 '+E(A.state.draft.basedOn)} · ${A.state.tasks.filter(t=>t.lock!=='none').length} 道已锁定</span></div></div>`+
      `<div class="plan-bottom"><section><div class="section-heading"><h2>订单交期</h2><a class="text-link" href="#orders">全部订单 ${I('arrow-up-right')}</a></div><div class="order-progress-list">${(result.orders||[]).map(o=>{const order=A.state.orders.find(x=>x.id===o.id);const ratio=o.qty?Math.min(100,Math.round(o.good/o.qty*100)):0;return `<div class="order-progress"><div><button class="text-link" data-action="cat-order-detail" data-id="${E(o.id)}">${E(o.id)}</button><small>${E(order?.customer||'')} · ${order?.lines?.length||0} 个产品</small></div><div>${o.end==null?'未排定':E(fmt(o.end))}<small>承诺 ${E(fmt(order?.due))}</small></div>${o.end==null?A.badge('待排定','warning'):o.late>0?A.badge('延期 '+A.hours(o.late)+'h','warning'):A.badge(ratio===100?'已完成':'可按期','success')}</div>`;}).join('')}</div></section><section><div class="section-heading"><h2>约束与调度关注</h2><a class="text-link" href="#conflicts">查看全部</a></div><div class="issue-list">${errors.length?errors.slice(0,3).map(issue=>`<div class="issue-item">${I('triangle-alert')}<div>${E(issue.message)}${issue.taskId?`<small>${E(issue.taskId)}</small><button class="text-link" data-action="task-detail" data-id="${E(issue.taskId)}">定位工序</button>`:''}</div></div>`).join(''):`<div class="issue-item">${I('shield-check')}<div>当前计划通过资源与依赖校验<small>人员、设备和批次按实际占用检查</small></div></div><div class="issue-item">${I('layers')}<div>${A.state.batches.length} 个共享加工批次<small>可查看成员、容量与共同加工时段</small><a class="text-link" href="#batches">查看加工批次</a></div></div>`}</div></section></div>`;
  };
  function gantt(assignments) {
    const mode=A.filters.ganttMode, days=A.filters.days, startDay=A.filters.day, px=.8, dayWidth=600*px, width=days*dayWidth;
    const search=A.filters.search.toLowerCase();
    let rows=[];
    if(mode==='machine'||mode==='person')rows=A.state.resources.filter(r=>r.type===mode).map(r=>({id:r.id,name:r.name,sub:r.wc||r.workshop,resource:r}));
    else {
      const keys=[...new Set(A.state.tasks.map(t=>`${t.workshop} · ${t.wc} · ${t.op}`))];
      rows=keys.map(k=>({id:k,name:k.split(' · ').slice(2).join(' · '),sub:k.split(' · ').slice(0,2).join(' / ')}));
    }
    const filtered=assignments.filter(a=>{const t=task(a.taskId);return t&&(!search||[t.id,t.orderId,t.product,t.op].join(' ').toLowerCase().includes(search));});
    const background=Array.from({length:days},(_,d)=>`<div class="off-hour" style="left:${d*dayWidth+240*px}px;width:${60*px}px"></div><div class="off-hour" style="left:${d*dayWidth+540*px}px;width:${60*px}px"></div>${d?`<div class="day-divider" style="left:${d*dayWidth}px"></div>`:''}`).join('');
    function coords(segment){const cuts=[];for(let d=startDay;d<startDay+days;d++){const l=Math.max(segment.start,d*1440),r=Math.min(segment.end,d*1440+600);if(r>l)cuts.push({left:(d-startDay)*dayWidth+(l-d*1440)*px,width:(r-l)*px});}return cuts;}
    let any=false;
    const rowHTML=rows.map(row=>{
      const seen=new Set(), bars=[];
      filtered.forEach(a=>{
        const t=task(a.taskId);let segments=a.segments||[];
        if(mode==='workshop') {
          if(`${t.workshop} · ${t.wc} · ${t.op}`!==row.id)return;
          segments=[{start:a.start,end:a.end,kind:'task'}];
        }else segments=segments.filter(s=>(mode==='machine'?s.machineId:s.personId)===row.id);
        segments.forEach(s=>{
          const key=mode==='workshop'?`${t.id}-${s.start}`:`${a.batchId||t.id}-${row.id}-${s.start}-${s.end}-${s.kind}`;
          if(seen.has(key))return;seen.add(key);
          coords(s).forEach(c=>{bars.push({a,t,s,...c});});
        });
      });
      bars.sort((a,b)=>a.left-b.left||b.width-a.width);const lanes=[];
      bars.forEach(bar=>{let lane=lanes.findIndex(end=>end<=bar.left+.1);if(lane<0){lane=lanes.length;lanes.push(0);}lanes[lane]=bar.left+bar.width;bar.lane=lane;});
      if(bars.length)any=true;
      const height=Math.max(67,lanes.length*44+20);
      return `<div class="gantt-row" style="min-height:${height}px"><div class="gantt-label"><span class="resource-icon">${I(mode==='machine'?'cpu':mode==='person'?'user-round':'git-branch')}</span><div><strong>${E(row.name)}</strong><small>${E(row.sub||'')}</small></div></div><div class="gantt-track" data-resource-id="${E(row.id)}" data-resource-type="${mode}" style="width:${width}px;min-height:${height}px">${background}${bars.map(({a,t,s,left,width:barWidth,lane})=>{const batch=a.batchId;const color=batch?'amber':t.mode==='manual'||/检/.test(t.op)?'blue':'';const title=`${batch?batch+' · ':''}${t.orderId} / ${t.product} / ${t.op}\n${fmt(s.start)} — ${fmt(s.end)}\n${s.personId?rname(s.personId):'无人工占用'} · ${s.machineId?rname(s.machineId):'纯人工'}`;return `<button class="task-bar ${color} ${t.lock!=='none'?'locked':''}" style="left:${left}px;width:${Math.max(6,barWidth-2)}px;top:${12+lane*44}px" title="${E(title)}" aria-label="${E(title)}" draggable="${A.filters.scope==='draft'}" data-action="task-detail" data-id="${E(t.id)}" data-start="${a.start}" data-segment-start="${s.start}"><strong>${E(batch||t.product)} · ${E(t.op)}</strong><small>${E(t.orderId)} ${E(s.kind==='setup'?'准备':s.kind==='unload'?'卸料':((s.kind==='task'?t.qty:batch?A.state.tasks.filter(member=>member.batchId===batch).reduce((sum,member)=>sum+member.qty,0):Number.isFinite(s.qty)?s.qty:t.qty)+'件'))}</small></button>`;}).join('')}</div></div>`;
    }).join('');
    return `<div class="gantt-scroll"><div class="gantt-grid"><div class="gantt-head"><div class="gantt-label">${mode==='machine'?'工作中心 / 设备':mode==='person'?'班组 / 人员':'车间 / 工序'}</div><div class="gantt-scale" style="width:${width}px">${Array.from({length:days},(_,d)=>`<div class="gantt-day" style="width:${dayWidth}px"><div class="gantt-date">${E(fmt((startDay+d)*1440).split(' ')[0])} ${['周一','周二','周三','周四','周五'][startDay+d]||''}</div><div class="gantt-hours">${Array.from({length:10},(_,h)=>`<span>${String(h+8).padStart(2,'0')}:00</span>`).join('')}</div></div>`).join('')}</div></div>${rowHTML}</div></div>${!any?`<div style="padding:12px;text-align:center;color:var(--muted);font-size:12px">当前时间与筛选范围没有已排任务</div>`:''}`;
  }
  Views.conflicts = function(){
    const list=issues(A.state.draft.assignments);return pageTitle('约束与异常','当前候选 · 数据、工艺依赖与资源占用',A.btn('run-plan','','重新排程','refresh-cw','primary'))+
      `<div class="metric-strip">${stat('shield-check','已校验工序',A.state.tasks.length,'道','同一计划全量校验')}${stat('circle-alert','硬约束问题',list.filter(i=>i.severity!=='warning').length,'项','阻止发布')}${stat('triangle-alert','提示',list.filter(i=>i.severity==='warning').length,'项','需查看影响')}${stat('database','数据修订',A.state.revision,'版','资源及订单变更追溯')}</div>`+
      (list.length?A.table(['严重程度','问题类型','任务 / 资源','原因','处理'],list.map(i=>[A.badge(i.severity==='warning'?'提示':'阻断',i.severity==='warning'?'warning':'danger'),E(i.code),`<span class="mono">${E(i.taskId||i.resourceId||'计划')}</span>`,`<div class="issue-message">${E(i.message)}</div>`,i.taskId?A.btn('task-detail',i.taskId,'查看工序','arrow-up-right','small'):A.btn('go-resources','','检查资源','arrow-up-right','small')])):A.empty('当前候选没有硬约束冲突','发布时仍会复核最新订单、日历和执行状态。'));
  };

  function openTask(id) {
    const t=task(id);if(!t){UI.toast('该工序已不存在，请刷新计划。');return;}
    const as=A.state.draft.assignments.find(a=>a.taskId===id),pub=A.state.published.assignments.find(a=>a.taskId===id);
    const personOptions=A.state.resources.filter(r=>r.type==='person'&&(!t.skill||(r.skills?.[t.skill]||0)>=(t.minLevel||1)));
    const machines=A.state.resources.filter(r=>r.type==='machine'&&t.machines.includes(r.id));
    const execution=['running','paused','done'].includes(t.status);
    const deps=(t.deps||[]).map(d=>{const p=task(d.taskId);return `<div class="info-row"><span>${E(p?.product||'')} · ${E(p?.op||d.taskId)}</span><strong>${d.type==='quantity'?(d.consume?'逐批转序 '+(d.transferQty||d.qty):'合格 '+d.qty)+' 件':'全部完成'}${d.lag?' + '+d.lag+'分钟':''}</strong></div>`;}).join('');
    const body=`<dl class="task-meta-grid"><div><dt>生产订单</dt><dd>${E(t.orderId)}</dd></div><div><dt>产品 / 数量</dt><dd>${E(t.product)} · ${E(t.qty)} 件</dd></div><div><dt>工作中心</dt><dd>${E(t.workshop)} / ${E(t.wc)}</dd></div><div><dt>工艺模式</dt><dd>${E({manual:'纯人工',machine:'人机协同',auto:'自动运行 / 人工装卸',batch:'共享加工批次'}[t.mode]||t.mode)}</dd></div><div><dt>技能要求</dt><dd>${E(t.skill||'无需人员')} ${t.skill?'≥ '+(t.minLevel||1)+' 级':''}</dd></div><div><dt>当前进度</dt><dd>${E(t.good||0)} 合格 / ${E(t.bad||0)} 不良</dd></div></dl><div class="form-error" id="task-form-error" role="alert"></div>${execution?`<div class="notice">${I('lock-keyhole')}<span>该工序已有执行状态，调整保留已发生事实。</span></div>`:''}<form id="task-edit-form"><div class="field"><label for="task-start">候选开始时间</label><input id="task-start" name="start" type="datetime-local" value="${toInput(as?.start||0)}" step="60" required ${execution?'disabled':''}></div><div class="form-grid"><div class="field"><label for="task-machine">设备</label><select id="task-machine" name="machineId" ${!machines.length||execution?'disabled':''}>${machines.length?machines.map(r=>`<option value="${E(r.id)}" ${as?.machineId===r.id?'selected':''}>${E(r.name)}</option>`).join(''):'<option value="">纯人工</option>'}</select></div><div class="field"><label for="task-person">执行人员</label><select id="task-person" name="personId" ${!personOptions.length||execution?'disabled':''}>${personOptions.length?personOptions.map(r=>`<option value="${E(r.id)}" ${as?.personId===r.id?'selected':''}>${E(r.name)}</option>`).join(''):'<option value="">无需人员</option>'}</select></div></div><div class="field"><label for="task-lock">排程锁定</label><select id="task-lock" name="lock" ${execution?'disabled':''}>${[['none','不锁定'],['time','锁定时间'],['resource','锁定资源'],['all','全部锁定']].map(([v,l])=>`<option value="${v}" ${t.lock===v?'selected':''}>${l}</option>`).join('')}</select></div></form><section class="drawer-section"><h3>工序关系</h3>${deps||'<span class="muted">无前置工序</span>'}</section><section class="drawer-section"><h3>资源占用分段</h3><div class="segment-list">${as?(as.segments||[]).map(s=>`<div class="segment-row"><strong>${E({setup:'准备',run:'加工',unload:'卸料'}[s.kind]||s.kind)}</strong><div>${E(fmt(s.start))} — ${E(fmt(s.end))}<small>${E(s.machineId?rname(s.machineId):'无设备')} · ${E(s.personId?rname(s.personId):'无需人员到场')}</small></div></div>`).join(''):'<div class="muted">尚未分配</div>'}</div></section><section class="drawer-section"><h3>正式基线</h3><div class="info-row"><span>${E(A.state.published.id)}</span><strong>${pub?E(fmt(pub.start)+' — '+fmt(pub.end)):'未下达'}</strong></div>${t.batchId?`<div class="info-row"><span>共享批次</span><a class="text-link" href="#batches" data-close-drawer>${E(t.batchId)}</a></div>`:''}</section>`;
    UI.drawer(`${t.id} · ${t.op}`,body,A.btn('close-panel','','关闭','','secondary')+(!execution?A.btn('preview-task',id,'试算调整','scan-line','primary'):A.btn('go-execution',id,'查看现场','arrow-up-right','primary')));
  }
  function previewChanges(id,changes,lock) {
    const t=task(id);if(!t)return;
    let result;
    const adjustmentState=clone(A.state);
    if(lock==='none'){const changedTask=adjustmentState.tasks.find(x=>x.id===id);changedTask.lock='none';changedTask.lockAssignment=null;}
    try {result=Engine.adjust(adjustmentState,id,changes);}catch(error){UI.toast(error.message);return;}
    const errors=(result.issues||[]).filter(i=>i.severity!=='warning');
    if(errors.length){const el=document.getElementById('task-form-error');if(el)el.textContent=errors.map(i=>i.message).join('\n');else UI.modal('调整未应用',`<div class="issue-list">${errors.map(i=>`<div class="issue-item">${I('triangle-alert')}<div>${E(i.message)}</div></div>`).join('')}</div>`,A.btn('close-panel','','关闭'));return;}
    const before=A.state.draft.assignments.find(a=>a.taskId===id),after=result.assignments.find(a=>a.taskId===id);
    const beforeM=Engine.metrics(A.state,A.state.draft.assignments),afterM=Engine.metrics(A.state,result.assignments);
    A.pendingAdjustment={id,assignments:result.assignments,lock,revision:A.state.revision,basedOn:A.state.published.id};
    UI.modal('确认排程调整',`<div class="notice success">${I('shield-check')}<span>候选已通过全计划约束校验。</span></div>${A.table(['项目','调整前','调整后'],[['开始',E(fmt(before?.start)),E(fmt(after?.start))],['完成',E(fmt(before?.end)),E(fmt(after?.end))],['设备',E(rname(before?.machineId)),E(rname(after?.machineId))],['人员',E(rname(before?.personId)),E(rname(after?.personId))]])}<section class="drawer-section"><h3>订单交期影响</h3>${A.table(['订单','原预计完成','调整后'],(afterM.orders||[]).map(o=>[E(o.id),E(fmt(beforeM.orders.find(p=>p.id===o.id)?.end)),E(fmt(o.end))]))}</section>`,A.btn('close-panel','','取消')+A.btn('apply-adjustment','','应用到候选','check','primary'));
  }

  Actions['task-detail']=b=>openTask(b.dataset.id);
  Actions['close-panel']=()=>UI.closeModal();
  Actions['go-resources']=()=>A.go('resources');
  Actions['go-execution']=()=>{UI.closeDrawer();A.go('execution');};
  Actions['gantt-mode']=b=>{A.filters.ganttMode=b.dataset.id;A.render();};
  Actions['gantt-prev']=()=>{A.filters.day=Math.max(0,A.filters.day-1);A.render();};
  Actions['gantt-next']=()=>{A.filters.day=Math.min(4,A.filters.day+1);A.render();};
  Actions['gantt-today']=()=>{A.filters.day=0;A.render();};
  Actions['preview-task']=b=>{
    const form=document.getElementById('task-edit-form');if(!form.reportValidity())return;
    const start=(new Date(form.elements.start.value).getTime()-BASE)/60000;
    if(!Number.isFinite(start)){document.getElementById('task-form-error').textContent='请输入合法开始时间。';return;}
    previewChanges(b.dataset.id,{start,machineId:form.elements.machineId.value||null,personId:form.elements.personId.value||null},form.elements.lock.value);
  };
  Actions['apply-adjustment']=()=>{
    const p=A.pendingAdjustment;if(!p)return;
    if(p.revision!==A.state.revision||p.basedOn!==A.state.published.id){UI.toast('数据已发生变化，请重新试算。');return;}
    const proposedState=clone(A.state);if(p.lock==='none'){const proposedTask=proposedState.tasks.find(t=>t.id===p.id);proposedTask.lock='none';proposedTask.lockAssignment=null;}
    const errors=Engine.validate(proposedState,p.assignments).filter(i=>i.severity!=='warning');if(errors.length){UI.toast(errors[0].message);return;}
    takeUndo();setDraft(p.assignments);
    if(p.lock!=null){const t=task(p.id);t.lock=p.lock;t.lockAssignment=p.lock==='none'?null:clone(p.assignments.find(a=>a.taskId===p.id));}
    A.audit('调度调整',`调整 ${p.id} 的候选计划`,p.id);A.pendingAdjustment=null;UI.closeModal();A.commit('已更新候选，正式计划保持原版本。');
  };
  Actions['undo-plan']=()=>{
    const previous=A.undoStack.pop();if(!previous){UI.toast('暂无可撤销的排程调整。');return;}
    if(previous.revision!==A.state.revision){UI.toast('基础数据已更新，不能恢复过期候选。');return;}
    const restored=clone(A.state);(previous.locks||[]).forEach(l=>{const t=restored.tasks.find(t=>t.id===l.id);if(t){t.lock=l.lock;t.lockAssignment=l.lockAssignment;}});
    const check=Engine.validate(restored,previous.assignments).filter(i=>i.severity!=='warning');if(check.length){UI.toast(check[0].message);return;}
    (previous.locks||[]).forEach(l=>{const t=task(l.id);if(t){t.lock=l.lock;t.lockAssignment=l.lockAssignment;}});
    setDraft(previous.assignments);A.audit('撤销','撤销上一次候选排程调整');A.commit('已恢复上一份候选。');
  };
  Actions['run-plan']=async b=>{
    if(A.planning)return;A.planning=true;if(b){b.disabled=true;b.innerHTML='<span class="spinner"></span> 正在计算';}
    await new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)));
    try{
      const result=Engine.plan(A.state,{strategy:'priority'});takeUndo();setDraft(result.assignments);A.filters.scope='draft';
      A.audit('自动排程',`生成候选，已排 ${result.assignments.length} 道工序`);
      A.commit(result.issues?.some(i=>i.severity!=='warning')?'候选已生成，存在需要处理的约束问题。':'候选已生成，已校验人员、设备与工序关系。');
    }catch(error){UI.toast('排程未完成：'+error.message);}finally{A.planning=false;A.render();}
  };
  Actions['review-publish']=()=>{
    const list=issues(A.state.draft.assignments).filter(i=>i.severity!=='warning');
    const stale=A.state.draft.revision!==A.state.revision||A.state.draft.basedOn!==A.state.published.id;
    const before=Engine.metrics(A.state,A.state.published.assignments),after=Engine.metrics(A.state,A.state.draft.assignments);
    const changed=A.state.draft.assignments.filter(a=>JSON.stringify(a)!==JSON.stringify(A.state.published.assignments.find(p=>p.taskId===a.taskId))).length;
    UI.modal('审查并发布生产计划',`${stale?'<div class="notice warning">候选基线已过期，请重新生成或校验。</div>':''}${list.length?`<div class="notice warning">${I('triangle-alert')}<span>${list.length} 项约束问题需要处理后才能发布。</span></div><ul>${list.slice(0,6).map(i=>`<li>${E(i.message)}</li>`).join('')}</ul>`:`<div class="notice success">${I('shield-check')}<span>当前候选通过约束校验，${changed} 道工序分配发生变化。</span></div>`}${A.table(['订单','正式预计完成','候选预计完成'],after.orders.map(o=>[E(o.id),E(fmt(before.orders.find(p=>p.id===o.id)?.end)),E(fmt(o.end))]))}<div class="field" style="margin-top:18px"><label for="publish-note">发布说明</label><textarea id="publish-note" rows="2" placeholder="本次调整原因" required></textarea></div><label style="display:flex;gap:9px;align-items:flex-start;font-size:12px"><input type="checkbox" id="publish-confirm">已核对人员、设备和订单影响，将该版本作为车间执行计划</label><div class="form-error" id="publish-error" role="alert"></div>`,A.btn('close-panel','','取消')+`<button class="btn primary" data-action="confirm-publish" ${list.length||stale?'disabled':''}>${I('send')}确认发布</button>`);
  };
  Actions['confirm-publish']=()=>{
    const checked=document.getElementById('publish-confirm')?.checked,note=document.getElementById('publish-note')?.value.trim();
    if(!checked||!note){document.getElementById('publish-error').textContent='请填写发布说明，并确认影响范围。';return;}
    const result=Engine.publish(A.state,note);if(!result.ok){document.getElementById('publish-error').textContent=result.message;return;}
    UI.closeModal();A.undoStack=[];A.commit(result.message||'正式计划已更新。');
  };

  PageEvents.schedule=function(){
    document.getElementById('plan-scope').addEventListener('change',e=>{A.filters.scope=e.target.value;A.render();});
    document.getElementById('gantt-days').addEventListener('change',e=>{A.filters.days=Number(e.target.value);A.render();});
    const search=document.getElementById('gantt-search');search.addEventListener('input',e=>{A.filters.search=e.target.value;const pos=e.target.selectionStart;A.render();const next=document.getElementById('gantt-search');next.focus();try{next.setSelectionRange(pos,pos);}catch(_){}});
    let drag=null;
    document.querySelectorAll('.task-bar[draggable=true]').forEach(bar=>{
      bar.addEventListener('dragstart',e=>{drag={id:bar.dataset.id,start:Number(bar.dataset.start),segmentStart:Number(bar.dataset.segmentStart),x:e.clientX,source:bar.closest('.gantt-track').dataset.resourceId};bar.classList.add('dragging');e.dataTransfer.setData('text/plain',bar.dataset.id);e.dataTransfer.effectAllowed='move';});
      bar.addEventListener('dragend',()=>bar.classList.remove('dragging'));
    });
    document.querySelectorAll('.gantt-track').forEach(track=>{
      track.addEventListener('dragover',e=>{if(drag){e.preventDefault();e.dataTransfer.dropEffect='move';}});
      track.addEventListener('drop',e=>{
        if(!drag)return;e.preventDefault();
        if(track.dataset.resourceType==='workshop'&&track.dataset.resourceId!==drag.source){UI.toast('工序归属不能通过拖动改变，请在工艺与资源中维护兼容工作中心。');drag=null;return;}
        const delta=Math.round((e.clientX-drag.x)/.8/15)*15;
        const local=drag.start%1440+delta;
        const changes={start:drag.start+delta};
        if(local<0||local>600){UI.toast('跨日调整请在工序详情中选择准确时间。');drag=null;return;}
        if(track.dataset.resourceType==='machine')changes.machineId=track.dataset.resourceId;
        if(track.dataset.resourceType==='person')changes.personId=track.dataset.resourceId;
        previewChanges(drag.id,changes);drag=null;
      });
    });
  };
  function render(){
    let view=location.hash.slice(1)||'schedule';if(view==='main')view=A.view;
    A.view=names[view]?view:'schedule';
    document.title=`${names[A.view]} · 序程 APS`;
    document.getElementById('crumb').textContent=names[A.view];
    document.getElementById('header-version').textContent='正式计划 '+A.state.published.id;
    document.getElementById('navigation').innerHTML=nav.map(([group,items])=>`<div class="nav-group"><span class="nav-label">${group}</span>${items.map(([id,label,icon])=>`<a class="nav-item ${A.view===id?'active':''}" href="#${id}" ${A.view===id?'aria-current="page"':''}>${I(icon)}<span>${label}</span>${id==='orders'?`<span class="nav-count">${A.state.orders.length}</span>`:''}</a>`).join('')}</div>`).join('');
    try{
      const renderer=Views[A.view];
      if(!renderer)throw new Error('页面组件未载入：'+A.view);
      document.getElementById('main').innerHTML=renderer();UI.icons();
      if(typeof PageEvents[A.view]==='function')PageEvents[A.view]();
      else if(typeof PageEvents[A.view]?.afterRender==='function')PageEvents[A.view].afterRender();
    }catch(error){
      document.getElementById('main').innerHTML=`<div class="app-error"><h1>此页面暂时无法显示</h1><p>${E(error.message)}</p>${A.btn('recover-view','','返回排程工作台','arrow-left')}</div>`;UI.icons();console.error(error);
    }
  }
  Actions['recover-view']=()=>A.go('schedule');
  Actions['export-conflict']=()=>UI.download('序程-未合并编辑副本.json',JSON.stringify(A.state,null,2),'application/json');
  Actions['reload-storage']=()=>{const latest=UI.load(STORE,null);if(latest?.schema===2){A.state=latest;storageToken=latest._writeToken||null;A.undoStack=[];UI.closeModal();A.render();UI.toast('已载入最新本地计划。');}};
  document.addEventListener('click',e=>{
    const button=e.target.closest('[data-action]');if(!button||button.disabled)return;
    if(!['export-conflict','reload-storage','close-panel'].includes(button.dataset.action)&&!storageCurrent())return;
    const action=Actions[button.dataset.action];if(typeof action==='function'){try{const promise=action(button,e);if(promise?.catch)promise.catch(error=>{console.error(error);UI.toast(error.message);});}catch(error){console.error(error);UI.toast('操作未完成：'+error.message);}}
  });
  window.addEventListener('hashchange',()=>{UI.closeModal(false);A.render();});
  window.addEventListener('storage',e=>{if(e.key==='business-prototype-v1:'+STORE&&e.newValue){UI.toast('其他标签页已更新本地计划，请重新载入后编辑。');}});
  window.addEventListener('error',e=>{if(e.message&&!A.lastError){A.lastError=e.message;UI.toast('页面发生错误，当前正式计划不会自动改变。');}});
  if(!A.state._writeToken){A.state._writeToken=Date.now()+'-'+Math.random().toString(36).slice(2);storageToken=A.state._writeToken;UI.save(STORE,A.state);}render();
}());
