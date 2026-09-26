package com.ruoyi.fashion.application.settings;

import com.ruoyi.fashion.configuration.FashionModuleEnabled;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

import org.springframework.stereotype.Component;

/** Fashion 可读写配置的封闭注册表；未注册键一律不可通过业务 API 访问。 */
@FashionModuleEnabled
@Component
public final class FashionSettingRegistry {
    private final Map<String, FashionSettingDefinition> definitions;

    public FashionSettingRegistry() {
        List<FashionSettingDefinition> ordered = List.of(
                integer("fashion.stock.freshnessHours", "库存新鲜度", "库存超过该小时数需重新核实", "24", 1, 168),
                integer("fashion.quote.defaultValidDays", "报价默认有效期", "新报价默认有效天数", "7", 1, 30),
                decimal("fashion.quote.defaultDiscountPercent", "默认折扣率", "百分数，5.00 表示优惠 5%", "5.00", "0", "100"),
                integer("fashion.image.maxResults", "单任务图片上限", "一次图片任务最多结果数", "2", 1, 4),
                integer("fashion.retention.messageDays", "消息保留天数", "AI 消息保留策略天数", "365", 30, 3650),
                integer("fashion.retention.quoteDays", "报价与交付保留天数", "方案、确认报价及交付文件默认保留天数", "365", 30, 3650),
                integer("fashion.retention.importFileDays", "原始导入文件保留天数", "原始导入文件默认保留天数", "90", 7, 3650),
                integer("fashion.retention.failedImageDays", "失败素材保留天数", "失败且未采用图片默认保留天数", "30", 7, 3650),
                integer("fashion.alert.runStuckMinutes", "Run 卡住阈值", "运行超过该分钟数进入告警候选", "15", 1, 1440),
                decimal("fashion.alert.importFailurePercent", "批次失败率阈值", "仅形成告警候选，不代表通知已送达", "10.00", "0", "100"),
                integer("fashion.alert.imagePendingCount", "图片失败待查阈值", "仅形成告警候选，不代表通知已送达", "10", 1, 100000),
                integer("fashion.alert.stockExpiredCount", "库存过期数量阈值", "仅形成告警候选，不代表通知已送达", "50", 1, 10000000),
                decimal("fashion.alert.deliveryFailurePercent", "文件失败率阈值", "仅形成告警候选，不代表通知已送达", "10.00", "0", "100"),
                decimal("fashion.ai.monthlyBudgetCny", "AI 月度预算", "人民币月度费用上限，0 表示禁用付费调用", "0.00", "0", "10000000"),
                new FashionSettingDefinition(
                        "fashion.catalog.defaultWarehouse", "默认仓库", "新方案默认核验仓库编码",
                        FashionSettingType.DICTIONARY_CODE, "MAIN", "fashion_warehouse",
                        value -> value.matches("[A-Z0-9][A-Z0-9_-]{0,63}"), "默认仓库必须是 1～64 位大写编码"));
        LinkedHashMap<String, FashionSettingDefinition> mapped = new LinkedHashMap<>();
        for (FashionSettingDefinition definition : ordered) {
            mapped.put(definition.key(), definition);
        }
        definitions = Collections.unmodifiableMap(mapped);
    }

    public List<FashionSettingDefinition> all() {
        return definitions.values().stream().toList();
    }

    public FashionSettingDefinition require(String key) {
        FashionSettingDefinition definition = definitions.get(key);
        if (definition == null) {
            throw new IllegalArgumentException("不允许访问未注册的 Fashion 配置键");
        }
        return definition;
    }

    private static FashionSettingDefinition integer(
            String key, String name, String description, String defaultValue, int min, int max) {
        return new FashionSettingDefinition(
                key, name, description, FashionSettingType.INTEGER, defaultValue, null,
                FashionSettingDefinition.integerRange(min, max),
                name + "必须是 " + min + "～" + max + " 之间的整数");
    }

    private static FashionSettingDefinition decimal(
            String key, String name, String description, String defaultValue, String min, String max) {
        return new FashionSettingDefinition(
                key, name, description, FashionSettingType.DECIMAL, defaultValue, null,
                FashionSettingDefinition.decimalRange(min, max),
                name + "必须是 " + min + "～" + max + " 之间且最多两位小数的数字");
    }
}
