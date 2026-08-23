-- 面试属于用户租户，但题目来自独立公共题库租户；版本 ID 全局唯一并作为不可变引用。
alter table catalog.question_version
    add constraint question_version_global_ref
        unique (question_version_id, version_no, content_hash);

alter table catalog.rubric_version
    add constraint rubric_version_global_ref
        unique (rubric_version_id, version_no, content_hash, question_version_id);

alter table interview.plan_question
    drop constraint plan_question_tenant_id_question_version_id_question_versi_fkey,
    drop constraint plan_question_tenant_id_rubric_version_id_rubric_version_n_fkey,
    add constraint plan_question_global_question_version_fk
        foreign key (question_version_id, question_version_no, question_content_hash)
        references catalog.question_version (question_version_id, version_no, content_hash),
    add constraint plan_question_global_rubric_version_fk
        foreign key (rubric_version_id, rubric_version_no, rubric_content_hash, question_version_id)
        references catalog.rubric_version (rubric_version_id, version_no, content_hash, question_version_id);

alter table interview.session_plan_question
    drop constraint session_plan_question_tenant_id_question_version_id_questi_fkey,
    drop constraint session_plan_question_tenant_id_rubric_version_id_rubric_v_fkey,
    add constraint session_plan_question_global_question_version_fk
        foreign key (question_version_id, question_version_no, question_content_hash)
        references catalog.question_version (question_version_id, version_no, content_hash),
    add constraint session_plan_question_global_rubric_version_fk
        foreign key (rubric_version_id, rubric_version_no, rubric_content_hash, question_version_id)
        references catalog.rubric_version (rubric_version_id, version_no, content_hash, question_version_id);
