package com.primefuel.fulltank.edge;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "edge.mqtt.enabled=false")
class EdgeApplicationTests {

    @Test
    void contextLoads() {
    }
}
