package com.ruoyi.interview.domain.catalog;

import com.ruoyi.interview.domain.platform.DomainErrorCode;
import com.ruoyi.interview.domain.platform.DomainPreconditions;

import java.util.Set;

/**
 * 题库稳定模块代码。GENERAL 只用于 V8 迁移前历史行的显式兼容，不允许新建内容使用。
 */
public final class QuestionCategory {

    public static final String GENERAL = "GENERAL";
    public static final String NEW_CATEGORY_PATTERN =
            "AGENT_BASICS_DEEP_V3|LLM_FOUNDATION_DEEP_V3|PROMPT_ENGINEERING_DEEP_V3|RAG_DEEP_V3|"
                    + "KNOWLEDGE_BASE_DEEP_V3|WORKFLOW_DEEP_V3|TOOL_CALLING_DEEP_V3|MEMORY_DEEP_V3|"
                    + "MULTI_AGENT_DEEP_V3|EVALUATION_DEEP_V3|AGENT_SECURITY_DEEP_V3|AGENT_ENGINEERING_DEEP_V3";
    public static final String STORED_CATEGORY_PATTERN = "GENERAL|" + NEW_CATEGORY_PATTERN;

    /** 公开题库只允许已归类的 V2 模块；GENERAL 仅供迁移/治理回读。 */
    public static final String PUBLIC_CATEGORY_PATTERN = NEW_CATEGORY_PATTERN;

    /** Portal 当前承诺的 12 个固定模块；顺序保持产品导航顺序。 */
    public static final Set<String> FIXED_MODULES = Set.of(
            "AGENT_BASICS_DEEP_V3",
            "LLM_FOUNDATION_DEEP_V3",
            "PROMPT_ENGINEERING_DEEP_V3",
            "RAG_DEEP_V3",
            "KNOWLEDGE_BASE_DEEP_V3",
            "WORKFLOW_DEEP_V3",
            "TOOL_CALLING_DEEP_V3",
            "MEMORY_DEEP_V3",
            "MULTI_AGENT_DEEP_V3",
            "EVALUATION_DEEP_V3",
            "AGENT_SECURITY_DEEP_V3",
            "AGENT_ENGINEERING_DEEP_V3");

    private QuestionCategory() {
    }

    /** 校验数据库中可存储的值（允许迁移回填的 GENERAL）。 */
    public static String requireStored(String value) {
        String normalized = DomainPreconditions.requireText(value, "category");
        DomainPreconditions.require(FIXED_MODULES.contains(normalized) || GENERAL.equals(normalized),
                DomainErrorCode.INVALID_ARGUMENT, "category is not a supported module");
        return normalized;
    }

    /** 校验新建/新版本请求；GENERAL 仅为旧数据兼容值。 */
    public static String requireNew(String value) {
        String normalized = DomainPreconditions.requireText(value, "category");
        DomainPreconditions.require(FIXED_MODULES.contains(normalized),
                DomainErrorCode.INVALID_ARGUMENT, "new question category must be one of the fixed modules");
        return normalized;
    }

    public static boolean isSupported(String value) {
        return FIXED_MODULES.contains(value) || GENERAL.equals(value);
    }
}
