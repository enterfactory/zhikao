-- Exact label migration preserves subject boundaries. Existing canonical IDs are preferred.
INSERT INTO knowledge_point(name,subject_id,sort)
 SELECT DISTINCT TRIM(t.label),q.subject_id,0 FROM question q
 CROSS JOIN LATERAL regexp_split_to_table(COALESCE(q.knowledge_point,''),'[,，;；|]') t(label)
 WHERE TRIM(t.label)<>'' AND NOT EXISTS(SELECT 1 FROM knowledge_point k WHERE k.subject_id=q.subject_id AND k.name=TRIM(t.label));
INSERT INTO question_knowledge(question_id,kp_id)
 SELECT q.id,MIN(k.id) FROM question q
 CROSS JOIN LATERAL regexp_split_to_table(COALESCE(q.knowledge_point,''),'[,，;；|]') t(label)
 JOIN knowledge_point k ON k.subject_id=q.subject_id AND k.name=TRIM(t.label)
 GROUP BY q.id,TRIM(t.label) ON CONFLICT DO NOTHING;
INSERT INTO knowledge_point(name,subject_id,sort)
 SELECT DISTINCT TRIM(t.label),p.subject_id,0 FROM paper_question pq JOIN paper p ON p.id=pq.paper_id
 CROSS JOIN LATERAL regexp_split_to_table(COALESCE(pq.knowledge_point,''),'[,，;；|]') t(label)
 WHERE TRIM(t.label)<>'' AND NOT EXISTS(SELECT 1 FROM knowledge_point k WHERE k.subject_id=p.subject_id AND k.name=TRIM(t.label));
INSERT INTO paper_question_knowledge(paper_question_id,kp_id,name,subject_id)
 SELECT pq.id,MIN(k.id),k.name,p.subject_id FROM paper_question pq JOIN paper p ON p.id=pq.paper_id
 CROSS JOIN LATERAL regexp_split_to_table(COALESCE(pq.knowledge_point,''),'[,，;；|]') t(label)
 JOIN knowledge_point k ON k.subject_id=p.subject_id AND k.name=TRIM(t.label)
 GROUP BY pq.id,k.name,p.subject_id ON CONFLICT DO NOTHING;

CREATE TABLE quality_annotation (
 id BIGSERIAL PRIMARY KEY, raw_id BIGINT NOT NULL UNIQUE REFERENCES crawl_raw(id),
 revision INTEGER NOT NULL DEFAULT 1, family VARCHAR(255) NOT NULL, split VARCHAR(20) NOT NULL,
 license_note TEXT NOT NULL, reference_text TEXT NOT NULL, questions_json TEXT NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', first_reviewer VARCHAR(100), second_reviewer VARCHAR(100),
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE crawl_question ADD COLUMN prediction_json TEXT;
ALTER TABLE crawl_raw ADD COLUMN extraction_millis BIGINT;
