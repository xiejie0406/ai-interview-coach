-- FEAT-APS-001 RuoYi 权限码增量。
-- 只写 RuoYi MySQL 的 sys_menu，不连接 APS 业务库，不创建用户/角色，不默认授权。
-- menu_id 910100-910128 为 APS 保留段；脚本可重复执行。若 ID 被占用则跳过对应项，
-- 管理员须先排除冲突再重跑，并通过 sys_role_menu 按业务角色显式授权。
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
where not exists (select 1 from sys_menu m where m.perms = v.perms)
  and not exists (select 1 from sys_menu m where m.menu_id = v.menu_id);

-- 未完成业务入口由 APS 功能开关隐藏；本脚本只预置稳定权限码。
-- 不在此脚本中写 sys_role_menu，权限必须由 RuoYi 管理员按车间/工作中心范围授予。
