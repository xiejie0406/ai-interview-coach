package com.ruoyi.interview.configuration;

import com.ruoyi.interview.application.catalog.SearchPublishedQuestions;
import com.ruoyi.interview.application.catalog.internal.DefaultSearchPublishedQuestions;
import com.ruoyi.interview.application.catalog.port.PublishedQuestionPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Catalog 公开只读查询的组合根绑定。管理写入工作流仍保持独立开关。 */
@Configuration
public class CatalogUseCaseConfiguration {
    @Bean
    @ConditionalOnMissingBean(SearchPublishedQuestions.class)
    SearchPublishedQuestions searchPublishedQuestions(PublishedQuestionPort publishedQuestions) {
        return new DefaultSearchPublishedQuestions(publishedQuestions);
    }
}

