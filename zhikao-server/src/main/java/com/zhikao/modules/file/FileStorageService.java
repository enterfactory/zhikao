package com.zhikao.modules.file;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhikao.common.BusinessException;
import com.zhikao.common.ErrorCode;
import com.zhikao.common.LoginUtil;
import com.zhikao.common.PageResult;
import com.zhikao.config.UploadProperties;
import com.zhikao.entity.SysFile;
import com.zhikao.mapper.SysFileMapper;
import com.zhikao.modules.file.vo.FileVO;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/** File references carry ownership and purpose; FileBlobStore owns shared bytes and reclamation.
 * FileTypeVerifier validates server-side types. No client filename becomes a disk path.
 */
@Service
public class FileStorageService {

    /**
     * 允许的 access_path 形态：category/yyyy/MM/uuid.ext，只允许字母数字与斜杠点。
     *
     * <p>注意 uuid 部分用的是**去掉短横线**的 32 位十六进制（见 {@code storedName} 的生成），
     * 所以这里必须按 32 位匹配 —— 写成 {@code {36}} 会把自家生成的路径判成非法，
     * 表现为「上传后报『文件路径不合法』」。两者必须一起改。
     */
    private static final String ACCESS_PATH_PATTERN =
            "^[a-z]{3,10}/\\d{4}/\\d{2}/[a-f0-9]{32}\\.[a-z0-9]{1,8}$";

    private final UploadProperties properties;
    private final SysFileMapper fileMapper;
    private final FileBlobStore blobs;
    private final FileTypeVerifier types;

    public FileStorageService(UploadProperties properties, SysFileMapper fileMapper, FileBlobStore blobs, FileTypeVerifier types) {
        this.blobs=blobs;this.types=types;
        this.properties = properties;
        this.fileMapper = fileMapper;
    }

    // ==================== 上传 ====================

    /**
     * 保存上传文件。
     *
     * @param file     上传文件
     * @param category 分类（决定校验规则）
     * @param bizType  业务标签，可为空
     * @param bizId    业务主键，可为空
     * @return 文件元数据（含可直接放前端使用的访问地址）
     */
    @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
    public SysFile store(MultipartFile file, FileCategory category, String bizType, Long bizId) {
        if(file==null || file.isEmpty())throw new BusinessException("请选择要上传的文件");
        String name=sanitizeName(file.getOriginalFilename());String ext=extensionOf(name);
        validate(file,category,ext);
        byte[] bytes=readBytes(file,category);
        String mime=types.verify(bytes,ext);
        return saveReference(bytes,name,ext,mime,category,bizType,bizId,LoginUtil.username());
    }

    @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
    public SysFile storeGenerated(byte[] bytes,String name,String bizType) {
        return storeGenerated(bytes,name,bizType,LoginUtil.username());
    }
    @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
    public SysFile storeGenerated(byte[] bytes,String name,String bizType,String owner) {
        String safe=sanitizeName(name);String ext=extensionOf(safe);
        return saveReference(bytes,safe,ext,types.verify(bytes,ext),FileCategory.EXPORT,bizType,null,owner);
    }
    @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
    public SysFile storeImage(byte[] bytes,String name,String contentType,String bizType,String owner) {
        String safe=sanitizeName(name);String ext=extensionOf(safe);
        if(bytes==null || bytes.length>FileCategory.CRAWLER.getMaxBytes())throw new BusinessException("图片为空或超过大小限制");
        return saveReference(bytes,safe,ext,types.verify(bytes,ext),FileCategory.CRAWLER,bizType,null,owner);
    }
    private SysFile saveReference(byte[] bytes,String name,String ext,String mime,FileCategory category,
                                  String bizType,Long bizId,String owner) {
        if(!StringUtils.hasText(owner))throw new BusinessException("无法确定文件归属人");
        var blob=blobs.attach(bytes,ext,mime);
        // Idempotent per owner + business purpose. Other owners always get their own reference ID.
        var query=Wrappers.<SysFile>lambdaQuery().eq(SysFile::getBlobId,blob.getId())
                .eq(SysFile::getCreateBy,owner).eq(SysFile::getCategory,category.getCode())
                .eq(SysFile::getOriginalName,name);
        if(bizType==null)query.isNull(SysFile::getBizType);else query.eq(SysFile::getBizType,bizType);
        if(bizId==null)query.isNull(SysFile::getBizId);else query.eq(SysFile::getBizId,bizId);
        SysFile existing=fileMapper.selectOne(query.last("LIMIT 1"));
        if(existing!=null)return existing;
        SysFile record=SysFile.builder().blobId(blob.getId()).originalName(name).storedName(blob.getStoredName())
                .accessPath(blob.getAccessPath()).ext(ext).contentType(mime).sizeBytes(blob.getSizeBytes())
                .sha256(blob.getSha256()).category(category.getCode()).bizType(bizType).bizId(bizId)
                .createBy(owner).createTime(LocalDateTime.now()).build();
        fileMapper.insert(record);return record;
    }

    // ==================== 读取 / 删除 ====================

    /** 按 id 取文件元数据；不存在时抛 404 */
    public SysFile require(Long id) {
        SysFile meta = fileMapper.selectById(id);
        if (meta == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文件不存在或已被清理");
        }
        boolean sharedImage = "avatar".equals(meta.getCategory()) || ("crawler".equals(meta.getCategory())
                && !"svg".equalsIgnoreCase(meta.getExt()));
        if (!sharedImage && !isOwnerOrAdmin(meta)) throw new BusinessException(ErrorCode.FORBIDDEN,"无权读取该文件");
        return meta;
    }

    /** 按元数据取磁盘资源；文件被手工删除时给出可读提示 */
    public Resource loadResource(SysFile meta) {
        Path path = resolveSafe(meta.getAccessPath());
        if (!Files.exists(path)) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "文件内容已丢失（可能被手工清理），请重新上传：" + meta.getOriginalName());
        }
        return new FileSystemResource(path);
    }

    /** 删除当前业务引用；共享内容在最后一个引用解除24小时后回收。 */
    @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
    public void delete(Long id) {
        SysFile meta=require(id);
        if(!isOwnerOrAdmin(meta))throw new BusinessException(ErrorCode.FORBIDDEN,"只能删除自己上传的文件");
        blobs.release(meta,true);
    }

    @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
    public int purge(String category,LocalDateTime before) {
        if(!"export".equals(category))throw new BusinessException("自动清理仅支持过期导出文件");
        var expired=fileMapper.selectList(Wrappers.<SysFile>lambdaQuery().eq(SysFile::getCategory,category).lt(SysFile::getCreateTime,before));
        for(SysFile meta:expired)blobs.release(meta,false);
        return expired.size();
    }

    /** 归属校验：本人或持有 file:manage 权限者可删 */
    public boolean isOwnerOrAdmin(SysFile meta) {
        String current = LoginUtil.username();
        return StpUtil.hasPermission("file:manage") || (current != null && current.equals(meta.getCreateBy()));
    }

    // ==================== 查询（从 Controller 下沉，控制器不再直接摸 Mapper） ====================

    /**
     * 文件分页查询。
     *
     * <p>可见范围规则放在服务层而不是 Controller：
     * 只有持有 {@code file:manage} 权限的用户才能让 {@code mine=false} 生效看到全站文件，
     * 普通用户无论传什么参数都只能看到自己上传的——把「参数不可信」的兜底写死在这里，
     * 任何调用方复用这段查询都不会越权。
     */
    public PageResult<FileVO> pageFiles(Integer pageNum, Integer pageSize, String category, boolean mine) {
        boolean siteWide = StpUtil.hasPermission("file:manage");
        Page<SysFile> page = fileMapper.selectPage(
                new Page<>(Math.max(1, pageNum), Math.min(50, pageSize)),
                Wrappers.<SysFile>lambdaQuery()
                        .eq(StringUtils.hasText(category), SysFile::getCategory, category)
                        .eq(!siteWide || mine, SysFile::getCreateBy, LoginUtil.username())
                        .orderByDesc(SysFile::getId));
        return PageResult.of(page, this::toVO);
    }

    /** 按业务对象查附件（如某份试卷挂了哪些文件） */
    public List<FileVO> attachments(String bizType, Long bizId) {
        return fileMapper.selectList(Wrappers.<SysFile>lambdaQuery()
                        .eq(SysFile::getBizType, bizType)
                        .eq(SysFile::getBizId, bizId)
                        .orderByDesc(SysFile::getId))
                .stream().filter(this::isOwnerOrAdmin).map(this::toVO).toList();
    }

    /** 对外访问地址（前端直接塞进 img src 或 a href） */
    public String urlOf(SysFile meta) {
        return "/api/file/view/" + meta.getId();
    }

    /** 元数据 -> 前端视图对象 */
    public FileVO toVO(SysFile meta) {
        FileVO vo = new FileVO();
        vo.setId(meta.getId());
        vo.setName(meta.getOriginalName());
        vo.setExt(meta.getExt());
        vo.setSize(meta.getSizeBytes());
        vo.setSizeText(humanSize(meta.getSizeBytes()));
        vo.setCategory(meta.getCategory());
        vo.setBizType(meta.getBizType());
        vo.setBizId(meta.getBizId());
        vo.setUrl(urlOf(meta));
        vo.setDownloadUrl("/api/file/download/" + meta.getId());
        vo.setCreateBy(meta.getCreateBy());
        vo.setCreateTime(meta.getCreateTime());
        return vo;
    }

    /** 人类可读体积，避免前端每处都写一遍换算 */
    public static String humanSize(Long bytes) {
        if (bytes == null || bytes <= 0) {
            return "0 B";
        }
        String[] units = {"B", "KB", "MB", "GB"};
        double value = bytes;
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return (unit == 0 ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value))
                + " " + units[unit];
    }

    // ==================== 校验与工具 ====================

    private void validate(MultipartFile file, FileCategory category, String ext) {
        long limit = Math.min(category.getMaxBytes(), properties.getMaxBytes());
        if (file.getSize() > limit) {
            throw new BusinessException("文件过大：" + humanSize(file.getSize())
                    + "，" + category.getLabel() + " 上限为 " + humanSize(limit));
        }
        if (!category.getAllowedExt().contains(ext)) {
            throw new BusinessException(category.getLabel() + "只支持 " + category.allowedExtText()
                    + " 格式，当前为 ." + ext);
        }
    }

    private byte[] readBytes(MultipartFile file, FileCategory category) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException("读取上传内容失败：" + e.getMessage());
        }
        if (bytes.length == 0) {
            throw new BusinessException("文件内容为空");
        }
        return bytes;
    }

    /** 取扩展名（小写、无点）；无扩展名时回退 bin（随后会被白名单拒绝） */
    private String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "bin";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 清洗客户端文件名：只保留最后一段路径、去掉控制字符、限长。
     * 注意这里的目的**只是让展示好看**，安全性完全由「磁盘文件用 uuid」保证。
     */
    private String sanitizeName(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "未命名文件";
        }
        String name = raw.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isEmpty()) {
            name = "未命名文件";
        }
        return name.length() > 120 ? name.substring(0, 120) : name;
    }

    /**
     * 把 access_path 解析为绝对路径，并确认没有越出存储根目录。
     * 这是「即使库被改坏也不会读到目录外」的最后一道闸门。
     */
    private Path resolveSafe(String accessPath) {
        if (accessPath == null || !accessPath.matches(ACCESS_PATH_PATTERN)) {
            throw new BusinessException("文件路径不合法：" + accessPath);
        }
        Path root = Paths.get(properties.getDir()).toAbsolutePath().normalize();
        Path resolved = root.resolve(accessPath).normalize();
        if (!resolved.startsWith(root)) {
            throw new BusinessException("文件路径越界，已拒绝访问");
        }
        return resolved;
    }

}
