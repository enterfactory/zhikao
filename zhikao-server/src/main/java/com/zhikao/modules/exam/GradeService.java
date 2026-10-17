package com.zhikao.modules.exam;

import com.zhikao.modules.exam.vo.ExamGradeVO;
import com.zhikao.modules.exam.vo.MyPaperVO;
import com.zhikao.modules.exam.vo.StudentGradeVO;

import java.util.List;

/**
 * 成绩服务。
 */
public interface GradeService {

    /** 教师视角：某场考试的统计概览 + 排名明细 */
    ExamGradeVO examGrade(Long examId);

    /** 教师视角：导出某场考试成绩为 Excel */
    byte[] exportExamGrade(Long examId);

    /** 学生视角：本人全部成绩列表 */
    List<StudentGradeVO> myGrades();

    List<StudentGradeVO> releasedGrades(Long studentId);

    /** 学生视角：本人某场考试的卷面明细（含标准答案与解析，仅限已交卷） */
    MyPaperVO myPaper(Long examId);
}
