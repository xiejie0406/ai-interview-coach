-- IMP-08：结构调整请求的技术幂等指纹与用户可读变更说明分离。
-- change_note 可在候选发布或废弃时更新；request_fingerprint 一经创建不再修改。

ALTER TABLE aps_plan_version
    ADD COLUMN request_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER request_id,
    ADD CONSTRAINT ck_plan_version_request_fingerprint CHECK (
        request_fingerprint IS NULL OR request_fingerprint REGEXP '^[0-9a-f]{64}$'
    );
