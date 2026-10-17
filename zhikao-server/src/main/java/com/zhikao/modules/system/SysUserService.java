package com.zhikao.modules.system;

import com.zhikao.common.ImportResultVO;
import com.zhikao.common.PageResult;
import com.zhikao.modules.system.dto.UserQueryDTO;
import com.zhikao.modules.system.dto.UserSaveDTO;
import com.zhikao.modules.system.vo.UserVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 用户管理服务。
 */
public interface SysUserService {

    /** 分页查询用户（支持关键字、角色、班级、状态筛选） */
    PageResult<UserVO> page(UserQueryDTO query);

    /** 新增或修改用户 */
    void save(UserSaveDTO dto);

    /** 启用 / 禁用用户 */
    void updateStatus(Long id, Integer status);

    /** 重置密码为默认密码，返回重置后的明文密码供页面提示 */
    String resetPassword(Long id);

    /** 删除用户 */
    void remove(Long id);

    /** 批量启用 / 禁用用户（自动排除当前登录账号，避免把自己锁在系统外） */
    void batchStatus(List<Long> ids, Integer status);

    /** 批量删除用户（逐条校验存在性，给出明确定位） */
    void batchRemove(List<Long> ids);

    /** 从 Excel 批量导入用户（每行独立处理，单行失败不影响其他行） */
    ImportResultVO importUsers(MultipartFile file);

    /** 生成导入模板（xlsx 字节） */
    byte[] importTemplate();

    /**
     * 更新当前登录用户的头像。
     * 刻意只允许改「自己的」——用户管理里的头像由管理员在编辑用户时设置，不走这里。
     */
    void updateMyAvatar(String avatarUrl);
}
