package com.zhikao.modules.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhikao.common.BusinessException;
import com.zhikao.common.DictTypeConst;
import com.zhikao.common.PageResult;
import com.zhikao.entity.SysDictItem;
import com.zhikao.entity.SysDictType;
import com.zhikao.mapper.SysDictItemMapper;
import com.zhikao.mapper.SysDictTypeMapper;
import com.zhikao.modules.system.dto.DictItemSaveDTO;
import com.zhikao.modules.system.dto.DictTypeSaveDTO;
import com.zhikao.modules.system.vo.DictItemVO;
import com.zhikao.modules.system.vo.DictTypeCountVO;
import com.zhikao.modules.system.vo.DictTypeVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 数据字典服务实现。
 */
@Service
public class SysDictServiceImpl implements SysDictService {

    /** 启用状态 */
    private static final int ENABLED = 1;

    private final SysDictTypeMapper dictTypeMapper;
    private final SysDictItemMapper dictItemMapper;

    public SysDictServiceImpl(SysDictTypeMapper dictTypeMapper, SysDictItemMapper dictItemMapper) {
        this.dictTypeMapper = dictTypeMapper;
        this.dictItemMapper = dictItemMapper;
    }

    @Override
    public List<DictItemVO> items(String dictType) {
        if (!StringUtils.hasText(dictType)) {
            return List.of();
        }
        return dictItemMapper.selectList(Wrappers.<SysDictItem>lambdaQuery()
                        .eq(SysDictItem::getDictType, dictType)
                        .eq(SysDictItem::getStatus, ENABLED)
                        .orderByAsc(SysDictItem::getSort)
                        .orderByAsc(SysDictItem::getId))
                .stream().map(this::toItemVO).collect(Collectors.toList());
    }

    @Override
    public Map<String, List<DictItemVO>> batch(List<String> types) {
        if (types == null || types.isEmpty()) {
            return Map.of();
        }
        // 一次 IN 查询取回全部类型的字典项，再在内存里分组：避免 N 次往返
        List<SysDictItem> all = dictItemMapper.selectList(Wrappers.<SysDictItem>lambdaQuery()
                .in(SysDictItem::getDictType, types)
                .eq(SysDictItem::getStatus, ENABLED)
                .orderByAsc(SysDictItem::getSort)
                .orderByAsc(SysDictItem::getId));

        Map<String, List<DictItemVO>> grouped = new LinkedHashMap<>();
        // 即使某类型没有字典项也返回空数组，前端无需判空
        types.forEach(type -> grouped.put(type, new ArrayList<>()));
        for (SysDictItem item : all) {
            grouped.computeIfAbsent(item.getDictType(), key -> new ArrayList<>()).add(toItemVO(item));
        }
        return grouped;
    }

    @Override
    public PageResult<DictTypeVO> typePage(String keyword, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<SysDictType> wrapper = Wrappers.<SysDictType>lambdaQuery()
                .and(StringUtils.hasText(keyword), w -> w
                        .like(SysDictType::getDictName, keyword)
                        .or()
                        .like(SysDictType::getDictType, keyword))
                .orderByAsc(SysDictType::getId);

        long safeNum = pageNum == null || pageNum < 1 ? 1L : pageNum;
        long safeSize = pageSize == null || pageSize < 1 ? 10L : Math.min(pageSize, 100);
        Page<SysDictType> page = dictTypeMapper.selectPage(new Page<>(safeNum, safeSize), wrapper);

        // 一次聚合取回各类型的字典项数量，避免逐行 count（N+1）
        Map<String, Long> counts = countingByType();
        return PageResult.of(page, type -> {
            DictTypeVO vo = new DictTypeVO();
            vo.setId(type.getId());
            vo.setDictName(type.getDictName());
            vo.setDictType(type.getDictType());
            vo.setRemark(type.getRemark());
            vo.setStatus(type.getStatus());
            vo.setCreateTime(type.getCreateTime());
            vo.setItemCount(counts.getOrDefault(type.getDictType(), 0L).intValue());
            return vo;
        });
    }

    @Override
    public List<DictItemVO> itemList(String dictType) {
        return dictItemMapper.selectList(Wrappers.<SysDictItem>lambdaQuery()
                        .eq(StringUtils.hasText(dictType), SysDictItem::getDictType, dictType)
                        .orderByAsc(SysDictItem::getSort)
                        .orderByAsc(SysDictItem::getId))
                .stream().map(this::toItemVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveType(DictTypeSaveDTO dto) {
        boolean create = dto.getId() == null;
        Long duplicated = dictTypeMapper.selectCount(Wrappers.<SysDictType>lambdaQuery()
                .eq(SysDictType::getDictType, dto.getDictType())
                .ne(!create, SysDictType::getId, dto.getId()));
        if (duplicated > 0) {
            throw new BusinessException("字典类型已存在：" + dto.getDictType());
        }

        SysDictType type = SysDictType.builder()
                .id(dto.getId())
                .dictName(dto.getDictName().trim())
                .dictType(dto.getDictType().trim())
                .remark(dto.getRemark())
                .status(dto.getStatus() == null ? ENABLED : dto.getStatus())
                .build();
        if (create) {
            type.setCreateTime(LocalDateTime.now());
            dictTypeMapper.insert(type);
            return;
        }
        requireTypeExists(dto.getId());
        dictTypeMapper.updateById(type);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeType(Long id) {
        SysDictType type = requireTypeExists(id);
        if (DictTypeConst.isBuiltIn(type.getDictType())) {
            throw new BusinessException("内置字典不允许删除，可停用或修改其字典项");
        }
        dictItemMapper.delete(Wrappers.<SysDictItem>lambdaQuery()
                .eq(SysDictItem::getDictType, type.getDictType()));
        dictTypeMapper.deleteById(id);
    }

    @Override
    public void saveItem(DictItemSaveDTO dto) {
        boolean create = dto.getId() == null;
        if (create) {
            // 新增时必须挂在已存在的字典类型下，避免出现「孤儿字典项」
            Long typeExists = dictTypeMapper.selectCount(Wrappers.<SysDictType>lambdaQuery()
                    .eq(SysDictType::getDictType, dto.getDictType()));
            if (typeExists == 0) {
                throw new BusinessException("字典类型不存在：" + dto.getDictType());
            }
        }
        // 同一类型下取值唯一，否则前端按 value 反查 label 会出现二义
        Long duplicated = dictItemMapper.selectCount(Wrappers.<SysDictItem>lambdaQuery()
                .eq(SysDictItem::getDictType, dto.getDictType())
                .eq(SysDictItem::getItemValue, dto.getItemValue())
                .ne(!create, SysDictItem::getId, dto.getId()));
        if (duplicated > 0) {
            throw new BusinessException("该字典下已存在取值：" + dto.getItemValue());
        }

        SysDictItem item = SysDictItem.builder()
                .id(dto.getId())
                .dictType(dto.getDictType())
                .itemLabel(dto.getItemLabel().trim())
                .itemValue(dto.getItemValue().trim())
                .color(dto.getColor())
                .sort(dto.getSort() == null ? 0 : dto.getSort())
                .status(dto.getStatus() == null ? ENABLED : dto.getStatus())
                .build();
        if (create) {
            item.setCreateTime(LocalDateTime.now());
            dictItemMapper.insert(item);
            return;
        }
        requireItemExists(dto.getId());
        dictItemMapper.updateById(item);
    }

    @Override
    public void removeItem(Long id) {
        requireItemExists(id);
        dictItemMapper.deleteById(id);
    }

    // ==================== 内部方法 ====================

    private Map<String, Long> countingByType() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (DictTypeCountVO row : dictItemMapper.countGroupByType()) {
            if (row.getDictType() != null) {
                counts.put(row.getDictType(), row.getTotal() == null ? 0L : row.getTotal());
            }
        }
        return counts;
    }

    private SysDictType requireTypeExists(Long id) {
        SysDictType type = dictTypeMapper.selectById(id);
        if (type == null) {
            throw new BusinessException("字典类型不存在");
        }
        return type;
    }

    private void requireItemExists(Long id) {
        if (dictItemMapper.selectById(id) == null) {
            throw new BusinessException("字典项不存在");
        }
    }

    private DictItemVO toItemVO(SysDictItem item) {
        DictItemVO vo = new DictItemVO();
        vo.setId(item.getId());
        vo.setDictType(item.getDictType());
        vo.setLabel(item.getItemLabel());
        vo.setValue(item.getItemValue());
        vo.setColor(item.getColor());
        vo.setSort(item.getSort());
        vo.setStatus(item.getStatus());
        return vo;
    }

    /** 供 DataInitializer 复用的空判断（避免重复初始化字典项） */
    boolean isEmpty() {
        return Objects.equals(dictTypeMapper.selectCount(null), 0L);
    }
}
