package com.zhikao.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 题库文档采集配置（zhikao.crawler.*，七期由「网页爬虫」转向「文档摄取」后重写）。
 *
 * <p>两条硬闸门仍在配置里：
 * <ol>
 *   <li>{@code allowedRoots} 本地目录白名单——LOCAL_DIR 采集、文档上传落盘、
 *       以及富文本配图镜像读取本地文件时，都只允许访问这些根之下的路径；</li>
 *   <li>{@code ingestDir} 上传摄取目录——前端上传的 docx/pdf/图片统一落在这里，
 *       它由 {@code LocalPathGuard} 自动并入白名单，因此「上传」与「本地目录」走同一条采集链路。</li>
 * </ol>
 *
 * <p>OCR 双通道（{@code ocr.*}）：离线 RapidOCR 为默认，云端 Qwen-VL 需自行配 key 才可用；
 * 两者都不可用时，采集只做「文本层抽取」，图片类文档明确失败而非静默丢题。
 * 在双通道之上还有一层「片段级级联」（{@code ocr.formula.*}）：文本层完整的 PDF 里，
 * 公式这类内联图片是取不到字的，只把那一小块重认回来插回原位，不动已有文本。
 */
@Data
@Component
@ConfigurationProperties(prefix = "zhikao.crawler")
public class CrawlerProperties {

    /**
     * 本地目录白名单根：只允许采集位于这些根目录之下的路径。
     * 配置里给的是相对项目根（zhikao-server）或绝对路径，运行时统一规范化为绝对路径再比对。
     */
    private List<String> allowedRoots = new ArrayList<>();

    /**
     * 文档上传摄取目录（相对路径以 user.dir 为基准）。
     * 上传的文件按「日期/时间戳-原名」落盘，天然形成可追溯的采集现场；该目录自动并入白名单。
     */
    private String ingestDir = "./data/crawl-ingest";

    /** 外链配图镜像 UA（固定标识，绝不伪装浏览器） */
    private String userAgent = "ZhikaoBot/1.0 (exam-bank research)";

    /** 图片镜像下载超时（毫秒）：宁可慢也别挂死流水线 */
    private int imageTimeoutMs = 10000;

    /** 单张镜像图片的大小上限（字节，默认 5MB）：超过即判定失败，保留原 URL 并打标记 */
    private int imageMaxBytes = 5 * 1024 * 1024;

    /** Only controlled hosts may supply remote images; empty means local images only. */
    private List<String> imageAllowedHosts = new ArrayList<>();

    /** 单个采集文档的体积上限（字节，默认 50MB）：超限直接拒收，不进入解析 */
    private long maxDocBytes = 50L * 1024 * 1024;

    /** OCR 与 PDF 相关配置 */
    private Ocr ocr = new Ocr();

    /** OCR 通道配置 */
    @Data
    public static class Ocr {

        /** 引擎选择：auto（优先离线，不可用再云端）| rapid（仅离线）| qwen（仅云端） */
        private String engine = "auto";

        /** 单个文档最多处理的页数/图片数（防超大扫描件把一次采集拖死） */
        private int maxPages = 60;

        /** 扫描版 PDF 渲染成图时的分辨率（DPI）：200 是识别精度与速度的常用折中 */
        private int pdfDpi = 200;

        /**
         * 判定「扫描版」的阈值：PDF 文本层平均每页字符数低于此值即认为没有可用文本层，
         * 转而渲染页面做 OCR。经验值 20——够挡掉水印/页眉级噪声，又不会误伤真·纯文本 PDF。
         */
        private int pdfMinCharsPerPage = 20;

        /** 云端 Qwen-VL-OCR 通道（OpenAI 兼容对话接口） */
        private Qwen qwen = new Qwen();

        /** 公式片段回插（文本层为底，只重认丢字的小图） */
        private Formula formula = new Formula();
    }

    /**
     * 公式片段回插配置（{@code zhikao.crawler.ocr.formula.*}）。
     *
     * <p>背景：试卷 PDF 的数学公式多是<b>内联图片对象</b>，PDF 文本层根本没有对应字符，
     * 于是「题干完整、条件里的式子凭空消失」。这类文档平均每页字符数是达标的（正文是真文本层），
     * 走整页 OCR 反而会把精确的文本层换成概率结果——所以这里的做法是
     * <b>文本层原样保留，只把丢掉的那一小块认回来插回原位</b>。
     *
     * <p>识别仍是级联：先本地（免费、快），置信度不够再升级云端（要 LaTeX，且有成本闸）。
     */
    @Data
    public static class Formula {

        private boolean enabled = true;

        /** 放置高度上限（pt）：公式行内小图一般 10~40pt；超过就按题图/插图处理，不当公式 */
        private double maxHeight = 48;

        /** 放置宽度上限（pt）：再宽就是图表或整题配图 */
        private double maxWidth = 160;

        /** 放置尺寸下限（pt）：装饰点、下划线一类的小图形不值得送去 OCR */
        private double minHeight = 4;

        private double minWidth = 4;

        /** 单份文档最多处理的片段数：一次采集别被上百张小图拖住 */
        private int maxPerDocument = 200;

        /**
         * 单份文档最多调用<b>云端</b>的次数（成本闸）。
         * 本地兜不住的才升级，升级要花钱，宁可留几处没补上，也不能让一次上传产生不可控账单。
         */
        private int maxCloudPerDocument = 30;

        /**
         * 本地识别结果的可信下限：低于它就升级云端。
         * 公式里全是符号与上下标，本地通用文本模型给到 0.9 以上才基本可信，
         * 0.85 是「明显在猜」与「大致认对」的分界。
         */
        private double escalateBelow = 0.85;

        /** 片段送 OCR 前的放大倍数（像素高不足 64 时还会自动再加，上限 8 倍） */
        private int upscale = 2;

        /** 识别文本长度上限：超过多半是模型在解释题目，不是公式本身 */
        private int maxOutputChars = 160;

        /** 片段识别指令：要 LaTeX 源码，不要解说，不要自带 $ 包裹 */
        private String prompt = "识别图片中的数学公式，只输出与之对应的 LaTeX 源码："
                + "不要解释、不要翻译、不要输出中文句子，"
                + "不要包裹 $、$$ 或代码块，上下标用 ^ 与 _，分式用 \\frac{}{}。";
    }

    /** 通义 Qwen-VL OCR 通道配置；未配 apiKey 时该通道视为不可用 */
    @Data
    public static class Qwen {

        private boolean enabled = false;

        private String apiKey = "";

        /** OpenAI 兼容服务地址（阿里云百炼默认） */
        private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

        private String model = "qwen-vl-ocr";

        /** 单次调用超时（秒）：整页识别比对话慢，默认给足 60s */
        private int timeoutSeconds = 60;

        /** 识别指令：要求「只按原样输出文字」，避免模型改写成摘要 */
        private String prompt = "请识别图片中的全部文字内容，按原有行序原样输出，"
                + "保留题号、选项（A/B/C/D）、答案与解析等标记，不要做任何总结、翻译或补全。";
    }
}
