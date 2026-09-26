package com.ruoyi.fashion.infrastructure.persistence.foundation;

import com.ruoyi.fashion.configuration.FashionModuleEnabled;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.ruoyi.common.exception.ServiceException;
import org.springframework.stereotype.Component;

/** MySQL JSON 公共编码器；只接受结构化值，不拼接 SQL 或吞掉解析错误。 */
@FashionModuleEnabled
@Component
public final class FashionJsonCodec {
    private final ObjectMapper objectMapper;

    public FashionJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new ServiceException("Fashion JSON 编码失败");
        }
    }

    public JsonNode readTree(String value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (JacksonException exception) {
            throw new ServiceException("Fashion JSON 数据格式无效");
        }
    }

    public ObjectNode objectNode() {
        return objectMapper.createObjectNode();
    }
}
