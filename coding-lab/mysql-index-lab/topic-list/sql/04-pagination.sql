USE topic_list_lab;
-- 当前保留idx_cover；状态20约16667条，取offset=10000有真实结果。
SELECT publish_time,id INTO @cursor_time,@cursor_id FROM meeting_topic
 WHERE status='20' ORDER BY publish_time DESC,id DESC LIMIT 9999,1;
SELECT @cursor_time,@cursor_id;
EXPLAIN ANALYZE SELECT id,subject,status,publish_time FROM meeting_topic
 WHERE status='20' ORDER BY publish_time DESC,id DESC LIMIT 10000,20;
EXPLAIN ANALYZE SELECT id,subject,status,publish_time FROM meeting_topic
 WHERE status='20' AND (publish_time<@cursor_time OR (publish_time=@cursor_time AND id<@cursor_id))
 ORDER BY publish_time DESC,id DESC LIMIT 20;
-- 验证两种方式同一位置得到相同的人员无关议题ID和顺序。
CREATE TEMPORARY TABLE offset_page AS SELECT id,publish_time FROM meeting_topic
 WHERE status='20' ORDER BY publish_time DESC,id DESC LIMIT 10000,20;
CREATE TEMPORARY TABLE cursor_page AS SELECT id,publish_time FROM meeting_topic
 WHERE status='20' AND (publish_time<@cursor_time OR (publish_time=@cursor_time AND id<@cursor_id))
 ORDER BY publish_time DESC,id DESC LIMIT 20;
SELECT COUNT(*) AS offset_count FROM offset_page;
SELECT COUNT(*) AS cursor_count FROM cursor_page;
SELECT COUNT(*) AS same_ids FROM offset_page a JOIN cursor_page b ON a.id=b.id;
SELECT 'offset' AS source,id,publish_time FROM offset_page ORDER BY publish_time DESC,id DESC;
SELECT 'cursor' AS source,id,publish_time FROM cursor_page ORDER BY publish_time DESC,id DESC;
-- 游标通常来自上一页响应；上面用OFFSET取游标仅为对照定位，不是线上实现。
