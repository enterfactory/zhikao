-- File IDs remain business references, preserving every existing download URL.
CREATE TABLE file_blob (
 id BIGSERIAL PRIMARY KEY, sha256 VARCHAR(64) NOT NULL UNIQUE,
 access_path VARCHAR(500) NOT NULL, stored_name VARCHAR(255) NOT NULL,
 size_bytes BIGINT NOT NULL, content_type VARCHAR(150),
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, garbage_after TIMESTAMP
);
INSERT INTO file_blob(sha256,access_path,stored_name,size_bytes,content_type,created_at)
 SELECT sha256,access_path,stored_name,size_bytes,content_type,create_time FROM sys_file;
ALTER TABLE sys_file ADD COLUMN blob_id BIGINT REFERENCES file_blob(id);
UPDATE sys_file f SET blob_id=b.id FROM file_blob b WHERE b.sha256=f.sha256;
ALTER TABLE sys_file ALTER COLUMN blob_id SET NOT NULL;
DROP INDEX IF EXISTS uk_file_sha;
DROP INDEX IF EXISTS uk_file_access;
CREATE INDEX idx_file_blob_ref ON sys_file(blob_id);

ALTER TABLE crawl_raw ADD COLUMN content_hash VARCHAR(64);
ALTER TABLE crawl_raw ADD COLUMN parser_version VARCHAR(120);
ALTER TABLE crawl_raw ADD COLUMN source_name VARCHAR(255);
ALTER TABLE crawl_raw ADD COLUMN source_snapshot VARCHAR(1000);
ALTER TABLE crawl_raw ADD COLUMN page_count INTEGER;
ALTER TABLE crawl_question ADD COLUMN source_page INTEGER;
ALTER TABLE crawl_question ADD COLUMN source_anchor TEXT;
ALTER TABLE crawl_question ADD COLUMN source_bbox VARCHAR(255);
ALTER TABLE crawl_question ADD COLUMN locator_method VARCHAR(40);
ALTER TABLE crawl_question ADD COLUMN source_ordinal INTEGER;
ALTER TABLE crawl_question ADD COLUMN review_started_at TIMESTAMP;
ALTER TABLE crawl_question ADD COLUMN review_milliseconds BIGINT;

CREATE TABLE paper_question_knowledge (
 paper_question_id BIGINT NOT NULL REFERENCES paper_question(id) ON DELETE CASCADE,
 kp_id BIGINT NOT NULL, name VARCHAR(255) NOT NULL, subject_id BIGINT NOT NULL,
 PRIMARY KEY(paper_question_id,kp_id)
);
INSERT INTO paper_question_knowledge SELECT pq.id,k.id,k.name,q.subject_id
 FROM paper_question pq JOIN question q ON q.id=pq.question_id
 JOIN question_knowledge qk ON qk.question_id=q.id JOIN knowledge_point k ON k.id=qk.kp_id
 ON CONFLICT DO NOTHING;

ALTER TABLE exam ADD COLUMN roster_frozen INTEGER NOT NULL DEFAULT 0;
CREATE TABLE exam_roster (
 exam_id BIGINT NOT NULL REFERENCES exam(id) ON DELETE CASCADE,
 student_id BIGINT NOT NULL, class_id BIGINT, student_name VARCHAR(100), class_name VARCHAR(100),
 captured_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(exam_id,student_id)
);
-- Existing attempts are historical facts. Never fabricate non-participants for ended exams.
INSERT INTO exam_roster(exam_id,student_id,class_id,student_name,class_name)
 SELECT es.exam_id,es.student_id,e.class_id,u.real_name,c.class_name
 FROM exam_student es JOIN exam e ON e.id=es.exam_id JOIN sys_user u ON u.id=es.student_id
 LEFT JOIN sys_class c ON c.id=e.class_id ON CONFLICT DO NOTHING;
INSERT INTO exam_roster(exam_id,student_id,class_id,student_name,class_name)
 SELECT e.id,u.id,u.class_id,u.real_name,c.class_name FROM exam e
 JOIN sys_user u ON u.class_id=e.class_id AND u.user_type=3 AND u.status=1
 LEFT JOIN sys_class c ON c.id=u.class_id WHERE e.published=1 AND e.end_time>CURRENT_TIMESTAMP
 ON CONFLICT DO NOTHING;
UPDATE exam SET roster_frozen=1 WHERE published=1 AND end_time>CURRENT_TIMESTAMP;

CREATE TABLE notification_outbox (
 id BIGSERIAL PRIMARY KEY, event_key VARCHAR(64) NOT NULL UNIQUE,
 user_ids TEXT NOT NULL, type VARCHAR(40) NOT NULL, title VARCHAR(255) NOT NULL,
 content TEXT, biz_type VARCHAR(80), biz_id BIGINT,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 delivered_at TIMESTAMP, attempts INTEGER NOT NULL DEFAULT 0,
 next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, last_error VARCHAR(255)
);
ALTER TABLE sys_notification ADD COLUMN event_key VARCHAR(64);
CREATE UNIQUE INDEX uk_notification_event_recipient ON sys_notification(event_key,user_id);
CREATE INDEX idx_outbox_pending ON notification_outbox(next_attempt_at,id) WHERE delivered_at IS NULL;

CREATE TABLE quality_evaluation_run (
 id BIGSERIAL PRIMARY KEY, name VARCHAR(255) NOT NULL,
 dataset_kind VARCHAR(40) NOT NULL, status VARCHAR(40) NOT NULL,
 summary_json TEXT NOT NULL, created_by VARCHAR(100) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
