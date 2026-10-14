USE topic_list_lab;
SELECT VERSION();
SHOW INDEX FROM meeting_topic;
EXPLAIN SELECT id,subject,status,publish_time FROM meeting_topic
 WHERE status='20' ORDER BY publish_time DESC,id DESC LIMIT 20;
EXPLAIN ANALYZE SELECT id,subject,status,publish_time FROM meeting_topic
 WHERE status='20' ORDER BY publish_time DESC,id DESC LIMIT 20;
