-- FEAT-APS-001 RuoYi 菜单与权限码增量。
-- 只写 RuoYi MySQL 的 sys_menu，不连接 APS 业务库，不创建用户/角色，不默认授权。
-- menu_id 910000-910007（目录/页面）、910100-910128（按钮）为 APS 保留段。
-- 脚本可重复执行；若 ID 或路径被其他菜单占用，先排除冲突再重跑。
insert into sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time,
     update_by, update_time, remark)
select v.menu_id, v.menu_name, 0, v.order_num, '', '', '', '', 1, 0, 'F', '1', '0',
       v.perms, '#', 'migration', now(), '', null, 'FEAT-APS-001 permission'
from (
    select 910100 menu_id, 'APS 就绪检查' menu_name, 1 order_num, 'aps:readiness:view' perms
    union all select 910101, 'APS 资源查看', 2, 'aps:resource:list'
    union all select 910102, 'APS 资源新增', 3, 'aps:resource:add'
    union all select 910103, 'APS 资源修改', 4, 'aps:resource:edit'
    union all select 910104, 'APS 资源导入', 5, 'aps:resource:import'
    union all select 910105, 'APS 工艺查看', 6, 'aps:routing:list'
    union all select 910106, 'APS 工艺新增', 7, 'aps:routing:add'
    union all select 910107, 'APS 工艺修改', 8, 'aps:routing:edit'
    union all select 910108, 'APS 工艺发布', 9, 'aps:routing:publish'
    union all select 910109, 'APS 订单查看', 10, 'aps:order:list'
    union all select 910110, 'APS 订单新增', 11, 'aps:order:add'
    union all select 910111, 'APS 订单修改', 12, 'aps:order:edit'
    union all select 910112, 'APS 订单导入', 13, 'aps:order:import'
    union all select 910113, 'APS 订单释放', 14, 'aps:order:release'
    union all select 910114, 'APS 数据校验', 15, 'aps:planning:validate'
    union all select 910115, 'APS 发起排程', 16, 'aps:planning:solve'
    union all select 910116, 'APS 取消排程', 17, 'aps:planning:cancel'
    union all select 910117, 'APS 计划查看', 18, 'aps:planning:view'
    union all select 910118, 'APS 人工调整', 19, 'aps:planning:adjust'
    union all select 910119, 'APS 计划锁定', 20, 'aps:planning:lock'
    union all select 910120, 'APS 计划发布', 21, 'aps:planning:publish'
    union all select 910121, 'APS 执行查看', 22, 'aps:execution:view'
    union all select 910122, 'APS 开工', 23, 'aps:execution:start'
    union all select 910123, 'APS 暂停', 24, 'aps:execution:pause'
    union all select 910124, 'APS 报工', 25, 'aps:execution:report'
    union all select 910125, 'APS 质量处置', 26, 'aps:execution:quality'
    union all select 910126, 'APS 报表查看', 27, 'aps:report:view'
    union all select 910127, 'APS 报表导出', 28, 'aps:report:export'
    union all select 910128, 'APS 数据范围管理', 29, 'aps:scope:manage'
) v
where not exists (select 1 from sys_menu m where m.perms = v.perms and m.menu_type = 'F')
  and not exists (select 1 from sys_menu m where m.menu_id = v.menu_id);

-- 页面使用已有查看权限码，目录不带权限码；普通角色仍须显式获授目录和页面菜单。
insert into sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time,
     update_by, update_time, remark)
select 910000, '生产排产系统', 0, 5, 'aps', '', '', 'ApsSystem',
       1, 0, 'M', '0', '0', '', 'chart', 'migration', now(), '', null,
       'FEAT-APS-001 navigation'
where not exists (select 1 from sys_menu where menu_id = 910000)
  and not exists (select 1 from sys_menu where parent_id = 0 and path = 'aps' and menu_type = 'M');

insert into sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time,
     update_by, update_time, remark)
select v.menu_id, v.menu_name, 910000, v.order_num, v.path, v.component, '', v.route_name,
       1, 0, 'C', '0', '0', v.perms, v.icon, 'migration', now(), '', null,
       'FEAT-APS-001 navigation'
from (
    select 910001 menu_id, '产品与工艺路线' menu_name, 1 order_num, 'routes' path, 'aps/routes/index' component, 'ApsRoutes' route_name, 'aps:routing:list' perms, 'tree' icon
    union all select 910002, '订单与任务展开', 2, 'orders', 'aps/orders/index', 'ApsOrders', 'aps:order:list', 'list'
    union all select 910003, '资源与日历', 3, 'resources', 'aps/resources/index', 'ApsResources', 'aps:resource:list', 'tree-table'
    union all select 910004, '资源数据就绪', 4, 'readiness', 'aps/readiness/index', 'ApsResourceReadiness', 'aps:readiness:view', 'validCode'
    union all select 910005, '生产排程工作台', 5, 'workbench', 'aps/workbench/index', 'ApsProductionWorkbench', 'aps:planning:view', 'chart'
    union all select 910006, '现场执行', 6, 'execution', 'aps/execution/index', 'ApsExecution', 'aps:execution:view', 'time'
    union all select 910007, '生产与交期报表', 7, 'reports', 'aps/reports/index', 'ApsReports', 'aps:report:view', 'chart'
) v
where exists (select 1 from sys_menu p where p.menu_id = 910000 and p.path = 'aps' and p.menu_type = 'M')
  and not exists (select 1 from sys_menu m where m.menu_id = v.menu_id)
  and not exists (select 1 from sys_menu m where m.parent_id = 910000 and m.path = v.path and m.menu_type = 'C');

-- 将原有按钮权限归入对应页面，不改变权限码或已有角色授权。
update sys_menu m
join (
    select 910100 menu_id, 910004 parent_id, 'aps:readiness:view' perms
    union all select 910101, 910003, 'aps:resource:list'
    union all select 910102, 910003, 'aps:resource:add'
    union all select 910103, 910003, 'aps:resource:edit'
    union all select 910104, 910003, 'aps:resource:import'
    union all select 910105, 910001, 'aps:routing:list'
    union all select 910106, 910001, 'aps:routing:add'
    union all select 910107, 910001, 'aps:routing:edit'
    union all select 910108, 910001, 'aps:routing:publish'
    union all select 910109, 910002, 'aps:order:list'
    union all select 910110, 910002, 'aps:order:add'
    union all select 910111, 910002, 'aps:order:edit'
    union all select 910112, 910002, 'aps:order:import'
    union all select 910113, 910002, 'aps:order:release'
    union all select 910114, 910004, 'aps:planning:validate'
    union all select 910115, 910005, 'aps:planning:solve'
    union all select 910116, 910005, 'aps:planning:cancel'
    union all select 910117, 910005, 'aps:planning:view'
    union all select 910118, 910005, 'aps:planning:adjust'
    union all select 910119, 910005, 'aps:planning:lock'
    union all select 910120, 910005, 'aps:planning:publish'
    union all select 910121, 910006, 'aps:execution:view'
    union all select 910122, 910006, 'aps:execution:start'
    union all select 910123, 910006, 'aps:execution:pause'
    union all select 910124, 910006, 'aps:execution:report'
    union all select 910125, 910006, 'aps:execution:quality'
    union all select 910126, 910007, 'aps:report:view'
    union all select 910127, 910007, 'aps:report:export'
    union all select 910128, 910003, 'aps:scope:manage'
) v on m.menu_id = v.menu_id and m.perms = v.perms and m.menu_type = 'F'
join sys_menu p on p.menu_id = v.parent_id and p.parent_id = 910000 and p.menu_type = 'C'
set m.parent_id = v.parent_id
where m.parent_id = 0;

-- 不在此脚本中写 sys_role_menu，权限必须由 RuoYi 管理员按业务角色显式授权。
