package com.zhikao.modules.exam;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhikao.common.BusinessException;
import com.zhikao.common.DataScope;
import com.zhikao.modules.notification.NotificationTypeEnum;
import com.zhikao.common.OperLog;
import com.zhikao.common.R;
import com.zhikao.entity.Exam;
import com.zhikao.entity.ExamStudent;
import com.zhikao.mapper.ExamMapper;
import com.zhikao.mapper.ExamStudentMapper;
import com.zhikao.modules.notification.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/v2/exams")
@RequiredArgsConstructor
public class ExamReleaseController {
    private final ExamReleaseService releases;

    public record ReleaseRequest(@NotNull @Min(1) @Max(2) Integer level) {}

    @PostMapping("/{id}/releases")
    @SaCheckPermission("exam:manage")
    @OperLog(module = "考试", action = "发布成绩与解析")
    public R<Void> release(@PathVariable Long id, @Valid @RequestBody ReleaseRequest body) {
        releases.release(id,body.level());
        return R.ok();
    }
}
