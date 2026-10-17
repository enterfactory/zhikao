package com.zhikao.modules.crawler.ocr;

import java.nio.file.Path;

/**
 * OCR 引擎抽象（七期文档采集）。
 *
 * <p>刻意做得很薄：一个「图片进、文字出」的能力位，加上一个「现在能不能用」的自检。
 * 这样上层（PDF 扫描页、图片文档、将来的试卷切题）只依赖接口，
 * 换引擎、加引擎、云端与离线互备都不需要碰流水线代码。
 *
 * <p>{@link #available()} 必须<b>不抛异常</b>：启动自检、面板展示、失败回落都靠它，
 * 引擎自身的问题（缺原生库、没配 key）只让它返回 false，绝不让采集流程崩掉。
 */
public interface OcrEngine {

    /** 引擎标识（配置项 ocr.engine 取值、日志与前端展示都用它） */
    String name();

    /** 是否具备真实识别条件（离线库能加载 / 云端凭据已配置） */
    boolean available();

    /**
     * 识别一张图片，返回按阅读顺序拼接的纯文本及其平均置信度。
     *
     * @throws com.zhikao.common.BusinessException 识别失败（不可用、图片损坏、云端报错）
     */
    OcrText recognize(Path imagePath);

    /**
     * 带识别指令的识别：级联上层要「这一小块按公式来认（要 LaTeX）」而不是「按原样输出文字」。
     *
     * <p>默认忽略指令——传统 OCR 引擎是指令无关的，多模态通道才把这句话拼进请求里。
     * 这样上层统一按「能给指令就给」写，不必为每个引擎判断。
     */
    default OcrText recognize(Path imagePath, String instruction) {
        return recognize(imagePath);
    }
}
