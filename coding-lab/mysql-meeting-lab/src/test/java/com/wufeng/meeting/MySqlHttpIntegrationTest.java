package com.wufeng.meeting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** 显式开启，重建meeting_lab四张练习表。不在普通mvn test里自动删表。 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("answer")
@EnabledIfEnvironmentVariable(named="MEETING_MYSQL_TEST",matches="true")
class MySqlHttpIntegrationTest {
    @Autowired TestRestTemplate http;
    Map post(String path) {
        var r=http.postForEntity(path,null,Map.class);assertEquals(200,r.getStatusCode().value(),String.valueOf(r.getBody()));return r.getBody();
    }
    Map get(String path) {var r=http.getForEntity(path,Map.class);assertEquals(200,r.getStatusCode().value());return r.getBody();}
    @Test void completeFiveStagesThroughRealHttpAndMySql() {
        assertEquals("ReferenceSteps",get("/api/lab").get("implementation"));
        assertEquals(500240,((Number)post("/api/lab/init").get("relations")).intValue());
        var before=get("/api/meetings/1/attendees");assertEquals(9,before.get("sqlCount"));assertEquals(300,before.get("peopleCount"));
        var plan=(List<Map>)get("/api/lab/explain").get("single");assertEquals("ALL",plan.get(0).get("type"));
        post("/api/lab/index");plan=(List<Map>)get("/api/lab/explain").get("single");assertEquals("ref",plan.get(0).get("type"));
        var after=get("/api/meetings/1/attendees?mode=batch");assertEquals(2,after.get("sqlCount"));assertEquals(before.get("people"),after.get("people"));
        var serial=post("/api/meetings/1/notifications/serial");var parallel=post("/api/meetings/1/notifications/parallel");
        assertEquals(300,serial.get("success"));assertEquals(300,parallel.get("success"));
        assertEquals(new HashSet<>((List)serial.get("results")),new HashSet<>((List)parallel.get("results")));
        var failure=post("/api/meetings/1/notifications/parallel?injectFailure=true&persist=true");
        assertEquals(283,failure.get("success"));assertEquals(17,failure.get("failed"));
        var retried=post("/api/meetings/1/retry");assertEquals(17,retried.get("success"));assertEquals(0,post("/api/meetings/1/retry").get("success"));
        var logs=http.getForObject("/api/meetings/1/logs",List.class);assertEquals(1,logs.size());assertEquals(300,((Map)logs.get(0)).get("total"));
        var saturation=post("/api/lab/saturation");assertEquals(18,saturation.get("rejected"));assertEquals(false,saturation.get("callerExecutedNotification"));
        var benchmark=http.postForEntity("/api/lab/log-benchmark",null,List.class);assertEquals(200,benchmark.getStatusCode().value());assertEquals(3,benchmark.getBody().size());
        var locks=post("/api/lab/locks");assertEquals(1,locks.get("deadlocks"));assertTrue(locks.get("diagnostic").toString().contains("push_log"));
    }
}
