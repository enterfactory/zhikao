package com.zhikao.modules.exam;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.zhikao.common.OperLog;
import com.zhikao.common.R;
import com.zhikao.common.RateLimit;
import com.zhikao.modules.exam.dto.AnswerSaveDTO;
import com.zhikao.modules.exam.dto.CheatReportDTO;
import com.zhikao.modules.exam.vo.CheatLogVO;
import com.zhikao.modules.exam.vo.CheatReportVO;
import com.zhikao.modules.exam.vo.EnterExamVO;
import com.zhikao.modules.exam.vo.MarkItemVO;
import com.zhikao.modules.exam.vo.MarkLogVO;
import com.zhikao.modules.exam.vo.SubmitResultVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 答题与阅卷接口。
 * 学生侧：进入考试、保存作答、上报切屏、交卷；教师侧：查看待批改、打分。
 */
@RestController
@RequestMapping("/exam")
public class AnswerController {

    /** 单次超时扫描的收卷上限，与定时任务保持一致 */
    private static final int TIMEOUT_SCAN_LIMIT = 200;

    private final AnswerService answerService;

    public AnswerController(AnswerService answerService) {
        this.answerService = answerService;
    }

    // ==================== 学生侧 ====================

    /** 进入考试：返回不带标准答案的答题卷与剩余时间 */
    @PostMapping("/{id}/enter")
    @SaCheckRole("student")
    public R<EnterExamVO> enter(@PathVariable Long id) {
        return R.ok(answerService.enter(id));
    }

    /** 保存单题作答，返回剩余答题秒数 */
    @PutMapping("/{id}/answer")
    @SaCheckRole("student")
    public R<Long> saveAnswer(@PathVariable Long id, @Valid @RequestBody AnswerSaveDTO dto) {
        return R.ok(answerService.saveAnswer(id, dto));
    }

    /**
     * 上报一次切屏等异常（防作弊开启时累计）。
     * 请求体可选：不传也能用（按「失焦」记录），带上可以补充「第几题、什么类型」。
     */
    @PostMapping("/{id}/cheat")
    @SaCheckRole("student")
    public R<CheatReportVO> reportCheat(@PathVariable Long id,
                                        @RequestBody(required = false) CheatReportDTO dto) {
        return R.ok(answerService.reportCheat(id, dto));
    }

    /** 交卷并自动判分（幂等，重复提交会被拒绝） */
    @PostMapping("/{id}/submit")
    @SaCheckRole("student")
    @RateLimit(key = "submit", capacity = 5, refillPerSecond = 0.5,
            message = "交卷请求过于频繁，请稍后确认")
    @OperLog(module = "在线答题", action = "交卷")
    public R<SubmitResultVO> submit(@PathVariable Long id) {
        return R.ok(answerService.submit(id));
    }

    // ==================== 教师侧 ====================

    /** 某场考试的主观题作答列表（含已批改，便于回看与调分） */
    @GetMapping("/{id}/subjective")
    @SaCheckPermission("marking:do")
    public R<List<MarkItemVO>> subjective(@PathVariable Long id) {
        return R.ok(answerService.subjectiveList(id));
    }

    /**
     * 主观题打分。
     *
     * reason 在「改分」时必填：批改一定会出错，错了要能改，但改了必须留下理由，
     * 否则成绩争议时无法回溯「为什么从 5 分变成 8 分」。
     */
    @PutMapping("/answer/{answerId}/mark")
    @SaCheckPermission("marking:do")
    @OperLog(module = "主观题阅卷", action = "批改打分")
    public R<Void> mark(@PathVariable Long answerId, @RequestParam BigDecimal score,
                        @RequestParam(required = false) String reason) {
        answerService.mark(answerId, score, reason);
        return R.ok();
    }

    /** 某场考试的批改留痕（谁在什么时候把哪道题从多少分改成了多少分） */
    @GetMapping("/{id}/mark-logs")
    @SaCheckPermission("marking:do")
    public R<List<MarkLogVO>> markLogs(@PathVariable Long id) {
        return R.ok(answerService.markLogs(id));
    }

    /** 某场考试的作弊证据链（谁、第几次、什么时候、在第几题） */
    @GetMapping("/{id}/cheat-logs")
    @SaCheckPermission("exam:manage")
    public R<List<CheatLogVO>> cheatLogs(@PathVariable Long id) {
        return R.ok(answerService.cheatLogs(id));
    }

    /**
     * 手动触发一次超时收卷（定时任务之外的兜底入口）。
     * 演示与排查时很有用：不用等到下一个调度周期才知道「自动收卷到底好不好使」。
     */
    @PostMapping("/tasks/auto-submit")
    @SaCheckPermission("exam:manage")
    @OperLog(module = "在线答题", action = "手动触发超时收卷")
    public R<Integer> autoSubmit() {
        return R.ok(answerService.autoSubmitExpired(TIMEOUT_SCAN_LIMIT));
    }
}
