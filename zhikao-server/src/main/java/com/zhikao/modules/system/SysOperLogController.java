package com.zhikao.modules.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.zhikao.common.PageResult;
import com.zhikao.common.R;
import com.zhikao.modules.system.dto.OperLogQueryDTO;
import com.zhikao.modules.system.vo.OperLogVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 操作日志接口（审计面，仅 system:log:view 可见——seed 里只有超级管理员持有）。
 */
@RestController
@RequestMapping("/log")
@SaCheckPermission("system:log:view")
public class SysOperLogController {

    private final SysOperLogService operLogService;

    public SysOperLogController(SysOperLogService operLogService) {
        this.operLogService = operLogService;
    }

    /** 分页查询操作日志 */
    @GetMapping("/page")
    public R<PageResult<OperLogVO>> page(OperLogQueryDTO query) {
        return R.ok(operLogService.page(query));
    }

    /** 模块下拉选项 */
    @GetMapping("/modules")
    public R<List<String>> modules() {
        return R.ok(operLogService.modules());
    }
}
