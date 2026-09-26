package com.ruoyi.fashion.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class FashionPlatformSqlScriptTest {
    @Test
    void permissionScriptDoesNotGrantRolesOrCreateIdentities() throws IOException {
        String sql = script("ruoyi-fashion-permissions.sql").toLowerCase();
        assertTrue(sql.contains("insert into sys_menu"));
        assertFalse(Pattern.compile("insert\\s+into\\s+sys_role_menu").matcher(sql).find());
        assertFalse(Pattern.compile("insert\\s+into\\s+sys_user").matcher(sql).find());
        assertTrue(sql.contains("fashion:settings:list"));
        assertTrue(sql.contains("fashion:settings:edit"));
        assertTrue(sql.contains("fashion:quote:export"));
        assertTrue(sql.contains("fashion:operations:query"));
        assertTrue(sql.contains("fashion:operations:retention"));
        assertTrue(sql.contains("fashion:product:list"));
        assertTrue(sql.contains("fashion:product:query"));
        assertTrue(sql.contains("fashion:product:edit"));
        assertTrue(sql.contains("fashion:product:import"));
        assertTrue(sql.contains("fashion:product:image"));
        assertTrue(sql.contains("fashion:price:import"));
        assertTrue(sql.contains("fashion:stock:import"));
        assertTrue(sql.contains("fashion:import:restore"));
        assertTrue(sql.contains("fashion:customer:list"));
        assertTrue(sql.contains("fashion:quote:list"));
        assertTrue(sql.contains("fashion:ai:agent:publish"));
        assertTrue(sql.contains("fashion:ai:run:execute"));
        assertTrue(sql.contains("fashion:ai:run:apply"));
        assertTrue(sql.contains("fashion:image:list"));
        assertTrue(sql.contains("fashion:image:create"));
        assertTrue(sql.contains("fashion:image:review"));
        assertTrue(sql.contains("fashion/customer/index"));
        assertTrue(sql.contains("fashion/quote/index"));
        assertTrue(sql.contains("fashion/workbench/index"));
        assertTrue(sql.contains("fashion/agent/index"));
        assertTrue(sql.contains("fashion/image/index"));
        assertTrue(sql.contains("fashion/delivery/index"));
        assertTrue(sql.contains("fashion/operations/index"));
        assertFalse(Pattern.compile("update\\s+sys_menu\\s+set\\s+parent_id\\s*=\\s*menu_id")
                .matcher(sql).find());
    }

    @Test
    void dictionaryScriptContainsExactlySixApprovedTypesAndIsIdempotent() throws IOException {
        String sql = script("ruoyi-fashion-dictionaries.sql");
        Set<String> expected = Set.of(
                "fashion_product_source", "fashion_product_category", "fashion_product_color",
                "fashion_product_unit", "fashion_product_season", "fashion_warehouse");
        Matcher matcher = Pattern.compile("'(?<type>fashion_[a-z_]+)'").matcher(sql);
        Set<String> actual = new java.util.LinkedHashSet<>();
        while (matcher.find()) {
            actual.add(matcher.group("type"));
        }
        assertEquals(expected, actual);
        assertTrue(sql.toLowerCase().contains("not exists"));
        String template = Files.readString(workspacePath(
                "ruoyi-backend", "ruoyi-fashion", "src", "main", "resources",
                "fashion", "import-templates", "product-v1.csv"));
        String exampleSource = template.lines().skip(1).findFirst().orElseThrow().split(",", -1)[0];
        assertTrue(sql.contains("'" + exampleSource + "'"), "商品模板示例来源必须存在于平台字典脚本");
        String priceTemplate = Files.readString(workspacePath(
                "ruoyi-backend", "ruoyi-fashion", "src", "main", "resources",
                "fashion", "import-templates", "price-v1.csv"));
        String stockTemplate = Files.readString(workspacePath(
                "ruoyi-backend", "ruoyi-fashion", "src", "main", "resources",
                "fashion", "import-templates", "stock-v1.csv"));
        assertTrue(sql.contains("'" + priceTemplate.lines().skip(1).findFirst().orElseThrow().split(",", -1)[0] + "'"));
        assertTrue(sql.contains("'" + stockTemplate.lines().skip(1).findFirst().orElseThrow().split(",", -1)[2] + "'"));
    }

    @Test
    void configScriptContainsOnlyRegistryKeysAndNoSecret() throws IOException {
        String sql = script("ruoyi-fashion-config.sql");
        Matcher matcher = Pattern.compile("'(fashion\\.[A-Za-z0-9.]+)'").matcher(sql);
        Set<String> keys = new java.util.LinkedHashSet<>();
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        assertEquals(Set.of(
                "fashion.stock.freshnessHours", "fashion.quote.defaultValidDays",
                "fashion.quote.defaultDiscountPercent", "fashion.image.maxResults",
                "fashion.retention.messageDays", "fashion.retention.quoteDays",
                "fashion.retention.importFileDays", "fashion.retention.failedImageDays",
                "fashion.alert.runStuckMinutes", "fashion.alert.importFailurePercent",
                "fashion.alert.imagePendingCount", "fashion.alert.stockExpiredCount",
                "fashion.alert.deliveryFailurePercent",
                "fashion.ai.monthlyBudgetCny", "fashion.catalog.defaultWarehouse"), keys);
        assertFalse(Pattern.compile("(?i)'fashion\\.[^']*(secret|api[._-]?key)[^']*'").matcher(sql).find());
    }

    private static String script(String name) throws IOException {
        return Files.readString(workspacePath("ruoyi-backend", "sql", name));
    }

    private static Path workspacePath(String... parts) {
        Path current = Path.of("").toAbsolutePath();
        for (int up = 0; up < 5 && current != null; up++, current = current.getParent()) {
            Path candidate = current;
            for (String part : parts) {
                candidate = candidate.resolve(part);
            }
            if (Files.exists(candidate)) {
                return candidate.normalize();
            }
        }
        throw new IllegalStateException("无法定位 workspace 文件 " + String.join("/", parts));
    }
}
