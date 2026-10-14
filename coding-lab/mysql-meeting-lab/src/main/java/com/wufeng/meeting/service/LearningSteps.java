package com.wufeng.meeting.service;
import com.wufeng.meeting.model.Models.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
/** 七个学习节点。默认PracticeSteps；answer profile切换ReferenceSteps。 */
public interface LearningSteps {
    void addTopicIndex();
    Audience loadBatch(List<Long> topics);
    ThreadPoolExecutor createPool(int core,int max,int capacity,AtomicInteger rejected);
    Outcome safeSend(long person,int attempt,boolean inject) throws InterruptedException;
    List<Outcome> parallel(Collection<Long> people,int attempt,boolean inject,ThreadPoolExecutor pool) throws InterruptedException;
    Set<Long> loadFailedPeople(long meeting);
    void writeBatch(List<Outcome> outcomes,long meeting);
}
