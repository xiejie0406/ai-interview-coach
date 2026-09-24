package com.ruoyi.fashion.application.foundation.port;

import java.util.Set;

/** 只读 Schema 探针；用于 readiness/诊断，不执行 migration 或业务写入。 */
public interface FashionSchemaRepository {
    String currentDatabase();

    Set<String> businessTables();
}
