package com.ruoyi.interview.configuration;

/**
 * AI 业务权限编码的唯一代码入口；权限事实仍存储在 RuoYi sys_menu。
 */
public final class InterviewPermissionCodes {

    public static final String QUESTION_LIST = "interview:question:list";
    public static final String QUESTION_ADD = "interview:question:add";
    public static final String QUESTION_EDIT = "interview:question:edit";
    /** 审核、发布和下线专用权限，不与普通内容编辑权限混用。 */
    public static final String QUESTION_REVIEW = "interview:question:review";
    public static final String SESSION_START = "interview:session:start";
    public static final String SESSION_RECOVER = "interview:session:recover";
    public static final String SESSION_EDIT = "interview:session:edit";
    public static final String REPORT_VIEW = "interview:report:view";
    public static final String REPORT_EXPORT = "interview:report:export";
    public static final String VOICE_UPLOAD = "interview:voice:upload";
    public static final String VOICE_CONFIRM = "interview:voice:confirm";

    private InterviewPermissionCodes() {
    }
}
