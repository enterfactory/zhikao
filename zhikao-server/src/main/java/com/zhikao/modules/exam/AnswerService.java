package com.zhikao.modules.exam;

import com.zhikao.modules.exam.dto.AnswerSaveDTO;
import com.zhikao.modules.exam.dto.CheatReportDTO;
import com.zhikao.modules.exam.vo.CheatLogVO;
import com.zhikao.modules.exam.vo.CheatReportVO;
import com.zhikao.modules.exam.vo.EnterExamVO;
import com.zhikao.modules.exam.vo.MarkItemVO;
import com.zhikao.modules.exam.vo.MarkLogVO;
import com.zhikao.modules.exam.vo.SubmitResultVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 答题与阅卷服务（学生侧答题 + 教师侧批改）。
 */
public interface AnswerService {

    /** 进入考试：校验资格、惰性创建参与记录、返回不带答案的答题卷与剩余时间 */
    EnterExamVO enter(Long examId);

    /** 保存单题作答，返回剩余答题秒数（前端据此校准倒计时） */
    Long saveAnswer(Long examId, AnswerSaveDTO dto);

    /**
     * 上报一次切屏等异常：落证据链、累计次数，并按阈值给出处置结果。
     *
     * @param dto 可为空（老客户端只传路径参数），为空时按 blur 处理
     */
    CheatReportVO reportCheat(Long examId, CheatReportDTO dto);

    /** 交卷：幂等推进状态并自动判定客观题；已过截止时间时按自动收卷处理 */
    SubmitResultVO submit(Long examId);

    /** 教师批改用：某场考试已交卷学生的主观题作答列表 */
    List<MarkItemVO> subjectiveList(Long examId);

    /**
     * 教师批改：给某条主观题作答打分，并自动重算该学生总分与状态。
     *
     * @param reason 改分理由；首次批改可空，改分（该题已批改过）必填
     */
    void mark(Long answerId, BigDecimal score, String reason);

    /** 某场考试的批改留痕（谁在什么时候把哪道题从多少分改成了多少分） */
    List<MarkLogVO> markLogs(Long examId);

    /** 某场考试的作弊证据链（谁、第几次、什么时候、在第几题） */
    List<CheatLogVO> cheatLogs(Long examId);

    /**
     * 定时收卷：把「已过答题截止时间却仍在考试中」的记录自动收卷。
     *
     * @param limit 单次扫描上限
     * @return 本次实际收卷的份数
     */
    int autoSubmitExpired(int limit);
}
