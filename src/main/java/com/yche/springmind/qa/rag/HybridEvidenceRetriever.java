package com.yche.springmind.qa.rag;

import com.yche.springmind.common.exception.BusinessException;
import com.yche.springmind.ingestion.mapper.DocumentChunkMapper;
import com.yche.springmind.ingestion.model.entity.DocumentChunkEntity;
import com.yche.springmind.qa.model.EvidenceLevel;
import com.yche.springmind.qa.model.QueryPlanResult;
import com.yche.springmind.qa.service.QueryPlanningService;
import com.yche.springmind.retrieval.elasticsearch.ElasticsearchChunkIndexService;
import com.yche.springmind.retrieval.vectorstore.PgVectorRetrievalAdapter;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 组合执行向量与关键词召回，使用 RRF 融合、去重和相邻切片扩展生成证据。
 *
 * <p>位于 RAG 检索链路：把问题转换为受知识组权限约束的证据集合，供回答生成阶段使用。</p>
 */
@Service
public class HybridEvidenceRetriever implements EvidenceRetriever {

    private static final int DEFAULT_NEIGHBOR_WINDOW = 1;
    private static final int CHANNEL_TOP_K = 50;
    private static final int RRF_K = 60;

    private final PgVectorRetrievalAdapter vectorRetrievalAdapter;
    private final ElasticsearchChunkIndexService elasticsearchChunkIndexService;
    private final DocumentChunkMapper documentChunkMapper;
    private final QueryPlanningService queryPlanningService;
    private final int neighborWindow;

    /**
     * 创建并初始化 {@link HybridEvidenceRetriever}，保存该组件运行所需的依赖与配置。
     * <p>
     * 实现要点：写入或查询 pgvector 向量索引；维护或查询 Elasticsearch 关键词索引；根据问题生成检索计划。
     *
     * @param vectorRetrievalAdapter 方法参数 {@code vectorRetrievalAdapter}
     * @param elasticsearchChunkIndexService 方法参数 {@code elasticsearchChunkIndexService}
     * @param documentChunkMapper 方法参数 {@code documentChunkMapper}
     * @param queryPlanningService 方法参数 {@code queryPlanningService}
     */
    @Autowired
    public HybridEvidenceRetriever(
            PgVectorRetrievalAdapter vectorRetrievalAdapter,
            ElasticsearchChunkIndexService elasticsearchChunkIndexService,
            DocumentChunkMapper documentChunkMapper,
            QueryPlanningService queryPlanningService
    ) {
        this(
                vectorRetrievalAdapter,
                elasticsearchChunkIndexService,
                documentChunkMapper,
                queryPlanningService,
                DEFAULT_NEIGHBOR_WINDOW
        );
    }

    /**
     * 创建并初始化 {@link HybridEvidenceRetriever}，保存该组件运行所需的依赖与配置。
     * <p>
     * 实现要点：写入或查询 pgvector 向量索引；维护或查询 Elasticsearch 关键词索引；根据问题生成检索计划。
     *
     * @param vectorRetrievalAdapter 方法参数 {@code vectorRetrievalAdapter}
     * @param elasticsearchChunkIndexService 方法参数 {@code elasticsearchChunkIndexService}
     * @param documentChunkMapper 方法参数 {@code documentChunkMapper}
     * @param queryPlanningService 方法参数 {@code queryPlanningService}
     * @param neighborWindow 方法参数 {@code neighborWindow}
     */
    public HybridEvidenceRetriever(
            PgVectorRetrievalAdapter vectorRetrievalAdapter,
            ElasticsearchChunkIndexService elasticsearchChunkIndexService,
            DocumentChunkMapper documentChunkMapper,
            QueryPlanningService queryPlanningService,
            int neighborWindow
    ) {
        this.vectorRetrievalAdapter = vectorRetrievalAdapter;
        this.elasticsearchChunkIndexService = elasticsearchChunkIndexService;
        this.documentChunkMapper = documentChunkMapper;
        this.queryPlanningService = queryPlanningService;
        this.neighborWindow = Math.max(0, neighborWindow);
    }

    /**
     * 依据查询条件检索与当前群组相关的证据片段。
     *
     * @param groupId 群组唯一标识
     * @param question 用户提交的自然语言问题
     * @param topK 最多返回的候选结果数量
     * @return 方法执行结果，具体结构由返回类型 {@code RetrievedEvidenceBundle} 表示
     */
    @Override
    public RetrievedEvidenceBundle retrieve(Long groupId, String question, int topK) {
        return retrieve(null, groupId, question, topK);
    }

    /**
     * 依据查询条件检索与当前群组相关的证据片段。
     * <p>
     * 实现要点：先校验输入、状态或业务边界；根据问题生成检索计划。
     *
     * @param userId 用户唯一标识
     * @param groupId 群组唯一标识
     * @param question 用户提交的自然语言问题
     * @param topK 最多返回的候选结果数量
     * @return 方法执行结果，具体结构由返回类型 {@code RetrievedEvidenceBundle} 表示
     */
    @Override
    public RetrievedEvidenceBundle retrieve(Long userId, Long groupId, String question, int topK) {
        Long validGroupId = requirePositiveGroupId(groupId);
        String normalizedQuestion = requireQuestion(question);
        int validTopK = topK > 0 ? topK : EvidenceRetriever.DEFAULT_TOP_K;
        QueryPlanResult queryPlan = queryPlanningService.plan(userId, normalizedQuestion);
        Map<Long, RetrievalCandidate> candidates = new LinkedHashMap<>();

        for (String plannedQuery : queryPlan.queries()) {
            mergeVectorHits(candidates, validGroupId, plannedQuery);
            mergeKeywordHits(candidates, validGroupId, plannedQuery);
        }

        if (candidates.isEmpty()) {
            return RetrievedEvidenceBundle.empty();
        }

        List<RetrievalCandidate> rankedCandidates = candidates.values().stream()
                .sorted(Comparator
                        .comparingDouble(RetrievalCandidate::rankingScore).reversed()
                        .thenComparing(RetrievalCandidate::chunkId))
                .limit(validTopK)
                .toList();
        List<RetrievalCluster> rankedClusters = buildClusters(rankedCandidates);

        List<Long> chunkIds = rankedCandidates.stream().map(RetrievalCandidate::chunkId).toList();
        Map<Long, Map<String, Object>> rowByChunkId = indexRows(
                documentChunkMapper.selectQaReadyChunksByIds(validGroupId, chunkIds)
        );
        Map<Long, List<DocumentChunkEntity>> chunkWindowCache = new LinkedHashMap<>();
        List<Document> documents = new ArrayList<>();
        int evidenceIndex = 1;
        for (RetrievalCluster cluster : rankedClusters) {
            Map<String, Object> row = rowByChunkId.get(cluster.primaryChunkId());
            if (row == null) {
                continue;
            }
            Document document = toDocument("E" + evidenceIndex, row, cluster, chunkWindowCache);
            if (document == null) {
                continue;
            }
            documents.add(document);
            evidenceIndex++;
        }
        if (documents.isEmpty()) {
            return RetrievedEvidenceBundle.empty();
        }
        EvidenceLevel evidenceLevel = evaluateEvidenceLevel(documents);
        return new RetrievedEvidenceBundle(documents, evidenceLevel, buildEvidenceGuidance(evidenceLevel));
    }

    /**
     * 执行 {@code mergeVectorHits} 对应的业务步骤。
     * <p>
     * 实现要点：写入或查询 pgvector 向量索引。
     *
     * @param candidates 方法参数 {@code candidates}
     * @param groupId 群组唯一标识
     * @param query 用于检索或筛选的查询条件
     */
    private void mergeVectorHits(
            Map<Long, RetrievalCandidate> candidates,
            Long groupId,
            String query
    ) {
        List<PgVectorRetrievalAdapter.VectorHit> vectorHits = vectorRetrievalAdapter.search(groupId, query, CHANNEL_TOP_K);
        for (int index = 0; index < vectorHits.size(); index++) {
            PgVectorRetrievalAdapter.VectorHit hit = vectorHits.get(index);
            RetrievalCandidate candidate = candidates.computeIfAbsent(
                    hit.chunkId(),
                    ignored -> RetrievalCandidate.fromVectorHit(hit)
            );
            candidate.mergeVectorHit(hit, index + 1);
        }
    }

    /**
     * 执行 {@code mergeKeywordHits} 对应的业务步骤。
     * <p>
     * 实现要点：维护或查询 Elasticsearch 关键词索引。
     *
     * @param candidates 方法参数 {@code candidates}
     * @param groupId 群组唯一标识
     * @param query 用于检索或筛选的查询条件
     */
    private void mergeKeywordHits(
            Map<Long, RetrievalCandidate> candidates,
            Long groupId,
            String query
    ) {
        List<ElasticsearchChunkIndexService.KeywordHit> keywordHits =
                elasticsearchChunkIndexService.search(groupId, query, CHANNEL_TOP_K);
        for (int index = 0; index < keywordHits.size(); index++) {
            ElasticsearchChunkIndexService.KeywordHit hit = keywordHits.get(index);
            RetrievalCandidate candidate = candidates.computeIfAbsent(
                    hit.chunkId(),
                    ignored -> RetrievalCandidate.fromKeywordHit(hit)
            );
            candidate.mergeKeywordHit(hit, index + 1);
        }
    }

    /**
     * 执行 {@code indexRows} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param rows 方法参数 {@code rows}
     * @return 方法执行结果，具体结构由返回类型 {@code Map&lt;Long, Map&lt;String, Object&gt;&gt;} 表示
     */
    private Map<Long, Map<String, Object>> indexRows(List<Map<String, Object>> rows) {
        Map<Long, Map<String, Object>> rowByChunkId = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            rowByChunkId.put(requireLong(getValue(row, "chunkId"), "chunkId"), row);
        }
        return rowByChunkId;
    }

    /**
     * 执行 {@code toDocument} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param evidenceId 证据唯一标识
     * @param row 方法参数 {@code row}
     * @param cluster 方法参数 {@code cluster}
     * @param chunkWindowCache 方法参数 {@code chunkWindowCache}
     * @return 方法执行结果，具体结构由返回类型 {@code Document} 表示
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private Document toDocument(
            String evidenceId,
            Map<String, Object> row,
            RetrievalCluster cluster,
            Map<Long, List<DocumentChunkEntity>> chunkWindowCache
    ) {
        Long documentId = requireLong(getValue(row, "documentId"), "documentId");
        Integer chunkIndex = requireInteger(getValue(row, "chunkIndex"), "chunkIndex");
        if (!documentId.equals(cluster.documentId()) || !chunkIndex.equals(cluster.primaryChunkIndex())) {
            throw new BusinessException("检索结果与文档切片不一致");
        }
        Long chunkId = requireLong(getValue(row, "chunkId"), "chunkId");
        String fileName = requireText(getValue(row, "fileName"), "fileName");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("evidenceId", evidenceId);
        metadata.put("groupId", requireLong(getValue(row, "groupId"), "groupId"));
        metadata.put("documentId", documentId);
        metadata.put("chunkId", chunkId);
        metadata.put("chunkIndex", chunkIndex);
        metadata.put("primaryChunkId", cluster.primaryChunkId());
        metadata.put("primaryChunkIndex", cluster.primaryChunkIndex());
        metadata.put("startChunkIndex", cluster.expandedStartChunkIndex(neighborWindow));
        metadata.put("endChunkIndex", cluster.expandedEndChunkIndex(neighborWindow));
        metadata.put("fileName", fileName);
        metadata.put("score", cluster.rankingScore());
        metadata.put("retrievalSource", cluster.source());
        metadata.put("vectorScore", cluster.vectorScore());
        metadata.put("keywordScore", cluster.keywordScore());
        metadata.put("hybridScore", cluster.rankingScore());
        String evidenceText = buildEvidenceWindow(row, cluster, chunkWindowCache);
        if (!StringUtils.hasText(evidenceText)) {
            return null;
        }
        return Document.builder()
                .id(evidenceId)
                .text(evidenceText)
                .metadata(metadata)
                .build();
    }

    /**
     * 执行 {@code buildEvidenceWindow} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param row 方法参数 {@code row}
     * @param cluster 方法参数 {@code cluster}
     * @param chunkWindowCache 方法参数 {@code chunkWindowCache}
     * @return 处理后得到的字符串结果
     */
    private String buildEvidenceWindow(
            Map<String, Object> row,
            RetrievalCluster cluster,
            Map<Long, List<DocumentChunkEntity>> chunkWindowCache
    ) {
        Long groupId = requireLong(getValue(row, "groupId"), "groupId");
        Long documentId = requireLong(getValue(row, "documentId"), "documentId");
        String fileName = requireText(getValue(row, "fileName"), "fileName");
        List<DocumentChunkEntity> chunks = chunkWindowCache.computeIfAbsent(
                documentId,
                ignored -> documentChunkMapper.selectReadyActiveChunksByDocumentId(groupId, documentId)
        );
        if (chunks.isEmpty()) {
            return null;
        }
        int startIndex = cluster.expandedStartChunkIndex(neighborWindow);
        int endIndex = cluster.expandedEndChunkIndex(neighborWindow);
        StringBuilder builder = new StringBuilder();
        for (DocumentChunkEntity chunk : chunks) {
            if (chunk.getChunkIndex() != null
                    && chunk.getChunkIndex() >= startIndex
                    && chunk.getChunkIndex() <= endIndex
                    && StringUtils.hasText(chunk.getChunkText())) {
                if (!builder.isEmpty()) {
                    builder.append("\n");
                }
                builder.append(chunk.getChunkText().trim());
            }
        }
        if (builder.isEmpty()) {
            return null;
        }
        return "文件名：" + fileName + "\n" + builder;
    }

    /**
     * 执行 {@code buildClusters} 对应的业务步骤。
     *
     * @param rankedCandidates 方法参数 {@code rankedCandidates}
     * @return 符合条件的结果集合；无结果时返回空集合
     */
    private List<RetrievalCluster> buildClusters(List<RetrievalCandidate> rankedCandidates) {
        Map<Long, List<RetrievalCandidate>> candidatesByDocumentId = new LinkedHashMap<>();
        for (RetrievalCandidate candidate : rankedCandidates) {
            candidatesByDocumentId.computeIfAbsent(candidate.documentId(), ignored -> new ArrayList<>()).add(candidate);
        }
        List<RetrievalCluster> clusters = new ArrayList<>();
        for (List<RetrievalCandidate> sameDocumentCandidates : candidatesByDocumentId.values()) {
            List<RetrievalCandidate> sortedByChunkIndex = sameDocumentCandidates.stream()
                    .sorted(Comparator.comparing(RetrievalCandidate::chunkIndex))
                    .toList();
            RetrievalCluster currentCluster = null;
            for (RetrievalCandidate candidate : sortedByChunkIndex) {
                if (currentCluster == null || !currentCluster.isContinuousWith(candidate)) {
                    currentCluster = new RetrievalCluster(candidate);
                    clusters.add(currentCluster);
                    continue;
                }
                currentCluster.add(candidate);
            }
        }
        return clusters.stream()
                .sorted(Comparator
                        .comparingDouble(RetrievalCluster::rankingScore).reversed()
                        .thenComparing(RetrievalCluster::primaryChunkId))
                .toList();
    }

    /**
     * 综合命中数量、检索来源和相关度评估证据可信等级。
     *
     * @param documents 方法参数 {@code documents}
     * @return 方法执行结果，具体结构由返回类型 {@code EvidenceLevel} 表示
     */
    private EvidenceLevel evaluateEvidenceLevel(List<Document> documents) {
        if (documents.isEmpty()) {
            return EvidenceLevel.NONE;
        }
        boolean hasBothSource = documents.stream()
                .map(document -> document.getMetadata().get("retrievalSource"))
                .anyMatch("BOTH"::equals);
        boolean hasVectorEvidence = documents.stream()
                .map(document -> document.getMetadata().get("retrievalSource"))
                .anyMatch(source -> "VECTOR".equals(source) || "BOTH".equals(source));
        double topScore = documents.stream()
                .map(document -> document.getMetadata().get("hybridScore"))
                .filter(Double.class::isInstance)
                .map(Double.class::cast)
                .max(Double::compareTo)
                .orElse(0D);
        if (documents.size() >= 2 && (hasBothSource || (hasVectorEvidence && topScore >= 0.95D))) {
            return EvidenceLevel.SUFFICIENT;
        }
        if (hasBothSource || documents.size() >= 2) {
            return EvidenceLevel.PARTIAL;
        }
        return EvidenceLevel.WEAK;
    }

    /**
     * 执行 {@code buildEvidenceGuidance} 对应的业务步骤。
     *
     * @param evidenceLevel 方法参数 {@code evidenceLevel}
     * @return 处理后得到的字符串结果
     */
    private String buildEvidenceGuidance(EvidenceLevel evidenceLevel) {
        return switch (evidenceLevel) {
            case NONE -> "当前没有可用证据，必须直接拒答。";
            case WEAK -> "当前证据相关性有限，只能谨慎回答，必须明确说明依据有限，不能给出确定性结论。";
            case PARTIAL -> "当前证据只覆盖部分问题，只能回答证据明确支持的部分，未覆盖部分必须明确说明不足。";
            case SUFFICIENT -> "当前证据较充分，可以正常回答，但仍然不得超出证据进行臆测。";
        };
    }

    /**
     * 返回 {@code value} 对应的配置或状态值。
     *
     * @param row 方法参数 {@code row}
     * @param field 方法参数 {@code field}
     * @return 查询得到的Value结果
     */
    private Object getValue(Map<String, Object> row, String field) {
        Object value = row.get(field);
        if (value != null) {
            return value;
        }
        return row.get(field.toLowerCase());
    }

    /**
     * 执行 {@code requirePositiveGroupId} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param groupId 群组唯一标识
     * @return 计算或处理得到的数值结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private Long requirePositiveGroupId(Long groupId) {
        if (groupId == null || groupId <= 0) {
            throw new BusinessException("groupId 非法");
        }
        return groupId;
    }

    /**
     * 执行 {@code requireQuestion} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param question 用户提交的自然语言问题
     * @return 处理后得到的字符串结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private String requireQuestion(String question) {
        if (!StringUtils.hasText(question)) {
            throw new BusinessException("问题不能为空");
        }
        return question.trim();
    }

    /**
     * 执行 {@code requireLong} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param value 方法参数 {@code value}
     * @param field 方法参数 {@code field}
     * @return 计算或处理得到的数值结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private Long requireLong(Object value, String field) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new BusinessException("检索结果缺少字段: " + field);
    }

    /**
     * 执行 {@code requireInteger} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param value 方法参数 {@code value}
     * @param field 方法参数 {@code field}
     * @return 计算或处理得到的数值结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private Integer requireInteger(Object value, String field) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new BusinessException("检索结果缺少字段: " + field);
    }

    /**
     * 执行 {@code requireText} 对应的业务步骤。
     * <p>
     * 实现要点：先校验输入、状态或业务边界。
     *
     * @param value 方法参数 {@code value}
     * @param field 方法参数 {@code field}
     * @return 处理后得到的字符串结果
     * @throws BusinessException 当输入、状态或依赖不满足方法约束时抛出
     */
    private String requireText(Object value, String field) {
        if (value instanceof String text && StringUtils.hasText(text)) {
            return text.trim();
        }
        throw new BusinessException("检索结果缺少字段: " + field);
    }

    /**
     * 汇总同一切片在多路召回中的排名、分数和来源信息。
     *
     * <p>仅服务于 {@code HybridEvidenceRetriever} 的内部算法，用于封装外部调用方不需要感知的状态与行为。</p>
     */
    static final class RetrievalCandidate {

        private final Long documentId;
        private final Long chunkId;
        private final Integer chunkIndex;
        private double vectorScore;
        private double keywordScore;
        private double rankingScore;
        private boolean vectorMatched;
        private boolean keywordMatched;

        /**
         * 创建并初始化 {@link RetrievalCandidate}，保存该组件运行所需的依赖与配置。
         *
         * @param documentId 文档唯一标识
         * @param chunkId 文档切片唯一标识
         * @param chunkIndex 方法参数 {@code chunkIndex}
         */
        private RetrievalCandidate(Long documentId, Long chunkId, Integer chunkIndex) {
            this.documentId = documentId;
            this.chunkId = chunkId;
            this.chunkIndex = chunkIndex;
        }

        /**
         * 执行 {@code fromVectorHit} 对应的业务步骤。
         * <p>
         * 实现要点：写入或查询 pgvector 向量索引。
         *
         * @param hit 当前需要合并的召回结果
         * @return 方法执行结果，具体结构由返回类型 {@code RetrievalCandidate} 表示
         */
        static RetrievalCandidate fromVectorHit(PgVectorRetrievalAdapter.VectorHit hit) {
            return new RetrievalCandidate(hit.documentId(), hit.chunkId(), hit.chunkIndex());
        }

        /**
         * 执行 {@code fromKeywordHit} 对应的业务步骤。
         *
         * @param hit 当前需要合并的召回结果
         * @return 方法执行结果，具体结构由返回类型 {@code RetrievalCandidate} 表示
         */
        static RetrievalCandidate fromKeywordHit(ElasticsearchChunkIndexService.KeywordHit hit) {
            return new RetrievalCandidate(hit.documentId(), hit.chunkId(), hit.chunkIndex());
        }

        /**
         * 把一路向量召回结果合并进候选项，并累加对应的 RRF 排名得分。
         * <p>
         * 实现要点：写入或查询 pgvector 向量索引；执行混合检索与结果融合。
         *
         * @param hit 当前需要合并的召回结果
         * @param rank 当前候选项在单路召回结果中的排名
         */
        void mergeVectorHit(PgVectorRetrievalAdapter.VectorHit hit, int rank) {
            this.vectorMatched = true;
            this.vectorScore = Math.max(this.vectorScore, hit.score());
            this.rankingScore += reciprocalRank(rank);
        }

        /**
         * 把一路关键词召回结果合并进候选项，并累加对应的 RRF 排名得分。
         * <p>
         * 实现要点：先校验输入、状态或业务边界；执行混合检索与结果融合。
         *
         * @param hit 当前需要合并的召回结果
         * @param rank 当前候选项在单路召回结果中的排名
         */
        void mergeKeywordHit(ElasticsearchChunkIndexService.KeywordHit hit, int rank) {
            this.keywordMatched = true;
            this.keywordScore = Math.max(this.keywordScore, hit.normalizedScore());
            this.rankingScore += reciprocalRank(rank);
        }

        /**
         * 执行 {@code documentId} 对应的业务步骤。
         *
         * @return 计算或处理得到的数值结果
         */
        Long documentId() {
            return documentId;
        }

        /**
         * 执行 {@code chunkId} 对应的业务步骤。
         *
         * @return 计算或处理得到的数值结果
         */
        Long chunkId() {
            return chunkId;
        }

        /**
         * 执行 {@code chunkIndex} 对应的业务步骤。
         *
         * @return 计算或处理得到的数值结果
         */
        Integer chunkIndex() {
            return chunkIndex;
        }

        /**
         * 执行 {@code vectorScore} 对应的业务步骤。
         *
         * @return 计算或处理得到的数值结果
         */
        double vectorScore() {
            return vectorScore;
        }

        /**
         * 执行 {@code keywordScore} 对应的业务步骤。
         *
         * @return 计算或处理得到的数值结果
         */
        double keywordScore() {
            return keywordScore;
        }

        /**
         * 执行 {@code rankingScore} 对应的业务步骤。
         *
         * @return 计算或处理得到的数值结果
         */
        double rankingScore() {
            return rankingScore;
        }

        /**
         * 执行 {@code source} 对应的业务步骤。
         *
         * @return 处理后得到的字符串结果
         */
        String source() {
            if (vectorMatched && keywordMatched) {
                return "BOTH";
            }
            return vectorMatched ? "VECTOR" : "KEYWORD";
        }

        /**
         * 根据候选结果的单路排名计算 RRF 倒数排名得分。
         * <p>
         * 实现要点：执行混合检索与结果融合。
         *
         * @param rank 当前候选项在单路召回结果中的排名
         * @return 计算或处理得到的数值结果
         */
        private double reciprocalRank(int rank) {
            return 1D / (RRF_K + Math.max(rank, 1));
        }
    }

    /**
     * 聚合相邻或相关的召回候选，便于扩展上下文并控制证据数量。
     *
     * <p>仅服务于 {@code HybridEvidenceRetriever} 的内部算法，用于封装外部调用方不需要感知的状态与行为。</p>
     */
    static final class RetrievalCluster {

        private final Long documentId;
        private final List<RetrievalCandidate> members = new ArrayList<>();
        private RetrievalCandidate primaryCandidate;
        private int startChunkIndex;
        private int endChunkIndex;
        private double rankingScore;
        private double vectorScore;
        private double keywordScore;
        private boolean hasVectorSource;
        private boolean hasKeywordSource;

        /**
         * 创建并初始化 {@link RetrievalCluster}，保存该组件运行所需的依赖与配置。
         *
         * @param seed 方法参数 {@code seed}
         */
        private RetrievalCluster(RetrievalCandidate seed) {
            this.documentId = seed.documentId();
            this.startChunkIndex = seed.chunkIndex();
            this.endChunkIndex = seed.chunkIndex();
            add(seed);
        }

        /**
         * 判断当前数据是否满足 {@code continuousWith} 条件。
         *
         * @param candidate 方法参数 {@code candidate}
         * @return 满足条件时返回 {@code true}，否则返回 {@code false}
         */
        boolean isContinuousWith(RetrievalCandidate candidate) {
            return documentId.equals(candidate.documentId()) && candidate.chunkIndex() == endChunkIndex + 1;
        }

        /**
         * 执行 {@code add} 对应的业务步骤。
         *
         * @param candidate 方法参数 {@code candidate}
         */
        void add(RetrievalCandidate candidate) {
            members.add(candidate);
            endChunkIndex = Math.max(endChunkIndex, candidate.chunkIndex());
            startChunkIndex = Math.min(startChunkIndex, candidate.chunkIndex());
            rankingScore = Math.max(rankingScore, candidate.rankingScore());
            vectorScore = Math.max(vectorScore, candidate.vectorScore());
            keywordScore = Math.max(keywordScore, candidate.keywordScore());
            hasVectorSource = hasVectorSource || "VECTOR".equals(candidate.source()) || "BOTH".equals(candidate.source());
            hasKeywordSource = hasKeywordSource || "KEYWORD".equals(candidate.source()) || "BOTH".equals(candidate.source());
            if (primaryCandidate == null
                    || candidate.rankingScore() > primaryCandidate.rankingScore()
                    || (candidate.rankingScore() == primaryCandidate.rankingScore()
                    && candidate.chunkIndex() < primaryCandidate.chunkIndex())) {
                primaryCandidate = candidate;
            }
        }

        /**
         * 执行 {@code documentId} 对应的业务步骤。
         *
         * @return 计算或处理得到的数值结果
         */
        Long documentId() {
            return documentId;
        }

        /**
         * 执行 {@code primaryChunkId} 对应的业务步骤。
         *
         * @return 计算或处理得到的数值结果
         */
        Long primaryChunkId() {
            return primaryCandidate.chunkId();
        }

        /**
         * 执行 {@code primaryChunkIndex} 对应的业务步骤。
         *
         * @return 计算或处理得到的数值结果
         */
        Integer primaryChunkIndex() {
            return primaryCandidate.chunkIndex();
        }

        /**
         * 执行 {@code rankingScore} 对应的业务步骤。
         *
         * @return 计算或处理得到的数值结果
         */
        double rankingScore() {
            return rankingScore;
        }

        /**
         * 执行 {@code vectorScore} 对应的业务步骤。
         *
         * @return 计算或处理得到的数值结果
         */
        double vectorScore() {
            return vectorScore;
        }

        /**
         * 执行 {@code keywordScore} 对应的业务步骤。
         *
         * @return 计算或处理得到的数值结果
         */
        double keywordScore() {
            return keywordScore;
        }

        /**
         * 执行 {@code expandedStartChunkIndex} 对应的业务步骤。
         *
         * @param neighborWindow 方法参数 {@code neighborWindow}
         * @return 计算或处理得到的数值结果
         */
        int expandedStartChunkIndex(int neighborWindow) {
            return Math.max(0, startChunkIndex - Math.max(0, neighborWindow));
        }

        /**
         * 执行 {@code expandedEndChunkIndex} 对应的业务步骤。
         *
         * @param neighborWindow 方法参数 {@code neighborWindow}
         * @return 计算或处理得到的数值结果
         */
        int expandedEndChunkIndex(int neighborWindow) {
            return endChunkIndex + Math.max(0, neighborWindow);
        }

        /**
         * 执行 {@code source} 对应的业务步骤。
         *
         * @return 处理后得到的字符串结果
         */
        String source() {
            if (hasVectorSource && hasKeywordSource) {
                return "BOTH";
            }
            return hasVectorSource ? "VECTOR" : "KEYWORD";
        }
    }
}
