package com.example.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class BackendApplicationTests {

    @MockBean
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads() {
    }

}
