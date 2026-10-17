package com.zhikao.modules.crawler.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhikao.common.BusinessException;
import com.zhikao.config.CrawlerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;

/**
 * 云端 OCR 通道：通义 Qwen-VL-OCR（OpenAI 兼容对话接口）。
 *
 * <p>定位是<b>离线通道的补充</b>而不是替代：手写体、模糊翻拍、复杂排版（表格混题号）
 * 这类传统 OCR 明显吃力的图，交给多模态大模型识别效果更好。
 * 未配置 {@code zhikao.crawler.ocr.qwen.api-key} 时它直接判定不可用——
 * 与项目里 AI 模块同一口径：<b>没有凭据就不假装能用</b>。
 *
 * <p>请求体走标准 OpenAI 多模态形态（{@code image_url} + {@code text}），
 * 因此只要baseUrl 换成任何 OpenAI 兼容的视觉服务都能用，不锁死单一厂商。
 * 图片以 data URL 内联（base64），避免「云端要求图片可公网访问」这道本地文件根本满足不了的门槛。
 */
@Component
public class QwenVlOcrEngine implements OcrEngine {

    private static final Logger log = LoggerFactory.getLogger(QwenVlOcrEngine.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CrawlerProperties properties;
    private final HttpClient httpClient;

    public QwenVlOcrEngine(CrawlerProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(5, properties.getOcr().getQwen().getTimeoutSeconds())))
                .build();
    }

    @Override
    public String name() {
        return "qwen-vl";
    }

    @Override
    public boolean available() {
        CrawlerProperties.Qwen qwen = properties.getOcr().getQwen();
        return qwen.isEnabled() && notBlank(qwen.getApiKey()) && notBlank(qwen.getBaseUrl());
    }

    @Override
    public OcrText recognize(Path imagePath) {
        return recognize(imagePath, null);
    }

    /**
     * {@code instruction} 非空时用它替换默认的整体识别指令。
     * 公式片段走的是同一套接口，只是要的东西完全不同：整页要「原样输出文字」，
     * 一小块式子要「输出 LaTeX 源码」。
     */
    @Override
    public OcrText recognize(Path imagePath, String instruction) {
        CrawlerProperties.Qwen qwen = properties.getOcr().getQwen();
        if (!available()) {
            throw new BusinessException("云端 OCR 未配置（zhikao.crawler.ocr.qwen.enabled / api-key）");
        }
        try {
            byte[] bytes = Files.readAllBytes(imagePath);
            ObjectNode body = MAPPER.createObjectNode();
            body.put("model", qwen.getModel());
            ArrayNode messages = body.putArray("messages");
            ObjectNode user = messages.addObject();
            user.put("role", "user");
            ArrayNode content = user.putArray("content");
            ObjectNode imagePart = content.addObject();
            imagePart.put("type", "image_url");
            imagePart.putObject("image_url")
                    .put("url", "data:" + mimeOf(imagePath) + ";base64,"
                            + Base64.getEncoder().encodeToString(bytes));
            ObjectNode textPart = content.addObject();
            textPart.put("type", "text");
            textPart.put("text", notBlank(instruction) ? instruction : qwen.getPrompt());

            HttpRequest request = HttpRequest.newBuilder(URI.create(trimBaseUrl(qwen.getBaseUrl()) + "/chat/completions"))
                    .timeout(Duration.ofSeconds(Math.max(10, qwen.getTimeoutSeconds())))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + qwen.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new BusinessException("云端 OCR 调用失败（HTTP " + response.statusCode() + "）："
                        + abbreviate(response.body()));
            }
            String text = extractText(response.body());
            if (text == null) {
                throw new BusinessException("云端 OCR 响应结构无法解析：" + abbreviate(response.body()));
            }
            // 大模型接口不给逐框置信度：score 留空，这份结果的质量只能靠下游的结构化指标判断
            return OcrText.of(text);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("云端 OCR 识别异常：{} {}", imagePath, e.getMessage());
            throw new BusinessException("云端 OCR 识别失败：" + e.getMessage());
        }
    }

    /** 取 choices[0].message.content：兼容字符串形态与「多段 content」数组形态 */
    private String extractText(String responseBody) throws Exception {
        JsonNode root = MAPPER.readTree(responseBody);
        JsonNode choice = root.path("choices").path(0).path("message").path("content");
        if (choice.isTextual()) {
            return choice.asText();
        }
        if (choice.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode part : choice) {
                JsonNode text = part.path("text");
                if (text.isTextual()) {
                    sb.append(text.asText()).append('\n');
                }
            }
            return sb.toString().trim();
        }
        return null;
    }

    private String mimeOf(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = fileName.lastIndexOf('.');
        String ext = dot < 0 ? "" : fileName.substring(dot + 1);
        return switch (ext) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            case "webp" -> "image/webp";
            default -> "image/png";
        };
    }

    /** baseUrl 允许带或不带结尾斜杠，统一去掉后再拼 /chat/completions */
    private String trimBaseUrl(String baseUrl) {
        String trimmed = baseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= 200 ? oneLine : oneLine.substring(0, 200) + "…";
    }
}
