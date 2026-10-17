package com.zhikao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 采集来源（六期建表，七期改造）：描述一道「从哪里取题」。
 *
 * <p>七期删掉了 HTTP 抓取路线（公开题库资源极少、且合规风险高），来源收敛为
 * <b>LOCAL_DIR 一种</b>——指向一个放满 docx/doc/pdf/图片/md/txt/json/csv 的目录。
 * 「文档上传」不是第二种来源类型：上传的文件统一落进摄取目录，摄取目录本身就是一个
 * LOCAL_DIR 源，因此入口虽多，落库路径只有一条。
 *
 * <p>license_note 强制留痕版权/许可说明，落实「只用免费公开/自有授权资源」。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("crawl_source")
public class CrawlSource {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 来源名称 */
    private String name;

    /** 目录绝对路径（必须在 zhikao.crawler.allowed-roots 之下） */
    private String baseUrl;

    /** 来源类型：七期起只有 LOCAL_DIR，字段保留是为了将来接「API 拉题」时无需改表 */
    private String sourceType;

    /** 版权 / 许可说明（合规留痕） */
    private String licenseNote;

    /** 历史字段：HTTP 时代的限频间隔，本地目录采集不触网，保留仅为兼容旧数据 */
    private Integer rateLimitMs;

    /** 默认归属科目 subject.id，打标匹配不上时兜底 */
    private Long defaultSubjectId;

    /** 1启用 0停用 */
    private Integer enabled;

    /** 备注 */
    private String remark;

    private LocalDateTime createTime;
}
