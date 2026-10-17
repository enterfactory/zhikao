package com.zhikao.modules.system;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhikao.common.BusinessException;
import com.zhikao.common.LoginUtil;
import com.zhikao.entity.Paper;
import com.zhikao.entity.Question;
import com.zhikao.entity.Subject;
import com.zhikao.mapper.PaperMapper;
import com.zhikao.mapper.QuestionMapper;
import com.zhikao.mapper.SubjectMapper;
import com.zhikao.modules.system.dto.SubjectSaveDTO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 科目管理服务实现。
 */
@Service
public class SubjectServiceImpl implements SubjectService {

    private final SubjectMapper subjectMapper;
    private final QuestionMapper questionMapper;
    private final PaperMapper paperMapper;

    public SubjectServiceImpl(SubjectMapper subjectMapper, QuestionMapper questionMapper,
                              PaperMapper paperMapper) {
        this.subjectMapper = subjectMapper;
        this.questionMapper = questionMapper;
        this.paperMapper = paperMapper;
    }

    @Override
    public List<Subject> list() {
        return subjectMapper.selectList(Wrappers.<Subject>lambdaQuery().orderByDesc(Subject::getId));
    }

    @Override
    public Long save(SubjectSaveDTO dto) {
        // 科目名称唯一：题目与试卷都按科目归类，重名会造成归类混乱
        Long duplicated = subjectMapper.selectCount(Wrappers.<Subject>lambdaQuery()
                .eq(Subject::getSubjectName, dto.getSubjectName())
                .ne(dto.getId() != null, Subject::getId, dto.getId()));
        if (duplicated > 0) {
            throw new BusinessException("科目已存在：" + dto.getSubjectName());
        }

        Subject entity = new Subject();
        entity.setId(dto.getId());
        entity.setSubjectName(dto.getSubjectName());
        entity.setSubjectCode(dto.getSubjectCode());
        if (dto.getId() == null) {
            entity.setCreateBy(LoginUtil.username());
            entity.setCreateTime(LocalDateTime.now());
            subjectMapper.insert(entity);
        } else {
            subjectMapper.updateById(entity);
        }
        return entity.getId();
    }

    @Override
    public void remove(Long id) {
        // 科目被题目或试卷引用时不允许删：删了之后那些题目的归属会变成悬空 ID，
        // 列表页显示不出来、筛选也筛不到，属于典型的「删了才发现有问题」。
        long questions = questionMapper.selectCount(Wrappers.<Question>lambdaQuery()
                .eq(Question::getSubjectId, id));
        if (questions > 0) {
            throw new BusinessException("该科目下还有 " + questions + " 道题目，请先转移或删除题目");
        }
        long papers = paperMapper.selectCount(Wrappers.<Paper>lambdaQuery()
                .eq(Paper::getSubjectId, id));
        if (papers > 0) {
            throw new BusinessException("该科目下还有 " + papers + " 份试卷，请先删除试卷");
        }
        subjectMapper.deleteById(id);
    }
}
