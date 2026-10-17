package com.zhikao.modules.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.zhikao.common.BatchIdsDTO;
import com.zhikao.common.ImportResultVO;
import com.zhikao.common.OperLog;
import com.zhikao.common.PageResult;
import com.zhikao.common.R;
import com.zhikao.modules.system.dto.UserQueryDTO;
import com.zhikao.modules.system.dto.UserSaveDTO;
import com.zhikao.modules.system.vo.UserVO;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 用户管理接口（系统管理，按 system:user:manage 权限点控制，
 * 超级管理员与教务管理员持有；角色分配在保存用户时通过 roleCode 一并完成）。
 */
@RestController
@RequestMapping("/user")
@SaCheckPermission("system:user:manage")
public class SysUserController {

    private final SysUserService userService;

    public SysUserController(SysUserService userService) {
        this.userService = userService;
    }

    /** 分页查询用户 */
    @GetMapping("/page")
    public R<PageResult<UserVO>> page(UserQueryDTO query) {
        return R.ok(userService.page(query));
    }

    /** 新增或修改用户（id 为空即新增） */
    @PostMapping
    @OperLog(module = "用户管理", action = "新增或修改用户")
    public R<Void> save(@Valid @RequestBody UserSaveDTO dto) {
        userService.save(dto);
        return R.ok();
    }

    /** 启用 / 禁用用户 */
    @PutMapping("/{id}/status")
    @OperLog(module = "用户管理", action = "启用或禁用用户")
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        userService.updateStatus(id, status);
        return R.ok();
    }

    /** 重置密码：返回重置后的明文密码，供页面提示告知用户 */
    @PutMapping("/{id}/password/reset")
    @OperLog(module = "用户管理", action = "重置密码")
    public R<String> resetPassword(@PathVariable Long id) {
        return R.ok(userService.resetPassword(id));
    }

    /** 删除用户 */
    @DeleteMapping("/{id}")
    @OperLog(module = "用户管理", action = "删除用户")
    public R<Void> remove(@PathVariable Long id) {
        userService.remove(id);
        return R.ok();
    }

    /** Excel 批量导入用户 */
    @PostMapping("/import")
    @OperLog(module = "用户管理", action = "批量导入用户", saveParams = false)
    public R<ImportResultVO> importUsers(@RequestParam("file") MultipartFile file) {
        return R.ok(userService.importUsers(file));
    }

    /** 批量启用 / 禁用 */
    @PutMapping("/batch/status")
    @OperLog(module = "用户管理", action = "批量启用或禁用用户")
    public R<Void> batchStatus(@RequestBody BatchIdsDTO body, @RequestParam Integer status) {
        userService.batchStatus(body.getIds(), status);
        return R.ok();
    }

    /** 批量删除 */
    @DeleteMapping("/batch")
    @OperLog(module = "用户管理", action = "批量删除用户")
    public R<Void> batchRemove(@RequestBody BatchIdsDTO body) {
        userService.batchRemove(body.getIds());
        return R.ok();
    }

    /** 下载导入模板（直接返回文件流，不套 R 结构） */
    @GetMapping("/import/template")
    public ResponseEntity<byte[]> importTemplate() {
        String filename = URLEncoder.encode("用户导入模板.xlsx", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(userService.importTemplate());
    }
}
