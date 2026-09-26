-- FEAT-RUOYI-001：平台密钥、AI 密钥与 AI 角色。
-- 仅交付结构和菜单；不得在本文件中写入任何真实密钥或自动授予现有角色。
-- 在经授权的若依主 MySQL 上执行，执行前检查表名和 menu_id 冲突。

CREATE TABLE IF NOT EXISTS sys_managed_secret (
  secret_alias VARCHAR(128) NOT NULL PRIMARY KEY,
  secret_kind VARCHAR(16) NOT NULL,
  project_code VARCHAR(64) NOT NULL,
  provider_code VARCHAR(64) NOT NULL DEFAULT '',
  capability_code VARCHAR(64) NOT NULL DEFAULT '',
  auth_mode VARCHAR(32) NOT NULL DEFAULT '',
  display_name VARCHAR(128) NOT NULL,
  status VARCHAR(16) NOT NULL,
  active_version INT NULL,
  row_version BIGINT NOT NULL DEFAULT 0,
  created_by VARCHAR(64) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_by VARCHAR(64) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  KEY idx_secret_kind_project (secret_kind, project_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_managed_secret_version (
  secret_alias VARCHAR(128) NOT NULL,
  version_no INT NOT NULL,
  provider_code VARCHAR(64) NOT NULL DEFAULT '',
  capability_code VARCHAR(64) NOT NULL DEFAULT '',
  auth_mode VARCHAR(32) NOT NULL DEFAULT '',
  key_id VARCHAR(64) NOT NULL,
  nonce VARBINARY(12) NOT NULL,
  ciphertext LONGBLOB NOT NULL,
  created_by VARCHAR(64) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  change_reason VARCHAR(500) NOT NULL,
  PRIMARY KEY (secret_alias, version_no),
  CONSTRAINT fk_secret_version_alias FOREIGN KEY (secret_alias)
    REFERENCES sys_managed_secret (secret_alias)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_managed_secret_audit (
  audit_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  secret_alias VARCHAR(128) NOT NULL,
  secret_kind VARCHAR(16) NOT NULL,
  action_code VARCHAR(32) NOT NULL,
  version_no INT NULL,
  operator_name VARCHAR(64) NOT NULL,
  change_reason VARCHAR(500) NOT NULL,
  occurred_at DATETIME(3) NOT NULL,
  KEY idx_secret_audit_alias_time (secret_alias, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_ai_role (
  role_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  role_code VARCHAR(64) NOT NULL,
  project_code VARCHAR(64) NOT NULL,
  display_name VARCHAR(100) NOT NULL,
  purpose VARCHAR(500) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  active_version INT NULL,
  row_version BIGINT NOT NULL DEFAULT 0,
  created_by VARCHAR(64) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_by VARCHAR(64) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_ai_role_project_code (project_code, role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_ai_role_version (
  role_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  persona TEXT NOT NULL,
  system_prompt TEXT NOT NULL,
  provider_code VARCHAR(64) NOT NULL,
  model_name VARCHAR(128) NOT NULL,
  ai_secret_alias VARCHAR(128) NOT NULL,
  model_config_json JSON NOT NULL,
  content_hash CHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL,
  created_by VARCHAR(64) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  published_by VARCHAR(64) NULL,
  published_at DATETIME(3) NULL,
  PRIMARY KEY (role_id, version_no),
  CONSTRAINT fk_ai_role_version_role FOREIGN KEY (role_id)
    REFERENCES sys_ai_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 940100～940139 为本功能候选保留段；若有冲突应停止而不是覆盖。
INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
   is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time,
   update_by, update_time, remark)
SELECT 940100, '平台密钥', 1, 8, 'platform-secret', 'system/platformSecret/index', '', 'PlatformSecret',
       1, 0, 'C', '0', '0', 'system:platformSecret:list', 'lock', 'migration', NOW(), '', NULL,
       'FEAT-RUOYI-001 平台密钥'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 940100 OR perms = 'system:platformSecret:list');

INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
   is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time,
   update_by, update_time, remark)
SELECT 940110, 'AI 基础设施', 0, 19, 'ai-infrastructure', NULL, '', 'AiInfrastructure',
       1, 0, 'M', '0', '0', '', 'tree-table', 'migration', NOW(), '', NULL,
       'FEAT-RUOYI-001 AI 基础设施'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 940110 OR (parent_id = 0 AND path = 'ai-infrastructure'));

INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
   is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time,
   update_by, update_time, remark)
SELECT 940111, 'AI 密钥', 940110, 1, 'ai-secret', 'ai/secret/index', '', 'AiSecret',
       1, 0, 'C', '0', '0', 'ai:secret:list', 'lock', 'migration', NOW(), '', NULL,
       'FEAT-RUOYI-001 AI 密钥'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 940110 AND path = 'ai-infrastructure')
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 940111 OR perms = 'ai:secret:list');

INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
   is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time,
   update_by, update_time, remark)
SELECT 940120, 'AI 角色', 940110, 2, 'ai-role', 'ai/role/index', '', 'AiRole',
       1, 0, 'C', '0', '0', 'ai:role:list', 'peoples', 'migration', NOW(), '', NULL,
       'FEAT-RUOYI-001 AI 角色'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 940110 AND path = 'ai-infrastructure')
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 940120 OR perms = 'ai:role:list');

INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
   is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time,
   update_by, update_time, remark)
SELECT x.menu_id, x.menu_name, x.parent_id, x.order_num, '', '', '', '',
       1, 0, 'F', '1', '0', x.perms, '#', 'migration', NOW(), '', NULL,
       'FEAT-RUOYI-001 action'
FROM (
  SELECT 940101 menu_id, '平台密钥写入' menu_name, 940100 parent_id, 1 order_num, 'system:platformSecret:write' perms
  UNION ALL SELECT 940102, '平台密钥启停', 940100, 2, 'system:platformSecret:activate'
  UNION ALL SELECT 940103, '平台密钥审计', 940100, 3, 'system:platformSecret:audit'
  UNION ALL SELECT 940112, 'AI 密钥写入', 940111, 1, 'ai:secret:write'
  UNION ALL SELECT 940113, 'AI 密钥启停', 940111, 2, 'ai:secret:activate'
  UNION ALL SELECT 940114, 'AI 密钥审计', 940111, 3, 'ai:secret:audit'
  UNION ALL SELECT 940121, 'AI 角色编辑', 940120, 1, 'ai:role:write'
  UNION ALL SELECT 940122, 'AI 角色发布', 940120, 2, 'ai:role:publish'
) x
WHERE EXISTS (SELECT 1 FROM sys_menu p WHERE p.menu_id = x.parent_id)
  AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.menu_id = x.menu_id OR m.perms = x.perms);
