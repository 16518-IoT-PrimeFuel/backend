package com.primefuel.fulltank.platform.shared.events;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:event_inbox;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
class EventInboxTest {

    @Autowired
    private EventInbox inbox;

    @Test
    void consumesAnEventOncePerConsumer() {
        assertThat(inbox.consume("notification", "event-1")).isTrue();
        assertThat(inbox.consume("notification", "event-1")).isFalse();
        assertThat(inbox.consume("reporting", "event-1")).isTrue();
    }
}
