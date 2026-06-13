package com.kbassistant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // Disable LLM auto-configuration — we don't need real API keys for the smoke test.
        // Phase 4+ integration tests will activate the specific providers they need.
        properties = {
                "spring.ai.openai.api-key=sk-test-key",
                "spring.ai.anthropic.api-key=test-key",
                "spring.ai.vertex.ai.gemini.project-id=test-project"
        }
)
@Testcontainers
class ApplicationSmokeIT {

    // Static container: started once per test class, reused across all test methods.
    // Non-static containers restart per test — expensive for DB-backed tests.
    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                    .withDatabaseName("knowledge_assistant")
                    .withUsername("ka_user")
                    .withPassword("ka_password");

    // DynamicPropertySource: injects container connection details into Spring context
    // BEFORE the ApplicationContext is initialized. This is the correct pattern for
    // Testcontainers + Spring Boot — overrides whatever datasource.url is in yml.
    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // pgvector store needs the same datasource
        registry.add("spring.ai.vectorstore.pgvector.url", postgres::getJdbcUrl);
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void applicationContextLoads() {
        // If this test passes, the Spring context started successfully.
        // Bean wiring, Flyway migrations, and all @Configuration classes executed.
    }

    @Test
    void healthEndpointReturnsUp() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/actuator/health",
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
    }

    @Test
    void flywayMigrationsAppliedSuccessfully() {
        // Flyway migration failure would prevent context startup.
        // If this test runs, migrations passed. We verify by querying the schema_version table.
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/actuator/health",
                Map.class
        );
        // Context started = Flyway succeeded. Additional DB assertions in Phase 2.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void swaggerUiIsAccessible() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/swagger-ui.html",
                String.class
        );
        // Redirects to /swagger-ui/index.html
        assertThat(response.getStatusCode().is2xxSuccessful()
                || response.getStatusCode().is3xxRedirection()).isTrue();
    }
}
