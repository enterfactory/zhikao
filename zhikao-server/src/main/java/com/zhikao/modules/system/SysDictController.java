package com.zhikao.modules.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.zhikao.common.OperLog;
import com.zhikao.common.PageResult;
import com.zhikao.common.R;
import com.zhikao.modules.system.dto.DictItemSaveDTO;
import com.zhikao.modules.system.dto.DictTypeSaveDTO;
import com.zhikao.modules.system.vo.DictItemVO;
import com.zhikao.modules.system.vo.DictTypeVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 数据字典接口。
 *
 * 权限分层：
 * - 读取（单查 / 批量）对所有登录用户开放——学生答题页也要用题型、状态等文案；
 * - 管理（分页 / 增删改）按 system:dict:manage 权限点（seed：仅超级管理员）。
 */
@RestController
@RequestMapping("/dict")
public class SysDictController {

    private final SysDictService dictService;

    public SysDictController(SysDictService dictService) {
        this.dictService = dictService;
    }

    /** 单类型查询 */
    @GetMapping("/{type}")
    public R<List<DictItemVO>> items(@PathVariable("type") String type) {
        return R.ok(dictService.items(type));
    }

    /** 批量查询：前端一次拉全，会话内复用 */
    @GetMapping("/batch")
    public R<Map<String, List<DictItemVO>>> batch(@RequestParam("types") String types) {
        List<String> list = Arrays.stream(types.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
        return R.ok(dictService.batch(list));
    }

    /** 字典类型分页（管理页） */
    @GetMapping("/type/page")
    @SaCheckPermission("system:dict:manage")
    public R<PageResult<DictTypeVO>> typePage(@RequestParam(required = false) String keyword,
                                              @RequestParam(defaultValue = "1") Integer pageNum,
                                              @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.ok(dictService.typePage(keyword, pageNum, pageSize));
    }

    /** 某字典类型下的全部字典项（管理页，含停用项） */
    @GetMapping("/item/list")
    @SaCheckPermission("system:dict:manage")
    public R<List<DictItemVO>> itemList(@RequestParam(required = false) String dictType) {
        return R.ok(dictService.itemList(dictType));
    }

    /** 新增或修改字典类型 */
    @PostMapping("/type")
    @SaCheckPermission("system:dict:manage")
    @OperLog(module = "数据字典", action = "保存字典类型")
    public R<Void> saveType(@Valid @RequestBody DictTypeSaveDTO dto) {
        dictService.saveType(dto);
        return R.ok();
    }

    /** 删除字典类型（内置字典会被拒绝） */
    @DeleteMapping("/type/{id}")
    @SaCheckPermission("system:dict:manage")
    @OperLog(module = "数据字典", action = "删除字典类型")
    public R<Void> removeType(@PathVariable Long id) {
        dictService.removeType(id);
        return R.ok();
    }

    /** 新增或修改字典项 */
    @PostMapping("/item")
    @SaCheckPermission("system:dict:manage")
    @OperLog(module = "数据字典", action = "保存字典项")
    public R<Void> saveItem(@Valid @RequestBody DictItemSaveDTO dto) {
        dictService.saveItem(dto);
        return R.ok();
    }

    /** 删除字典项 */
    @DeleteMapping("/item/{id}")
    @SaCheckPermission("system:dict:manage")
    @OperLog(module = "数据字典", action = "删除字典项")
    public R<Void> removeItem(@PathVariable Long id) {
        dictService.removeItem(id);
        return R.ok();
    }
}
