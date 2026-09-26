package com.ruoyi.fashion.application.settings;

import com.ruoyi.fashion.configuration.FashionModuleEnabled;

import java.util.List;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.fashion.application.security.FashionDictionaryGuard;
import com.ruoyi.system.domain.SysConfig;
import com.ruoyi.system.service.ISysConfigService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 在 sys_config 上提供类型化白名单读写，不暴露任意键或 Secret。 */
@FashionModuleEnabled
@Service
public class FashionSettingsService {
    private final FashionSettingRegistry registry;
    private final ISysConfigService configService;
    private final FashionDictionaryGuard dictionaryGuard;

    public FashionSettingsService(
            FashionSettingRegistry registry,
            ISysConfigService configService,
            FashionDictionaryGuard dictionaryGuard) {
        this.registry = registry;
        this.configService = configService;
        this.dictionaryGuard = dictionaryGuard;
    }

    public List<FashionSettingView> list() {
        return registry.all().stream().map(this::view).toList();
    }

    @Transactional(transactionManager = "fashionTransactionManager")
    public FashionSettingChange update(String key, String value, String operator) {
        FashionSettingDefinition definition;
        String normalized;
        try {
            definition = registry.require(key);
            normalized = definition.validate(value);
        } catch (IllegalArgumentException exception) {
            throw new ServiceException(exception.getMessage());
        }
        if (definition.type() == FashionSettingType.DICTIONARY_CODE) {
            dictionaryGuard.requireActiveValue(definition.dictionaryType(), normalized);
        }
        SysConfig existing = find(key);
        String before = existing == null ? definition.defaultValue() : existing.getConfigValue();
        if (existing == null) {
            SysConfig created = new SysConfig();
            created.setConfigName(definition.name());
            created.setConfigKey(definition.key());
            created.setConfigValue(normalized);
            created.setConfigType("N");
            created.setCreateBy(operator);
            created.setRemark("Fashion 类型化配置；只允许通过 /fashion/settings 更新");
            if (configService.insertConfig(created) != 1) {
                throw new ServiceException("新增 Fashion 配置失败");
            }
        } else {
            existing.setConfigValue(normalized);
            existing.setUpdateBy(operator);
            if (configService.updateConfig(existing) != 1) {
                throw new ServiceException("更新 Fashion 配置失败");
            }
        }
        return new FashionSettingChange(key, before, normalized, operator);
    }

    private FashionSettingView view(FashionSettingDefinition definition) {
        String configured = configService.selectConfigByKey(definition.key());
        String value = configured == null || configured.isBlank() ? definition.defaultValue() : configured;
        return new FashionSettingView(
                definition.key(), definition.name(), definition.description(), definition.type(), value,
                definition.defaultValue(), definition.dictionaryType());
    }

    private SysConfig find(String key) {
        SysConfig query = new SysConfig();
        query.setConfigKey(key);
        List<SysConfig> matches = configService.selectConfigList(query);
        if (matches == null || matches.isEmpty()) {
            return null;
        }
        if (matches.size() != 1 || !key.equals(matches.get(0).getConfigKey())) {
            throw new ServiceException("Fashion 配置键数据不唯一：" + key);
        }
        return matches.get(0);
    }
}
