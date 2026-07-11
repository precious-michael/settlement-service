package org.settlementservice.settlementservice;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    /**
     * Skipped when the "test" profile is active (see application-test.yaml), so tests can
     * fall back to a persistent local schema if Docker is ever unavailable again — run with
     * -Dspring.profiles.active=test to switch, no code changes needed.
     */
    @Bean
    @ServiceConnection
    @Profile("!test")
    MySQLContainer mysqlContainer() {
        return new MySQLContainer(DockerImageName.parse("mysql:latest"));
    }

}
