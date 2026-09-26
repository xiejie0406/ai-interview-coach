package com.ruoyi.fashion.application.security;

import com.ruoyi.fashion.configuration.FashionModuleEnabled;

import java.util.Arrays;
import java.util.List;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import org.springframework.stereotype.Component;

/** 客户资源访问守卫：管理员、负责人或已保存的协作者才可访问。 */
@FashionModuleEnabled
@Component
public final class FashionCustomerAccessGuard {
    private final ObjectMapper objectMapper;

    public FashionCustomerAccessGuard(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void requireCurrentUser(long salespersonId, String collaboratorIdsJson) {
        requireUser(salespersonId, collaboratorIdsJson, SecurityUtils.getUserId(), SecurityUtils.isAdmin());
    }

    public void requireCurrentUser(long salespersonId, List<Long> collaboratorIds) {
        long userId = SecurityUtils.getUserId();
        if (SecurityUtils.isAdmin() || salespersonId == userId
                || (collaboratorIds != null && collaboratorIds.contains(userId))) {
            return;
        }
        throw new ServiceException("无权访问该客户资源", 403);
    }

    void requireUser(long salespersonId, String collaboratorIdsJson, long userId, boolean administrator) {
        if (administrator || salespersonId == userId) {
            return;
        }
        if (isCollaborator(collaboratorIdsJson, userId)) {
            return;
        }
        throw new ServiceException("无权访问该客户资源", 403);
    }

    private boolean isCollaborator(String value, long userId) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(value);
            if (!root.isArray()) {
                throw new ServiceException("客户协作者数据格式无效");
            }
            return Arrays.stream(objectMapper.treeToValue(root, Long[].class))
                    .anyMatch(id -> id != null && id == userId);
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServiceException("客户协作者数据格式无效");
        }
    }
}
