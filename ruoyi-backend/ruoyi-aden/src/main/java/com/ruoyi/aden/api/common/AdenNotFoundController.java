package com.ruoyi.aden.api.common;

import com.ruoyi.aden.application.error.AdenNotFoundException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 保证未匹配的 Aden 路由仍使用 Aden ErrorEnvelope，而不是空 404 或 RuoYi 错误包。 */
@RestController
@RequestMapping("/api/v1/aden")
@ConditionalOnProperty(prefix = "aden", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AdenNotFoundController {
    @RequestMapping("/**")
    public void notFound() {
        throw new AdenNotFoundException();
    }
}
