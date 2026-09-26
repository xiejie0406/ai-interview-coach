package com.ruoyi.fashion.configuration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** 智能选品模块总开关；缺省启用，显式关闭后不装配本模块的 Spring Bean。 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ConditionalOnProperty(prefix = "fashion", name = "enabled", havingValue = "true", matchIfMissing = true)
public @interface FashionModuleEnabled {
}
