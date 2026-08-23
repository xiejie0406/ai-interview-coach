-- RuoYi 权限菜单增量（只写 MySQL sys_menu；不创建 AI 用户、角色或登录态）。
-- 执行前由平台管理员确认 menu_id 未占用，并按业务角色写入 sys_role_menu。
insert into sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time,
     update_by, update_time, remark)
select v.menu_id, v.menu_name, 0, v.order_num, '', '', '', '', 1, 0, 'F', '0', '0',
       v.perms, '#', 'migration', now(), '', null, 'AI Interview Coach permission'
from (
    select 2000 menu_id, 'Question list' menu_name, 1 order_num, 'interview:question:list' perms
    union all select 2001, 'Question add', 2, 'interview:question:add'
    union all select 2002, 'Question edit', 3, 'interview:question:edit'
    union all select 2003, 'Session start', 4, 'interview:session:start'
    union all select 2004, 'Session edit', 5, 'interview:session:edit'
    union all select 2005, 'Session submit', 6, 'interview:session:submit'
    union all select 2006, 'Report view', 7, 'interview:report:view'
    union all select 2007, 'Voice upload', 8, 'interview:voice:upload'
    union all select 2008, 'Voice confirm', 9, 'interview:voice:confirm'
    union all select 2009, 'Session recover', 10, 'interview:session:recover'
) v
where not exists (select 1 from sys_menu m where m.perms = v.perms);

-- 不在此脚本中默认授予角色；授权必须由 RuoYi sys_role_menu 的管理员操作完成。
