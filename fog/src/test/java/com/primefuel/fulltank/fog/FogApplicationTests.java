package com.primefuel.fulltank.fog;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "fog.mqtt.enabled=false")
class FogApplicationTests {

    @Test
    void contextLoads() {
    }
}
