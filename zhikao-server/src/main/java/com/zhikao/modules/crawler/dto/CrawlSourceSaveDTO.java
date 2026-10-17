package com.zhikao.modules.crawler.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 采集来源新增/修改参数。id 为空表示新增，不为空表示修改。
 */
@Data
public class CrawlSourceSaveDTO {

    private Long id;

    @NotBlank(message = "来源名称不能为空")
    private String name;

    /** 文档目录绝对路径（必须在 zhikao.crawler.allowed-roots 之下） */
    @NotBlank(message = "来源地址不能为空")
    private String baseUrl;

    /** 来源类型：七期起只接受 LOCAL_DIR */
    @NotBlank(message = "来源类型不能为空")
    private String sourceType;

    /** 版权/许可说明 */
    private String licenseNote;

    /** 历史字段（HTTP 限频），本地目录采集用不到，保留以兼容旧调用方 */
    private Integer rateLimitMs;

    /** 默认科目 id */
    private Long defaultSubjectId;

    /** 1启用 0停用 */
    private Integer enabled;

    private String remark;
}
