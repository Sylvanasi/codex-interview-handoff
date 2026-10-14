package com.wufeng.meeting;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("practice")
class PracticeStartupTest {
    @Autowired TestRestTemplate http;
    @Test void startsWithUnfinishedMethodsAndReturnsExplicitTodo() {
        var info=http.getForObject("/api/lab",Map.class);assertEquals("PracticeSteps",info.get("implementation"));
        var todo=http.postForEntity("/api/lab/index",null,Map.class);
        assertEquals(501,todo.getStatusCode().value());assertTrue(todo.getBody().get("message").toString().contains("TODO-1"));
        // 线程池的Lazy Bean创建失败也要返回学习提示，而不是启动失败。
        var pool=http.getForEntity("/api/lab/pool",Map.class);
        assertEquals(501,pool.getStatusCode().value());assertTrue(pool.getBody().get("message").toString().contains("TODO-3"));
    }
}
