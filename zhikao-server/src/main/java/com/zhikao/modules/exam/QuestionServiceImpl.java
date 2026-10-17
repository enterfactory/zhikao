package com.zhikao.modules.exam;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhikao.common.BusinessException;
import com.zhikao.common.DataScope;
import com.zhikao.common.ExcelHelper;
import com.zhikao.common.ImportResultVO;
import com.zhikao.common.JsonHelper;
import com.zhikao.common.LoginUtil;
import com.zhikao.common.PageResult;
import com.zhikao.common.QuestionTypeEnum;
import com.zhikao.entity.Question;
import com.zhikao.entity.Subject;
import com.zhikao.mapper.QuestionMapper;
import com.zhikao.mapper.SubjectMapper;
import com.zhikao.modules.exam.dto.QuestionQueryDTO;
import com.zhikao.modules.exam.dto.QuestionSaveDTO;
import com.zhikao.modules.exam.vo.QuestionSimilarVO;
import com.zhikao.modules.exam.vo.QuestionVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 题库服务实现。
 */
@Service
public class QuestionServiceImpl implements QuestionService {

    /** 未填分值时的默认分值 */
    private static final BigDecimal DEFAULT_SCORE = new BigDecimal("5.00");

    /** 未填难度时的默认难度 */
    private static final int DEFAULT_DIFFICULTY = 3;

    /** 导入模板中「选项」占用的列数（A-D） */
    private static final int OPTION_COLUMN_COUNT = 4;

    /** 题干最短长度：短于这个长度基本不构成一道题 */
    private static final int MIN_CONTENT_LENGTH = 2;

    /** 查重时的题干最短长度，太短不做字面比对 */
    private static final int MIN_SIMILAR_LENGTH = 6;

    /** 判定为「疑似重复」的重合度阈值 */
    private static final double SIMILAR_THRESHOLD = 0.6;

    /** 单次查重扫描的候选题数量上限（同科目同题型的最近题目） */
    private static final int SIMILAR_SCAN_LIMIT = 500;

    /** 返回给前端的相似题条数上限，避免一屏全是警告 */
    private static final int SIMILAR_RESULT_LIMIT = 3;

    private final com.zhikao.modules.knowledge.KnowledgeBindings knowledge;
    private final QuestionMapper questionMapper;
    private final org.springframework.transaction.support.TransactionTemplate transactions;
    private final SubjectMapper subjectMapper;
    private final DataScope dataScope;
    private final com.zhikao.modules.file.FileBlobStore fileBlobs;

    public QuestionServiceImpl(QuestionMapper questionMapper, SubjectMapper subjectMapper,
                               DataScope dataScope,com.zhikao.modules.knowledge.KnowledgeBindings knowledge,org.springframework.transaction.PlatformTransactionManager tx,com.zhikao.modules.file.FileBlobStore fileBlobs) {
        this.fileBlobs=fileBlobs;
        this.transactions=new org.springframework.transaction.support.TransactionTemplate(tx);
        this.knowledge=knowledge;
        this.questionMapper = questionMapper;
        this.subjectMapper = subjectMapper;
        this.dataScope = dataScope;
    }

    @Override
    public PageResult<QuestionVO> page(QuestionQueryDTO query) {
        LambdaQueryWrapper<Question> wrapper = Wrappers.<Question>lambdaQuery()
                .eq(query.getSubjectId() != null, Question::getSubjectId, query.getSubjectId())
                .eq(query.getType() != null, Question::getType, query.getType())
                .eq(query.getDifficulty() != null, Question::getDifficulty, query.getDifficulty())
                .and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(Question::getContent, query.getKeyword())
                        .or().like(Question::getKnowledgePoint, query.getKeyword()))
                .orderByDesc(Question::getId);

        Page<Question> page = questionMapper.selectPage(
                new Page<>(query.safePageNum(), query.safePageSize()), wrapper);
        Map<Long, String> subjectNames = subjectNames();
        return PageResult.of(page, question -> toVO(question, subjectNames));
    }

    @Override
    public QuestionVO detail(Long id) {
        Question question = questionMapper.selectById(id);
        if (question == null) {
            throw new BusinessException("题目不存在");
        }
        return toVO(question, subjectNames());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(QuestionSaveDTO dto) {
        if (dto.getId() == null) {
            insertNew(dto);
            return;
        }
        // 修改分支：先做与新增同样的守卫与校验，再落更新
        if (subjectMapper.selectById(dto.getSubjectId()) == null) {
            throw new BusinessException("科目不存在，请先选择有效科目");
        }
        validate(dto);
        Question entity = buildEntity(dto);
        // 水平越权校验：教师只能改自己创建的题目（管理员不受限）
        Question exists = questionMapper.selectById(dto.getId());
        if (exists == null) {
            throw new BusinessException("题目不存在");
        }
        dataScope.requireOwner(exists.getCreateBy(), "题目");
        fileBlobs.lockReferences(entity.getContent(),entity.getOptions(),entity.getAnswer(),entity.getAnalysis());
        questionMapper.updateById(entity);
        knowledge.bind(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveReturningId(QuestionSaveDTO dto) {
        // 外部链路（爬虫清洗入库）恒为新增：强制清空 id，避免误走更新分支覆盖既有题
        dto.setId(null);
        return insertNew(dto);
    }

    /**
     * 新增题目的唯一落库路径：单题 save、批量 saveBatch、Excel 导入、爬虫 promote 都经这里，
     * 保证「校验强度一致、不存在专用旁路通道」。返回自增主键供回填关联。
     */
    private Long insertNew(QuestionSaveDTO dto) {
        return transactions.execute(tx->insertNewInTransaction(dto));
    }
    private Long insertNewInTransaction(QuestionSaveDTO dto) {
        // 显式守卫不能省：单题新增走 @Valid 能拦住 subjectId 为空，但**批量通道的参数是 List，
        // @Valid 不会级联到元素**，空 subjectId 会一路走到 selectById(null) 抛框架异常，
        // 前端只能看到「系统异常」。所有写入共用这一条路径，守卫就放在这一条路径上。
        if (dto.getSubjectId() == null) {
            throw new BusinessException("请选择科目");
        }
        if (subjectMapper.selectById(dto.getSubjectId()) == null) {
            throw new BusinessException("科目不存在，请先选择有效科目");
        }
        validate(dto);

        Question entity = buildEntity(dto);
        entity.setCreateBy(LoginUtil.username());
        entity.setCreateTime(LocalDateTime.now());
        fileBlobs.lockReferences(entity.getContent(),entity.getOptions(),entity.getAnswer(),entity.getAnalysis());
        questionMapper.insert(entity);
        knowledge.bind(entity);
        return entity.getId();
    }

    /** 由 DTO 组装实体（不含 create/update 审计字段，由调用方按新增/更新分别补齐） */
    private Question buildEntity(QuestionSaveDTO dto) {
        return Question.builder()
                .id(dto.getId())
                .subjectId(dto.getSubjectId())
                .type(dto.getType())
                .content(dto.getContent().trim())
                // 选项以 JSON 字符串落库：只保留有内容的选项
                .options(JsonHelper.toJson(validOptions(dto.getOptions())))
                .answer(dto.getAnswer().trim())
                .analysis(dto.getAnalysis())
                .difficulty(dto.getDifficulty() == null ? DEFAULT_DIFFICULTY : dto.getDifficulty())
                .knowledgePoint(dto.getKnowledgePoint())
                .score(dto.getScore() == null ? DEFAULT_SCORE : dto.getScore())
                .updateTime(LocalDateTime.now())
                .build();
    }

    @Override
    public ImportResultVO saveBatch(List<QuestionSaveDTO> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("请至少选择一道题目");
        }
        ImportResultVO result = new ImportResultVO();
        // 逐条独立处理：一条不合格不影响其他条，失败原因带回前端
        for (int i = 0; i < items.size(); i++) {
            QuestionSaveDTO dto = items.get(i);
            String label = "第 " + (i + 1) + " 道";
            try {
                // 复用单题新增：同一套校验、同一条写入路径，不存在「批量通道绕过校验」
                dto.setId(null);
                save(dto);
                result.addSuccess();
            } catch (Exception e) {
                result.addError(label + "：" + e.getMessage());
            }
        }
        return result;
    }

    @Override
    public void remove(Long id) {
        Question exists = questionMapper.selectById(id);
        if (exists == null) {
            throw new BusinessException("题目不存在");
        }
        // 水平越权校验：删除同样受归属限制
        dataScope.requireOwner(exists.getCreateBy(), "题目");
        // 说明：已进入试卷快照的题目不受影响（快照已复制内容），此处直接删除题库题目
        questionMapper.deleteById(id);
    }

    @Override
    public ImportResultVO importQuestions(Long subjectId, MultipartFile file) {
        return importQuestions(subjectId, file, false);
    }

    @Override
    public ImportResultVO importQuestions(Long subjectId, MultipartFile file, boolean preview) {
        if (subjectId == null || subjectMapper.selectById(subjectId) == null) {
            throw new BusinessException("请先选择导入到哪个科目");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要导入的 Excel 文件");
        }
        List<List<String>> rows = ExcelHelper.read(file);
        if (rows.size() <= 1) {
            throw new BusinessException("Excel 中没有数据行（第 1 行应为表头）");
        }

        ImportResultVO result = new ImportResultVO();
        result.setPreview(preview);
        // 每行独立处理：单行失败只记录原因，不影响其他行
        for (int i = 1; i < rows.size(); i++) {
            int lineNo = i + 1; // 含表头的真实行号，便于用户定位
            try {
                QuestionSaveDTO dto = parseRow(subjectId, rows.get(i));
                if (preview) {
                    // 预校验复用入库前的同一套校验，保证「预览说得通 == 入库就能进」
                    validate(dto);
                    result.addPreview(summarize(lineNo, dto));
                } else {
                    save(dto);
                }
                result.addSuccess();
            } catch (Exception e) {
                result.addError("第 " + lineNo + " 行：" + e.getMessage());
            }
        }
        return result;
    }

    /** 预校验回显用的单行摘要：题型 + 题干前若干字 + 答案，足够用户判断是不是自己填的那一行 */
    private String summarize(int lineNo, QuestionSaveDTO dto) {
        String content = dto.getContent() == null ? "" : dto.getContent().replaceAll("\\s+", " ").trim();
        if (content.length() > 40) {
            content = content.substring(0, 40) + "…";
        }
        return "第 " + lineNo + " 行 [" + QuestionTypeEnum.labelOf(dto.getType()) + "] " + content
                + " ｜答案：" + dto.getAnswer();
    }

    @Override
    public byte[] importTemplate() {
        return ExcelHelper.write(
                "题目导入",
                List.of("题型", "题干", "选项A", "选项B", "选项C", "选项D", "正确答案", "难度", "知识点", "分值", "解析"),
                List.of(
                        List.of("单选", "Java 中用于声明常量的关键字是？", "final", "static", "const", "var",
                                "A", "2", "Java 基础", "5", "final 修饰的变量不可再次赋值。"),
                        List.of("多选", "下列属于 JVM 运行时数据区的有？", "堆", "虚拟机栈", "方法区", "程序计数器",
                                "ABC", "3", "JVM", "6", "堆、虚拟机栈、方法区均属于 JVM 运行时数据区。"),
                        List.of("判断", "Java 类支持多继承。", "", "", "", "",
                                "错", "1", "Java 基础", "3", "Java 类只能单继承，接口可多实现。"),
                        List.of("填空", "HTTP 协议默认端口是 ____。", "", "", "", "",
                                "80", "1", "计算机网络", "4", "HTTP 默认端口 80，HTTPS 为 443。"),
                        List.of("简答", "简述缓存穿透的常见解决办法。", "", "", "", "",
                                "布隆过滤器；空值缓存", "4", "Redis", "8", "参考答案，交卷后由教师批阅。")));
    }

    // ==================== 查重 ====================

    @Override
    public List<QuestionSimilarVO> findSimilar(QuestionSaveDTO dto) {
        String target = normalize(dto == null ? null : dto.getContent());
        if (target.length() < MIN_SIMILAR_LENGTH) {
            return List.of(); // 题干太短，字面比对没有意义（"对吗？" 这种会命中一大片）
        }
        // 只在同科目内比对：跨科目同名的题（比如都有「什么是死锁」）不该被判为重复
        LambdaQueryWrapper<Question> wrapper = Wrappers.<Question>lambdaQuery()
                .eq(dto.getSubjectId() != null, Question::getSubjectId, dto.getSubjectId())
                .eq(dto.getType() != null, Question::getType, dto.getType())
                .orderByDesc(Question::getId)
                .last("limit " + SIMILAR_SCAN_LIMIT);

        List<QuestionSimilarVO> result = new ArrayList<>();
        for (Question candidate : questionMapper.selectList(wrapper)) {
            double score = similarity(target, normalize(candidate.getContent()));
            if (score < SIMILAR_THRESHOLD) {
                continue;
            }
            QuestionSimilarVO vo = new QuestionSimilarVO();
            vo.setId(candidate.getId());
            vo.setContent(candidate.getContent());
            vo.setTypeName(QuestionTypeEnum.labelOf(candidate.getType()));
            vo.setKnowledgePoint(candidate.getKnowledgePoint());
            vo.setSimilarity(round(score));
            vo.setHint("题面重合度 " + Math.round(score * 100) + "%");
            result.add(vo);
        }
        result.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));
        return result.size() > SIMILAR_RESULT_LIMIT ? result.subList(0, SIMILAR_RESULT_LIMIT) : result;
    }

    /**
     * 字面重合度：二元组（bigram）Jaccard。
     *
     * <p>为什么不用向量/语义模型：查重要的其实不是「语义相近」——
     * 两道都考「进程调度」的题语义很近，但它们不该被判为重复。
     * 要找的是「换了个说法的同一道题」，这恰好是字面重合度最擅长的，
     * 而且它不需要引入向量库、不需要额外的一次模型调用，结果还可解释（能给出重合百分比）。
     */
    private double similarity(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        Set<String> setA = bigrams(a);
        Set<String> setB = bigrams(b);
        if (setA.isEmpty() || setB.isEmpty()) {
            return 0;
        }
        int intersection = 0;
        for (String gram : setA) {
            if (setB.contains(gram)) {
                intersection++;
            }
        }
        int union = setA.size() + setB.size() - intersection;
        return union == 0 ? 0 : (double) intersection / union;
    }

    private Set<String> bigrams(String text) {
        Set<String> grams = new HashSet<>();
        for (int i = 0; i + 2 <= text.length(); i++) {
            grams.add(text.substring(i, i + 2));
        }
        return grams;
    }

    /** 归一化：去掉空白与标点，避免「空格/全角」造成的假差异 */
    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("[\\s\\p{Punct}\\p{IsPunctuation}]", "");
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    // ==================== 内部方法 ====================

    /**
     * 校验题目内容。
     * 题型相关的硬约束（选项、答案取值）在此集中处理，避免脏数据进入题库。
     */
    private void validate(QuestionSaveDTO dto) {
        QuestionTypeEnum type = QuestionTypeEnum.of(dto.getType());
        if (type == null) {
            throw new BusinessException("题型不合法");
        }
        if (!StringUtils.hasText(dto.getContent()) || dto.getContent().trim().length() < MIN_CONTENT_LENGTH) {
            throw new BusinessException("题干不能为空且不应少于 " + MIN_CONTENT_LENGTH + " 个字");
        }
        if (!StringUtils.hasText(dto.getAnswer())) {
            throw new BusinessException("标准答案不能为空");
        }
        List<String> keys = new ArrayList<>(validOptions(dto.getOptions()).keySet());

        if (type == QuestionTypeEnum.SINGLE) {
            if (keys.size() < 2) {
                throw new BusinessException("单选题至少需要 2 个选项");
            }
            if (!keys.contains(dto.getAnswer().trim().toUpperCase())) {
                throw new BusinessException("单选题答案必须是某个选项（如 A）");
            }
        } else if (type == QuestionTypeEnum.MULTIPLE) {
            if (keys.size() < 2) {
                throw new BusinessException("多选题至少需要 2 个选项");
            }
            String answer = dto.getAnswer().replaceAll("[\\s,，]+", "").toUpperCase();
            if (answer.isEmpty()) throw new BusinessException("多选题答案不能为空");
            dto.setAnswer(answer.chars().distinct().sorted().collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString());
            for (char c : answer.toCharArray()) {
                if (!keys.contains(String.valueOf(c))) {
                    throw new BusinessException("多选题答案只能是选项字母的组合（如 ABD）");
                }
            }
        }
        if (type == QuestionTypeEnum.SINGLE) dto.setAnswer(dto.getAnswer().trim().toUpperCase());
        if (type == QuestionTypeEnum.JUDGE && !com.zhikao.common.AnswerJudge.correct(3,dto.getAnswer(),"对")
                && !com.zhikao.common.AnswerJudge.correct(3,dto.getAnswer(),"错")) throw new BusinessException("判断题标准答案必须为正确或错误");

        if (dto.getScore() != null && dto.getScore().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("分值必须大于 0");
        }
    }

    /** 过滤掉空选项，保证选项 Map 只含真实内容 */
    private Map<String, String> validOptions(Map<String, String> options) {
        Map<String, String> result = new LinkedHashMap<>();
        if (options == null) {
            return result;
        }
        options.forEach((key, value) -> {
            if (StringUtils.hasText(key) && StringUtils.hasText(value)) {
                result.put(key.trim().toUpperCase(), value.trim());
            }
        });
        return result;
    }

    /** 查出全部科目名称映射，供列表回填科目名，避免 N+1 查询 */
    private Map<Long, String> subjectNames() {
        return subjectMapper.selectList(null).stream()
                .collect(Collectors.toMap(Subject::getId, Subject::getSubjectName));
    }

    private QuestionVO toVO(Question question, Map<Long, String> subjectNames) {
        QuestionTypeEnum type = QuestionTypeEnum.of(question.getType());
        QuestionVO vo = new QuestionVO();
        vo.setId(question.getId());
        vo.setSubjectId(question.getSubjectId());
        vo.setSubjectName(subjectNames.get(question.getSubjectId()));
        vo.setType(question.getType());
        vo.setTypeName(QuestionTypeEnum.labelOf(question.getType()));
        vo.setObjective(type != null && type.isObjective());
        vo.setContent(question.getContent());
        vo.setOptions(JsonHelper.toOptionMap(question.getOptions()));
        vo.setAnswer(question.getAnswer());
        vo.setAnalysis(question.getAnalysis());
        vo.setDifficulty(question.getDifficulty());
        vo.setKnowledgePoint(question.getKnowledgePoint());
        vo.setScore(question.getScore());
        vo.setCreateBy(question.getCreateBy());
        vo.setCreateTime(question.getCreateTime());
        return vo;
    }

    /**
     * 解析 Excel 一行。
     * 列顺序：题型 | 题干 | 选项A | 选项B | 选项C | 选项D | 正确答案 | 难度 | 知识点 | 分值 | 解析
     */
    private QuestionSaveDTO parseRow(Long subjectId, List<String> row) {
        QuestionSaveDTO dto = new QuestionSaveDTO();
        dto.setSubjectId(subjectId);

        String typeText = cell(row, 0);
        Integer type = QuestionTypeEnum.parse(typeText);
        if (type == null) {
            throw new BusinessException("题型无法识别（应为 单选/多选/判断/填空/简答）：" + typeText);
        }
        dto.setType(type);
        dto.setContent(cell(row, 1));

        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 0; i < OPTION_COLUMN_COUNT; i++) {
            String value = cell(row, 2 + i);
            if (StringUtils.hasText(value)) {
                options.put(String.valueOf((char) ('A' + i)), value);
            }
        }
        dto.setOptions(options);
        dto.setAnswer(cell(row, 6));

        String difficulty = cell(row, 7);
        if (StringUtils.hasText(difficulty)) {
            try {
                dto.setDifficulty(Integer.valueOf(difficulty));
            } catch (NumberFormatException e) {
                throw new BusinessException("难度必须为 1-5 的数字：" + difficulty);
            }
        }
        dto.setKnowledgePoint(cell(row, 8));

        String score = cell(row, 9);
        if (StringUtils.hasText(score)) {
            try {
                dto.setScore(new BigDecimal(score));
            } catch (NumberFormatException e) {
                throw new BusinessException("分值必须为数字：" + score);
            }
        }
        dto.setAnalysis(cell(row, 10));
        return dto;
    }

    /** 取第 index 列内容，越界返回空串 */
    private String cell(List<String> row, int index) {
        return index < row.size() ? row.get(index).trim() : "";
    }
}
