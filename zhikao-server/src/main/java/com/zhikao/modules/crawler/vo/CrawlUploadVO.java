package com.zhikao.modules.crawler.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档上传结果（七期）。
 *
 * <p>把「收下的」和「拒掉的」分开回传：批量上传最常见的情况是<b>部分文件不该进采集</b>
 * （格式不支持、体积超限），全部成功或全部失败两种口径都会让人猜下一步该干什么。
 */
@Data
public class CrawlUploadVO {

    /** 已落盘并进入采集队列的文件数 */
    private Integer stored;

    /** 被拒开的文件名与原因（前端逐条展示） */
    private List<String> rejected = new ArrayList<>();

    /** 采集来源 id（上传目录对应的 LOCAL_DIR 源，复用同一个） */
    private Long sourceId;

    /** 采集任务 id：OCR 是十秒级的，前端据此轮询任务进度 */
    private Long taskId;

    /** 摄取目录（绝对路径），便于排查「文件到底落在哪」 */
    private String ingestDir;
}
