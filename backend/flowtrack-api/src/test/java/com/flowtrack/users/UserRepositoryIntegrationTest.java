package com.flowtrack.users;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(
    replace = AutoConfigureTestDatabase.Replace.NONE
)
class UserRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("flowtrack_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void shouldNotCreateDuplicateUser() {

        UUID keycloakId = UUID.randomUUID();

        int firstInsert = userRepository.insertIfAbsent(
                keycloakId,
                "alice@test.local",
                "alice"
        );

        int secondInsert = userRepository.insertIfAbsent(
                keycloakId,
                "alice@test.local",
                "alice"
        );

        assertEquals(1, firstInsert);
        assertEquals(0, secondInsert);

        assertTrue(userRepository.findByKeycloakId(keycloakId)
                .isPresent());
    }
    
    @Test
    void shouldNotCreateDuplicateUserWithConcurrentRequests() throws Exception {

    UUID keycloakId = UUID.randomUUID();

    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);

    ExecutorService executor = Executors.newFixedThreadPool(2);

    try {
        java.util.concurrent.Callable<Integer> task = () -> {
            ready.countDown();

            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timeout waiting for start");
            }

            TransactionTemplate transaction =
                    new TransactionTemplate(transactionManager);

            return transaction.execute(status ->
                    userRepository.insertIfAbsent(
                            keycloakId,
                            "alice@test.local",
                            "alice"
                    )
            );
        };

        Future<Integer> first = executor.submit(task);
        Future<Integer> second = executor.submit(task);

        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();

        int firstResult = first.get(30, TimeUnit.SECONDS);
        int secondResult = second.get(30, TimeUnit.SECONDS);

        assertEquals(1, firstResult + secondResult);

        assertTrue(userRepository.findByKeycloakId(keycloakId)
                .isPresent());

    } finally {
        start.countDown();
        executor.shutdownNow();
    }
}
}
