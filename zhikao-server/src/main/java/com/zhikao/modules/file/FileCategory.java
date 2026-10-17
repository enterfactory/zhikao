package com.zhikao.modules.file;

import com.zhikao.common.BusinessException;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 文件分类：一处定义「允许哪些扩展名、多大、要不要做图片魔数校验」。
 *
 * <p>为什么要按分类而不是「一个全局白名单」：头像必须真的是图片（否则
 * 把 .exe 改名成 .png 就能上传并在浏览器里以 image/png 渲染），
 * 而 Excel 导入又必须排除脚本类扩展。两类文件的校验规则天然不同，
 * 混在一个白名单里只能取交集，等于两头都不严。
 */
public enum FileCategory {

    /** 头像：只收常见位图，且必须通过魔数校验 */
    AVATAR("avatar", "头像", true, 2 * 1024 * 1024,
            "jpg", "jpeg", "png", "webp", "gif"),

    /** 表格：批量导入用 */
    EXCEL("excel", "表格文件", false, 10 * 1024 * 1024,
            "xlsx", "xls", "csv"),

    /** 题目导入：只接受表格 */
    QUESTION("question", "题目文件", false, 10 * 1024 * 1024,
            "xlsx", "xls", "csv"),

    /** 试卷附件：表格 / PDF / 文档 */
    PAPER("paper", "试卷附件", false, 20 * 1024 * 1024,
            "xlsx", "xls", "pdf", "docx", "doc", "png", "jpg", "jpeg"),

    /** 系统导出产物（由后端生成，不走上传校验） */
    EXPORT("export", "导出文件", false, 20 * 1024 * 1024,
            "xlsx", "csv"),

    /**
     * 采集镜像图片（六期任务#19）：爬虫从外链/本地目录搬进来的题目配图。
     * imageOnly=false 是刻意的——这里不做「位图魔数」校验，因为 SVG 是文本形态的正规图片，
     * 内容真伪由调用方（ImageMirrorService）在下载环节按响应 Content-Type + 文件头判定。
     */
    CRAWLER("crawler", "采集图片", false, 5 * 1024 * 1024,
            "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg"),

    /** 兜底：仅允许明确安全的文档与图片 */
    OTHER("other", "其他文件", false, 10 * 1024 * 1024,
            "xlsx", "xls", "pdf", "docx", "png", "jpg", "jpeg", "txt");

    private final String code;
    private final String label;
    private final boolean imageOnly;
    private final long maxBytes;
    private final Set<String> allowedExt;

    FileCategory(String code, String label, boolean imageOnly, long maxBytes, String... allowedExt) {
        this.code = code;
        this.label = label;
        this.imageOnly = imageOnly;
        this.maxBytes = maxBytes;
        this.allowedExt = new LinkedHashSet<>(Arrays.asList(allowedExt));
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public boolean isImageOnly() {
        return imageOnly;
    }

    public long getMaxBytes() {
        return maxBytes;
    }

    public Set<String> getAllowedExt() {
        return allowedExt;
    }

    /** 允许的扩展名串（用于错误提示，让用户知道到底能传什么） */
    public String allowedExtText() {
        return String.join(" / ", allowedExt);
    }

    /**
     * 解析分类；无法识别时回退到 {@link #OTHER} 而不是报错。
     * 分类由前端传参，传错不该让上传直接失败——扩展名与大小校验仍然会兜住风险。
     */
    public static FileCategory of(String code) {
        if (code == null || code.isBlank()) {
            return OTHER;
        }
        for (FileCategory category : values()) {
            if (category.code.equalsIgnoreCase(code.trim())) {
                return category;
            }
        }
        throw new BusinessException("不支持的文件分类：" + code);
    }
}
