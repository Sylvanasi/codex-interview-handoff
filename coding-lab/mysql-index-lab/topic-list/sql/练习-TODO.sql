USE topic_list_lab;
-- 本文件只含题目，先自己写，再看标准答案与编号SQL。
-- TODO1：查状态20，时间倒序，同时间用id倒序，前20条。
-- TODO2：为TODO1建联合索引，记录EXPLAIN；subject是否还要回表？
-- TODO3：条件改为IN('20','30')，观察filesort并解释。
-- TODO4：设计时间优先索引，对比IN查询的扫描数量。
-- TODO5：为单状态查询设计覆盖索引。
-- TODO6：根据上一页最后一条publish_time和id，写下一页条件。
