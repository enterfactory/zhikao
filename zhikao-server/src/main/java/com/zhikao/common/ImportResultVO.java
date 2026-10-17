package com.zhikao.common;

import java.util.ArrayList;
import java.util.List;

/**
 * Excel 批量导入结果（用户导入、题目导入等共用）。
 */
public class ImportResultVO {

    /** 失败明细最多保留的条数，避免大量错误时响应体过大 */
    private static final int MAX_ERRORS = 20;

    /** 预校验时最多回显的条目数 */
    private static final int MAX_PREVIEWS = 30;

    /** 成功导入条数 */
    private int successCount;

    /** 失败条数 */
    private int failCount;

    /** 失败明细（形如「第 3 行：用户名已存在」） */
    private List<String> errors = new ArrayList<>();

    /** true 表示本次只做了校验、没有落库（前端用「预校验」按钮触发） */
    private boolean preview;

    /** 预校验通过、将被导入的条目摘要（每行一条，便于用户确认后再提交） */
    private List<String> previewRows = new ArrayList<>();

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailCount() {
        return failCount;
    }

    public void setFailCount(int failCount) {
        this.failCount = failCount;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    public boolean isPreview() {
        return preview;
    }

    public void setPreview(boolean preview) {
        this.preview = preview;
    }

    public List<String> getPreviewRows() {
        return previewRows;
    }

    public void setPreviewRows(List<String> previewRows) {
        this.previewRows = previewRows;
    }

    /** 成功数 +1 */
    public void addSuccess() {
        this.successCount++;
    }

    /** 失败数 +1 并记录原因（超出上限后不再追加） */
    public void addError(String message) {
        this.failCount++;
        if (errors.size() < MAX_ERRORS) {
            errors.add(message);
        }
    }

    /** 记录一条「预校验通过」的条目摘要 */
    public void addPreview(String summary) {
        if (previewRows.size() < MAX_PREVIEWS) {
            previewRows.add(summary);
        }
    }
}
