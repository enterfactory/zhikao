-- Exam lifecycle locks are the primary protocol; these constraints reject orphan writes.
ALTER TABLE exam_student ADD CONSTRAINT fk_attempt_exam FOREIGN KEY (exam_id) REFERENCES exam(id) ON DELETE RESTRICT;
ALTER TABLE exam_answer ADD CONSTRAINT fk_answer_attempt FOREIGN KEY (exam_student_id) REFERENCES exam_student(id) ON DELETE RESTRICT;
ALTER TABLE exam_answer ADD CONSTRAINT fk_answer_snapshot FOREIGN KEY (paper_question_id) REFERENCES paper_question(id) ON DELETE RESTRICT;
