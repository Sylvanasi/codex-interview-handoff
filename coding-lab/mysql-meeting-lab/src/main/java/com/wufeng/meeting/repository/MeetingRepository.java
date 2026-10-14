package com.wufeng.meeting.repository;

import com.wufeng.meeting.model.Models.Audience;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.*;

@Repository
public class MeetingRepository {
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public MeetingRepository(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    public Map<String, Object> init() {
        ResourceDatabasePopulator resourceDatabasePopulator = new ResourceDatabasePopulator(new ClassPathResource("sql/01-init.sql"));
        resourceDatabasePopulator.execute(dataSource);
        return Map.of("topics", jdbc.queryForObject("SELECT COUNT(*) FROM meeting_topic", Long.class),
                "relations", jdbc.queryForObject("SELECT COUNT(*) FROM topic_attendee", Long.class),
                "target", jdbc.queryForMap("SELECT COUNT(*) AS relations,COUNT(DISTINCT person_id) AS people FROM topic_attendee WHERE topic_id<=8"));
    }

    public List<Long> topics(long meeting) {
        return jdbc.queryForList("SELECT id FROM meeting_topic WHERE meeting_id=? ORDER BY id", Long.class, meeting);
    }

    public Audience serial(List<Long> topics) {
        Set<Long> people = new TreeSet<>();
        for (long topic : topics)
            people.addAll(jdbc.queryForList("SELECT person_id FROM topic_attendee WHERE topic_id=?", Long.class, topic));
        return new Audience(people, topics.size());
    }

    public Map<String, Object> explain() {
        return Map.of("single", jdbc.queryForList("EXPLAIN SELECT person_id FROM topic_attendee WHERE topic_id=1"),
                "batch", jdbc.queryForList("EXPLAIN SELECT person_id FROM topic_attendee WHERE topic_id IN(1,2,3,4,5,6,7,8)"));
    }

    public List<Map<String, Object>> logs(long meeting) {
        return jdbc.queryForList("SELECT status,COUNT(*) AS total FROM push_log WHERE meeting_id=? GROUP BY status", meeting);
    }
}
