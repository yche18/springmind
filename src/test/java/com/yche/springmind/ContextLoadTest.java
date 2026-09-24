package com.yche.springmind;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.yche.springmind.ingestion.service.DocumentIngestionProcessor;

@SpringBootTest
class ContextLoadTest {

    @MockBean
    private DocumentIngestionProcessor documentIngestionProcessor;

    @Test
    void contextLoads() {
    }
}
