package com.zhikao.modules.file;

import com.zhikao.common.BusinessException;
import com.zhikao.common.ErrorCode;
import com.zhikao.common.LoginUtil;
import com.zhikao.common.OperLog;
import com.zhikao.common.PageResult;
import com.zhikao.common.R;
import com.zhikao.entity.SysFile;
import com.zhikao.modules.file.vo.FileVO;
import com.zhikao.modules.system.SysUserService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * 文件中心接口（三期新增）。
 *
 * <p>职责边界：只管「文件」这一件事——上传、预览、下载、删除、按业务查附件。
 * 业务导入（题目 Excel 等）仍由业务模块自己的接口负责，
 * 这样「文件存哪」与「题目怎么校验」不会互相污染。
 * 数据库查询已全部下沉到 {@link FileStorageService}，Controller 只做参数透传与响应包装。
 *
 * <p>权限约定（RBAC 改造后）：
 * <ul>
 *   <li><b>学生</b>（无 file:manage）：只能上传头像类文件，且只能看到/删除自己上传的；</li>
 *   <li><b>教师</b>（无 file:manage）：可上传表格、试卷附件、题目文件；</li>
 *   <li><b>持有 file:manage 权限者</b>：额外可查看与删除全站他人的文件。</li>
 * </ul>
 * 上传接口不放在 {@code @SaCheckPermission} 上而用方法级判断，正是因为「学生能不能上传」
 * 取决于分类，而不是取决于接口本身。
 */
@RestController
@RequestMapping("/file")
public class FileController {

    private final FileStorageService storageService;
    private final SysUserService userService;

    public FileController(FileStorageService storageService, SysUserService userService) {
        this.storageService = storageService;
        this.userService = userService;
    }

    /**
     * 通用上传。
     * 学生只允许 {@code avatar} 分类：他们是系统的数据消费者，
     * 放开任意上传等于给自己开了一个写入口。
     */
    @PostMapping("/upload")
    @OperLog(module = "文件中心", action = "上传文件", saveParams = false)
    public R<FileVO> upload(@RequestParam("file") MultipartFile file,
                            @RequestParam(value = "category", required = false) String category,
                            @RequestParam(value = "bizType", required = false) String bizType,
                            @RequestParam(value = "bizId", required = false) Long bizId) {
        FileCategory fileCategory = FileCategory.of(category);
        assertUploadAllowed(fileCategory);
        SysFile meta = storageService.store(file, fileCategory, bizType, bizId);
        return R.ok(storageService.toVO(meta));
    }

    /** 上传并设置当前用户头像（头像只需要一步：传完就是我的了） */
    @PostMapping("/avatar")
    @OperLog(module = "个人设置", action = "更换头像", saveParams = false)
    public R<FileVO> uploadAvatar(@RequestParam("file") MultipartFile file) {
        SysFile meta = storageService.store(file, FileCategory.AVATAR, "avatar", null);
        userService.updateMyAvatar(storageService.urlOf(meta));
        return R.ok(storageService.toVO(meta));
    }

    /** 清除头像（回退到姓名首字展示） */
    @DeleteMapping("/avatar")
    public R<Void> clearAvatar() {
        userService.updateMyAvatar(null);
        return R.ok();
    }

    /** 我的文件（持有 file:manage 权限的用户可传 mine=false 查看全站文件） */
    @GetMapping("/page")
    public R<PageResult<FileVO>> page(@RequestParam(defaultValue = "1") Integer pageNum,
                                      @RequestParam(defaultValue = "10") Integer pageSize,
                                      @RequestParam(required = false) String category,
                                      @RequestParam(defaultValue = "true") boolean mine) {
        return R.ok(storageService.pageFiles(pageNum, pageSize, category, mine));
    }

    /** 按业务查附件（如某份试卷挂了哪些文件） */
    @GetMapping("/attachments")
    public R<List<FileVO>> attachments(@RequestParam String bizType,
                                       @RequestParam Long bizId) {
        return R.ok(storageService.attachments(bizType, bizId));
    }

    /** 删除文件（本人或管理员） */
    @DeleteMapping("/{id}")
    @OperLog(module = "文件中心", action = "删除文件")
    public R<Void> remove(@PathVariable Long id) {
        storageService.delete(id);
        return R.ok();
    }

    /** 内联预览：图片可用作 img src，PDF 可直接在浏览器打开 */
    @GetMapping("/view/{id}")
    public ResponseEntity<Resource> view(@PathVariable Long id) {
        SysFile meta = storageService.require(id);
        Resource resource = storageService.loadResource(meta);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        (inlineAllowed(meta) ? "inline" : "attachment") + "; filename*=UTF-8''" + encode(meta.getOriginalName()))
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox; default-src 'none'")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(resolveMediaType(meta))
                .body(resource);
    }

    /** 下载：强制 attachment，文件名用原始名 */
    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        SysFile meta = storageService.require(id);
        Resource resource = storageService.loadResource(meta);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encode(meta.getOriginalName()))
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    // ==================== 内部方法 ====================

    /** 学生只能上传头像；其他分类属管理侧能力 */
    private void assertUploadAllowed(FileCategory category) {
        String role = LoginUtil.roleCode();
        if ("student".equals(role) && category != FileCategory.AVATAR) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "学生账号只能上传头像图片；如需导入题目，请联系教师使用题库导入功能");
        }
    }

    /** 按存储的 content-type 回放；缺失时按扩展名兜底 */
    private boolean inlineAllowed(SysFile meta) {
        return java.util.Set.of("png","jpg","jpeg","gif","webp","bmp","pdf").contains(meta.getExt());
    }
    private MediaType resolveMediaType(SysFile meta) {
        if(!inlineAllowed(meta))return MediaType.APPLICATION_OCTET_STREAM;
        return switch(meta.getExt()) {
            case "png" -> MediaType.IMAGE_PNG;
            case "jpg","jpeg" -> MediaType.IMAGE_JPEG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "webp" -> MediaType.parseMediaType("image/webp");
            case "bmp" -> MediaType.parseMediaType("image/bmp");
            case "pdf" -> MediaType.APPLICATION_PDF;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    /** RFC 5987 编码：中文文件名必须这样写，否则浏览器会拿到乱码 */
    private String encode(String name) {
        return URLEncoder.encode(name == null ? "file" : name, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }
}
