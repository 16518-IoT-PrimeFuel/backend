package com.primefuel.fulltank.platform.shared.events;

import com.primefuel.fulltank.platform.shared.infrastructure.persistence.jpa.repositories.EventPublicationPersistenceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:event_registry;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
class EventPublicationRegistryTest {

    @Autowired
    private EventPublicationRegistry registry;

    @Autowired
    private EventPublicationPersistenceRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void publishesAtomicallyWithTheCallersTransaction() {
        var template = new TransactionTemplate(transactionManager);
        var envelope = template.execute(status ->
                registry.publish("order.created.v1", "FuelOrder", "42", 7L, null, "{\"orderId\":42}"));

        var stored = repository.findByEventId(envelope.eventId().toString());
        assertThat(stored).isPresent();
        assertThat(stored.get().getOrganizationId()).isEqualTo(7L);
        assertThat(stored.get().getEventType()).isEqualTo("order.created.v1");
        assertThat(stored.get().getAggregateType()).isEqualTo("FuelOrder");
        assertThat(stored.get().getAggregateId()).isEqualTo("42");
        assertThat(stored.get().getPayload()).isEqualTo("{\"orderId\":42}");
        assertThat(stored.get().getCompletedAt()).isNull();
        assertThat(stored.get().getCreatedAt()).isNotNull();
    }

    @Test
    void rollbackLeavesNoPublication_simulatingACrash() {
        var template = new TransactionTemplate(transactionManager);
        template.execute(status -> {
            registry.publish("order.created.v1", "FuelOrder", "99", 7L, null, "{}");
            status.setRollbackOnly();
            return null;
        });

        assertThat(repository.findByAggregateTypeAndAggregateIdOrderByIdAsc("FuelOrder", "99")).isEmpty();
    }

    @Test
    void refusesToPublishOutsideATransaction() {
        assertThatThrownBy(() -> registry.publish("order.created.v1", "FuelOrder", "1", 7L, null, "{}"))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void refusesToPublishWithoutAnOrganizationId() {
        var template = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> template.execute(status ->
                registry.publish("order.created.v1", "FuelOrder", "1", null, null, "{}")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
