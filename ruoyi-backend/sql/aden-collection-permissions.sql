-- 只定义权限，不自动授权任何正式角色；角色和Workspace成员资格仍由管理员配置。
insert into sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time,
     update_by, update_time, remark)
select v.menu_id, v.menu_name, 0, v.order_num, '', '', '', '', 1, 0, 'F', '0', '0',
       v.perms, '#', 'migration', now(), '', null, 'Aden collection permission only'
from (
    select 2120 menu_id, 'Aden 商品采集查看' menu_name, 21 order_num, 'aden:collection:read' perms
    union all select 2121, 'Aden 当前商品采集', 22, 'aden:collection:capture'
    union all select 2122, 'Aden 手动新增商品', 23, 'aden:collection:create'
    union all select 2123, 'Aden 商品整理', 24, 'aden:collection:edit'
    union all select 2124, 'Aden 商品移入回收站', 25, 'aden:collection:delete'
    union all select 2125, 'Aden 商品恢复', 26, 'aden:collection:restore'
    union all select 2126, 'Aden 商品导出', 27, 'aden:collection:export'
) v
where not exists (select 1 from sys_menu m where m.perms = v.perms)
  and not exists (select 1 from sys_menu m where m.menu_id = v.menu_id);
