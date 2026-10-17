package com.zhikao.modules.ai.agent;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhikao.entity.StudentProfile;
import com.zhikao.mapper.StudentProfileMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 学生画像服务 —— 长期记忆的**应用层**实现。
 *
 * <b>为什么不用框架的 LongTermMemory</b>：反编译 agentscope-core 2.0.3 确认，
 * {@code io.agentscope.core.memory.LongTermMemory} 与 {@code rag.Knowledge}
 * 均标注 {@code @Deprecated(forRemoval = true, since = "2.0.0")}，
 * 官方注释明确写着「long-term memory is removed; integrate cross-session persistence
 * at the application layer」。也就是说这两个构建器方法仍在，但已是空壳。
 *
 * 所以这里按官方指引在应用层落地，反而更好：
 * <ul>
 *   <li>记忆内容进 DB，可查、可改、可审计，不依赖任何外部记忆服务；</li>
 *   <li>记忆边界清晰——只存「归纳结论」（薄弱知识点、偏好），
 *       成绩这类实时数据一律走工具现查，不会出现「AI 拿旧分数回答」。</li>
 * </ul>
 */
@Service
public class StudentProfileService {

    /** 画像摘要注入提示词时的长度上限，防止把上下文撑爆 */
    private static final int SUMMARY_MAX_LENGTH = 600;

    private final StudentProfileMapper profileMapper;

    public StudentProfileService(StudentProfileMapper profileMapper) {
        this.profileMapper = profileMapper;
    }

    /**
     * 读取学生画像。
     *
     * @return 画像文本；无记录时返回空串（调用方据此决定是否注入提示词）
     */
    public String loadSummary(Long studentId) {
        if (studentId == null) {
            return "";
        }
        StudentProfile profile = profileMapper.selectOne(Wrappers.<StudentProfile>lambdaQuery()
                .eq(StudentProfile::getStudentId, studentId)
                .last("limit 1"));
        if (profile == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        if (profile.getWeakPoints() != null && !profile.getWeakPoints().isBlank()) {
            builder.append("历史薄弱知识点：").append(profile.getWeakPoints()).append("；");
        }
        if (profile.getPreferences() != null && !profile.getPreferences().isBlank()) {
            builder.append("学习偏好：").append(profile.getPreferences()).append("；");
        }
        if (profile.getSummary() != null && !profile.getSummary().isBlank()) {
            builder.append(profile.getSummary());
        }
        String text = builder.toString();
        return text.length() <= SUMMARY_MAX_LENGTH ? text : text.substring(0, SUMMARY_MAX_LENGTH) + "...";
    }

    /**
     * 更新学生画像（按 student_id 做 upsert）。
     *
     * @param weakPoints 薄弱知识点（顿号分隔）
     */
    public void saveWeakPoints(Long studentId, List<String> weakPoints) {
        if (studentId == null || weakPoints == null) {
            return;
        }
        String joined = String.join("、", weakPoints);
        StudentProfile exists = profileMapper.selectOne(Wrappers.<StudentProfile>lambdaQuery()
                .eq(StudentProfile::getStudentId, studentId)
                .last("limit 1"));
        if (exists == null) {
            profileMapper.insert(StudentProfile.builder()
                    .studentId(studentId)
                    .weakPoints(joined)
                    .updateTime(LocalDateTime.now())
                    .build());
            return;
        }
        exists.setWeakPoints(joined);
        exists.setUpdateTime(LocalDateTime.now());
        profileMapper.updateById(exists);
    }
}
