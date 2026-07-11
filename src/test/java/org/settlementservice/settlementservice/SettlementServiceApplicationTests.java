package org.settlementservice.settlementservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

// Default: runs against a disposable MySQL container via Testcontainers.
// If Docker is ever unavailable, run with -Dspring.profiles.active=test or @ActiveProfiles("test") to fall back
// to a persistent local schema instead (see application-test.yaml) — no code change needed.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SettlementServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
