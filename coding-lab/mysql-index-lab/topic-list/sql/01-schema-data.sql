CREATE DATABASE IF NOT EXISTS topic_list_lab CHARACTER SET utf8mb4;
USE topic_list_lab;
-- 只重置本Demo专用表。
DROP TABLE IF EXISTS meeting_topic;
CREATE TABLE meeting_topic (
 id BIGINT PRIMARY KEY,
 subject VARCHAR(100) NOT NULL,
 status CHAR(2) NOT NULL,
 publish_time DATETIME NOT NULL,
 create_time DATETIME NOT NULL,
 proposer_dept VARCHAR(40) NOT NULL
) ENGINE=InnoDB;
DROP TABLE IF EXISTS demo_digits;
CREATE TABLE demo_digits(n INT PRIMARY KEY);
INSERT INTO demo_digits VALUES(0),(1),(2),(3),(4),(5),(6),(7),(8),(9);
INSERT INTO meeting_topic
SELECT n, CONCAT('练习议题-',n),
 CASE MOD(n,3) WHEN 0 THEN '10' WHEN 1 THEN '20' ELSE '30' END,
 TIMESTAMPADD(MINUTE, MOD(n*7919,1051200), '2024-01-01 00:00:00'),
 '2024-01-01 00:00:00', CONCAT('部门-',MOD(n,20))
FROM (SELECT 1+a.n+10*b.n+100*c.n+1000*d.n+10000*e.n AS n
 FROM demo_digits a CROSS JOIN demo_digits b CROSS JOIN demo_digits c CROSS JOIN demo_digits d CROSS JOIN demo_digits e) x
WHERE n<=50000;
DROP TABLE demo_digits;
ANALYZE TABLE meeting_topic;
SELECT COUNT(*) AS total FROM meeting_topic;
SELECT status,COUNT(*) FROM meeting_topic GROUP BY status;
