-- 仅供一次性 E2E 数据库使用；密码沿用 RuoYi 公开基线的合成 admin123 hash。
delete from sys_role_menu where role_id = 19001;
delete from sys_user_role where user_id = 19001 or role_id = 19001;
delete from sys_user where user_id = 19001 or user_name = 'aden_e2e';
delete from sys_role where role_id = 19001 or role_key = 'aden_e2e';

insert into sys_user
    (user_id, dept_id, user_name, nick_name, user_type, email, phonenumber, sex, avatar,
     password, status, del_flag, login_ip, login_date, pwd_update_date, create_by,
     create_time, update_by, update_time, remark)
values
    (19001, 103, 'aden_e2e', 'Aden 合成操作员', '00', '', '', '2', '',
     '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2',
     '0', '0', '127.0.0.1', null, now(), 'e2e', now(), '', null, '一次性 E2E 合成用户');

insert into sys_role
    (role_id, role_name, role_key, role_sort, data_scope, menu_check_strictly,
     dept_check_strictly, status, del_flag, create_by, create_time, update_by,
     update_time, remark)
values
    (19001, 'Aden E2E 合成角色', 'aden_e2e', 90, '1', 1, 1, '0', '0',
     'e2e', now(), '', null, '只授予 canonical aden:* 权限');

insert into sys_user_role(user_id, role_id) values (19001, 19001);
insert into sys_role_menu(role_id, menu_id)
select 19001, menu_id from sys_menu where perms like 'aden:%';

update sys_config set config_value = 'false' where config_key = 'sys.account.captchaEnabled';
