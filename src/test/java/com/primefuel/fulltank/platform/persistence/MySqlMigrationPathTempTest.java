package com.primefuel.fulltank.platform.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:mysql://localhost:3306/fulltank_baseline_check?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&createDatabaseIfNotExist=true",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.datasource.username=root",
        "spring.datasource.password=12345678",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "spring.flyway.baseline-on-migrate=true",
        "spring.flyway.baseline-version=1",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
class MySqlMigrationPathTempTest {

    @Test
    void bootValidates() {
    }
}
