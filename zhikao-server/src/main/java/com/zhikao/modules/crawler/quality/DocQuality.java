package com.zhikao.modules.crawler.quality;

/**
 * 一份文档的自动质量结论（七期 P1）。
 *
 * @param score  0~100 综合分，可比、可排序、可作为「要不要升级识别通道」的判据
 * @param detail 分数构成（人可读），直接显示在解析日志里，避免只剩一个孤零零的数字
 */
public record DocQuality(int score, String detail) {

    /** 一道题都没解析出来：分数没有意义，固定 0，原因由解析说明给出 */
    public static DocQuality unparsed() {
        return new DocQuality(0, "未解析出题目（原因见「解析说明」）");
    }
}
