package com.zhikao.modules.exam;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhikao.common.BusinessException;
import com.zhikao.common.DataScope;
import com.zhikao.common.JsonHelper;
import com.zhikao.common.LoginUtil;
import com.zhikao.common.PageResult;
import com.zhikao.common.QuestionTypeEnum;
import com.zhikao.entity.Exam;
import com.zhikao.entity.Paper;
import com.zhikao.entity.PaperQuestion;
import com.zhikao.entity.Question;
import com.zhikao.entity.Subject;
import com.zhikao.mapper.ExamMapper;
import com.zhikao.mapper.PaperMapper;
import com.zhikao.mapper.PaperQuestionMapper;
import com.zhikao.mapper.QuestionMapper;
import com.zhikao.mapper.SubjectMapper;
import com.zhikao.modules.exam.dto.PaperManualDTO;
import com.zhikao.modules.exam.dto.PaperQueryDTO;
import com.zhikao.modules.exam.dto.PaperRandomDTO;
import com.zhikao.modules.exam.vo.PaperDetailVO;
import com.zhikao.modules.exam.vo.PaperQuestionVO;
import com.zhikao.modules.exam.vo.PaperVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 试卷服务实现。
 */
@Service
public class PaperServiceImpl implements PaperService {

    /** 组卷方式：手动 */
    private static final int GEN_MANUAL = 1;
    /** 组卷方式：随机 */
    private static final int GEN_RANDOM = 2;

    /** 默认答题时长（分钟） */
    private static final int DEFAULT_DURATION = 90;

    private final com.zhikao.mapper.PaperKnowledgeMapper knowledgeSnapshots;
    private final PaperMapper paperMapper;
    private final PaperQuestionMapper paperQuestionMapper;
    private final QuestionMapper questionMapper;
    private final SubjectMapper subjectMapper;
    private final ExamMapper examMapper;
    private final DataScope dataScope;
    private final com.zhikao.modules.file.FileBlobStore fileBlobs;

    public PaperServiceImpl(com.zhikao.mapper.PaperKnowledgeMapper knowledgeSnapshots,PaperMapper paperMapper, PaperQuestionMapper paperQuestionMapper,
                            QuestionMapper questionMapper, SubjectMapper subjectMapper,
                            ExamMapper examMapper, DataScope dataScope,com.zhikao.modules.file.FileBlobStore fileBlobs) {
        this.fileBlobs=fileBlobs;
        this.knowledgeSnapshots=knowledgeSnapshots;
        this.paperMapper = paperMapper;
        this.paperQuestionMapper = paperQuestionMapper;
        this.questionMapper = questionMapper;
        this.subjectMapper = subjectMapper;
        this.examMapper = examMapper;
        this.dataScope = dataScope;
    }

    @Override
    public PageResult<PaperVO> page(PaperQueryDTO query) {
        LambdaQueryWrapper<Paper> wrapper = Wrappers.<Paper>lambdaQuery()
                .eq(query.getSubjectId() != null, Paper::getSubjectId, query.getSubjectId())
                .eq(query.getGenType() != null, Paper::getGenType, query.getGenType())
                .eq(query.getStatus() != null, Paper::getStatus, query.getStatus())
                .like(StringUtils.hasText(query.getKeyword()), Paper::getPaperName, query.getKeyword())
                .orderByDesc(Paper::getId);

        Page<Paper> page = paperMapper.selectPage(
                new Page<>(query.safePageNum(), query.safePageSize()), wrapper);
        Map<Long, String> subjectNames = subjectNames();
        return PageResult.of(page, paper -> toVO(paper, subjectNames));
    }

    @Override
    public List<PaperVO> list() {
        Map<Long, String> subjectNames = subjectNames();
        return paperMapper.selectList(Wrappers.<Paper>lambdaQuery().orderByDesc(Paper::getId))
                .stream().map(paper -> toVO(paper, subjectNames)).collect(Collectors.toList());
    }

    @Override
    public PaperDetailVO detail(Long id) {
        Paper paper = paperMapper.selectById(id);
        if (paper == null) {
            throw new BusinessException("试卷不存在");
        }
        PaperDetailVO detail = new PaperDetailVO();
        detail.setPaper(toVO(paper, subjectNames()));
        detail.setQuestions(listQuestions(id, true));
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long genManual(PaperManualDTO dto) {
        return createPaper(dto.getPaperName(), dto.getSubjectId(), dto.getTotalDuration(),
                GEN_MANUAL, dto.getQuestions());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long genRandom(PaperRandomDTO dto) {
        if (subjectMapper.selectById(dto.getSubjectId()) == null) {
            throw new BusinessException("科目不存在");
        }

        List<PaperManualDTO.Item> picked = new ArrayList<>();
        List<Long> pickedIds = new ArrayList<>();
        // 逐条规则抽题：用已抽到的 id 去重，保证同一份试卷不出现重复题
        for (PaperRandomDTO.Rule rule : dto.getRules()) {
            QuestionTypeEnum type = QuestionTypeEnum.of(rule.getType());
            if (type == null) {
                throw new BusinessException("组卷规则中的题型不合法");
            }
            long available = questionMapper.selectCount(Wrappers.<Question>lambdaQuery()
                    .eq(Question::getSubjectId, dto.getSubjectId())
                    .eq(Question::getType, rule.getType())
                    .eq(rule.getDifficulty() != null, Question::getDifficulty, rule.getDifficulty()));
            if (available < rule.getCount()) {
                throw new BusinessException(String.format("%s 题目不足：需要 %d 道，题库中符合条件的只有 %d 道",
                        type.getLabel(), rule.getCount(), available));
            }

            List<Long> ids = questionMapper.selectRandomIds(dto.getSubjectId(), rule.getType(),
                    rule.getDifficulty(), pickedIds, rule.getCount());
            if (ids.size() < rule.getCount()) {
                throw new BusinessException(type.getLabel()
                        + " 题目去重后数量不足，请减少抽题数量或先补充题库");
            }
            for (Long questionId : ids) {
                PaperManualDTO.Item item = new PaperManualDTO.Item();
                item.setQuestionId(questionId);
                item.setScore(rule.getScore());
                picked.add(item);
                pickedIds.add(questionId);
            }
        }
        return createPaper(dto.getPaperName(), dto.getSubjectId(), dto.getTotalDuration(), GEN_RANDOM, picked);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        Paper paper = paperMapper.selectById(id);
        if (paper == null) {
            throw new BusinessException("试卷不存在");
        }
        // 水平越权校验：教师只能发布/撤回自己创建的试卷（管理员不受限）
        dataScope.requireOwner(paper.getCreateBy(), "试卷");
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("试卷状态不合法");
        }
        paper.setStatus(status);
        paperMapper.updateById(paper);
    }

    @Override
    public void remove(Long id) {
        Paper paper = paperMapper.selectById(id);
        if (paper == null) {
            throw new BusinessException("试卷不存在");
        }
        dataScope.requireOwner(paper.getCreateBy(), "试卷");
        // 已被考试引用的试卷不能删：否则考试取卷会失败
        Long used = examMapper.selectCount(Wrappers.<Exam>lambdaQuery().eq(Exam::getPaperId, id));
        if (used > 0) {
            throw new BusinessException("该试卷已被 " + used + " 场考试使用，不能删除");
        }
        paperQuestionMapper.delete(Wrappers.<PaperQuestion>lambdaQuery().eq(PaperQuestion::getPaperId, id));
        paperMapper.deleteById(id);
    }

    /**
     * 查询试卷题目快照（供教师预览、学生取卷、阅卷复用）。
     *
     * @param withAnswer 是否带标准答案与解析（教师预览 / 阅卷传 true；学生取卷传 false）
     */
    @Override
    public List<PaperQuestionVO> listQuestions(Long paperId, boolean withAnswer) {
        return paperQuestionMapper.selectList(Wrappers.<PaperQuestion>lambdaQuery()
                        .eq(PaperQuestion::getPaperId, paperId)
                        .orderByAsc(PaperQuestion::getSort))
                .stream()
                .map(entity -> toQuestionVO(entity, withAnswer))
                .collect(Collectors.toList());
    }

    PaperQuestionVO toQuestionVO(PaperQuestion entity, boolean withAnswer) {
        QuestionTypeEnum type = QuestionTypeEnum.of(entity.getType());
        PaperQuestionVO vo = new PaperQuestionVO();
        vo.setId(entity.getId());
        vo.setQuestionId(withAnswer ? entity.getQuestionId() : null);
        vo.setSort(entity.getSort());
        vo.setType(entity.getType());
        vo.setTypeName(QuestionTypeEnum.labelOf(entity.getType()));
        vo.setObjective(type != null && type.isObjective());
        vo.setContent(entity.getContent());
        vo.setOptions(JsonHelper.toOptionMap(entity.getOptions()));
        vo.setScore(entity.getScore());
        vo.setDifficulty(entity.getDifficulty());
        vo.setKnowledgePoint(entity.getKnowledgePoint());
        if (withAnswer) {
            vo.setAnswer(entity.getAnswer());
            vo.setAnalysis(entity.getAnalysis());
        }
        return vo;
    }

    // ==================== 内部方法 ====================

    /**
     * 统一的建卷流程（手工 / 随机共用）：
     * 校验科目与题目 -> 生成 paper -> 逐题写入 paper_question 快照 -> 回填总分与题数。
     */
    private Long createPaper(String paperName, Long subjectId, Integer duration, int genType,
                             List<PaperManualDTO.Item> items) {
        if (subjectMapper.selectById(subjectId) == null) {
            throw new BusinessException("科目不存在");
        }
        // 去重（同一题只保留第一次出现，保持选择顺序）
        Map<Long, PaperManualDTO.Item> itemMap = new LinkedHashMap<>();
        for (PaperManualDTO.Item item : items) {
            itemMap.putIfAbsent(item.getQuestionId(), item);
        }
        if (itemMap.isEmpty()) {
            throw new BusinessException("请至少选择一道题目");
        }

        Map<Long, Question> questionMap = questionMapper.selectBatchIds(itemMap.keySet()).stream()
                .collect(Collectors.toMap(Question::getId, question -> question));
        if (questionMap.isEmpty()) {
            throw new BusinessException("所选题目不存在，请刷新后重试");
        }

        Paper paper = Paper.builder()
                .paperName(paperName.trim())
                .subjectId(subjectId)
                .totalDuration(duration == null ? DEFAULT_DURATION : duration)
                .genType(genType)
                .questionCount(0)
                .totalScore(BigDecimal.ZERO)
                .status(0)
                .createBy(LoginUtil.username())
                .createTime(LocalDateTime.now())
                .build();
        paperMapper.insert(paper);

        BigDecimal totalScore = BigDecimal.ZERO;
        int sort = 1;
        for (Map.Entry<Long, PaperManualDTO.Item> entry : itemMap.entrySet()) {
            Question source = questionMap.get(entry.getKey());
            if (source == null) {
                continue;
            }
            if (!source.getSubjectId().equals(subjectId)) {
                throw new BusinessException("题目「" + source.getContent() + "」不属于所选科目，请检查");
            }
            BigDecimal score = entry.getValue().getScore() == null ? source.getScore() : entry.getValue().getScore();
            if (score == null || score.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("题目分值必须大于 0");
            }

            PaperQuestion snapshot=PaperQuestion.builder()
                    .paperId(paper.getId())
                    .questionId(source.getId())
                    .sort(sort++)
                    .type(source.getType())
                    .content(source.getContent())
                    .options(source.getOptions())
                    .answer(source.getAnswer())
                    .analysis(source.getAnalysis())
                    .score(score)
                    .difficulty(source.getDifficulty())
                    .knowledgePoint(source.getKnowledgePoint())
                    .build();
            fileBlobs.lockReferences(snapshot.getContent(),snapshot.getOptions(),snapshot.getAnswer(),snapshot.getAnalysis());
            paperQuestionMapper.insert(snapshot);
            knowledgeSnapshots.snapshot(snapshot.getId(),source.getId());
            totalScore = totalScore.add(score);
        }

        paper.setQuestionCount(sort - 1);
        paper.setTotalScore(totalScore);
        paperMapper.updateById(paper);
        return paper.getId();
    }

    /** 科目名称映射，避免列表逐行查库 */
    private Map<Long, String> subjectNames() {
        return subjectMapper.selectList(null).stream()
                .collect(Collectors.toMap(Subject::getId, Subject::getSubjectName));
    }

    private PaperVO toVO(Paper paper, Map<Long, String> subjectNames) {
        PaperVO vo = new PaperVO();
        vo.setId(paper.getId());
        vo.setPaperName(paper.getPaperName());
        vo.setSubjectId(paper.getSubjectId());
        vo.setSubjectName(subjectNames.get(paper.getSubjectId()));
        vo.setTotalScore(paper.getTotalScore());
        vo.setTotalDuration(paper.getTotalDuration());
        vo.setGenType(paper.getGenType());
        vo.setGenTypeName(paper.getGenType() != null && paper.getGenType() == GEN_RANDOM ? "随机组卷" : "手工组卷");
        vo.setQuestionCount(paper.getQuestionCount());
        vo.setStatus(paper.getStatus());
        vo.setCreateBy(paper.getCreateBy());
        vo.setCreateTime(paper.getCreateTime());
        return vo;
    }
}
