package com.ruoyi.aden.configuration;

import com.ruoyi.aden.application.collection.AdenCollectionService;
import com.ruoyi.aden.application.idempotency.AdenRequestFingerprint;
import com.ruoyi.aden.application.task.AdenTaskCasRepository;
import com.ruoyi.aden.application.task.AdenTaskLedgerRepository;
import com.ruoyi.aden.application.workspace.AdenWorkspaceAccessGuard;
import com.ruoyi.aden.infrastructure.persistence.AdenCollectionStore;
import com.ruoyi.aden.infrastructure.storage.AdenCollectionFiles;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;
import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix="aden.collection", name="enabled", havingValue="true")
public class AdenCollectionConfiguration {
    @Bean public AdenCollectionStore adenCollectionStore(@Qualifier("dynamicDataSource") DataSource dataSource) { return new AdenCollectionStore(dataSource); }
    @Bean public AdenCollectionFiles adenCollectionFiles(@Value("${aden.collection.storage-root}") String root) { return new AdenCollectionFiles(root); }
    @Bean public AdenCollectionService adenCollectionService(AdenCollectionStore store, AdenCollectionFiles files,
        AdenWorkspaceAccessGuard guard, AdenTaskLedgerRepository ledger, AdenTaskCasRepository cas,
        ObjectMapper json, AdenRequestFingerprint fingerprint,
        @Value("${aden.collection.workspace-quota-bytes:1073741824}") long quota,
        @Value("${aden.collection.fixture-origin:}") String fixtureOrigin) {
        return new AdenCollectionService(store, files, guard, ledger, cas, json, fingerprint, quota,fixtureOrigin);
    }
}
