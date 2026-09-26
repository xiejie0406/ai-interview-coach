package com.ruoyi.interview.domain.governance;

/**
 * 业务语义角色，仅用于描述面试内容协作能力。
 * 运行时授权仍以 RuoYi sys_role/sys_menu 的权限编码为准。
 */
public enum BusinessRole {
    OWNER,
    MEMBER,
    CONTENT_ADMIN,
    OPS_ADMIN,
    SUPPORT,
    PRIVACY_AUDITOR,
    SUPER_ADMIN
}
