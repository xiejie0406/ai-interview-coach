-- Aden 权限字符增量：仅定义 sys_menu 功能权限，不创建 workspace、成员、Runner、凭据或角色授权。
-- menu_id 2100-2114 为候选保留段；若 ID 已占用则跳过该行，由管理员分配不冲突 ID 后重跑。
insert into sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time,
     update_by, update_time, remark)
select v.menu_id, v.menu_name, 0, v.order_num, '', '', '', '', 1, 0, 'F', '0', '0',
       v.perms, '#', 'migration', now(), '', null, 'Aden permission only'
from (
    select 2100 menu_id, 'Aden workspace list' menu_name, 1 order_num, 'aden:workspace:list' perms
    union all select 2101, 'Aden workspace create', 2, 'aden:workspace:create'
    union all select 2102, 'Aden task list', 3, 'aden:task:list'
    union all select 2103, 'Aden task query', 4, 'aden:task:query'
    union all select 2104, 'Aden task create', 5, 'aden:task:create'
    union all select 2105, 'Aden task command', 6, 'aden:task:command'
    union all select 2106, 'Aden task cancel', 7, 'aden:task:cancel'
    union all select 2107, 'Aden event subscribe', 8, 'aden:event:subscribe'
    union all select 2108, 'Aden capability list', 9, 'aden:capability:list'
    union all select 2109, 'Aden runner list', 10, 'aden:runner:list'
    union all select 2110, 'Aden runner enroll', 11, 'aden:runner:enroll'
    union all select 2111, 'Aden runner revoke', 12, 'aden:runner:revoke'
    union all select 2112, 'Aden audit list', 13, 'aden:audit:list'
    union all select 2113, 'Aden agent manage', 14, 'aden:agent:manage'
    union all select 2114, 'Aden action approve', 15, 'aden:action:approve'
) v
where not exists (select 1 from sys_menu m where m.perms = v.perms)
  and not exists (select 1 from sys_menu m where m.menu_id = v.menu_id);

-- 刻意不写 sys_role_menu；角色授权必须由 RuoYi 管理员显式完成。
