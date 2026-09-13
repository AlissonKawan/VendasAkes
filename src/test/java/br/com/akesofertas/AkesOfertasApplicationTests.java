package br.com.akesofertas;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"telegram.bot-token=test-token", "telegram.chat-id=@test"})
class AkesOfertasApplicationTests {
    @Test
    void contextLoads() {}
}
