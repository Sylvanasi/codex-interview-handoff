package com.wufeng.meeting.service;
import com.wufeng.meeting.model.Models.Outcome;
import org.springframework.stereotype.Component;
@Component
public class MockNotificationClient {
    public Outcome send(long person,int attempt,boolean inject) throws InterruptedException {
        Thread.sleep(20); // 模拟一次OA/短信操作；没有真实网络发送
        if(inject && attempt==1 && person%17==0) throw new IllegalStateException("模拟上游失败");
        return new Outcome(person,"SUCCESS",attempt);
    }
}
