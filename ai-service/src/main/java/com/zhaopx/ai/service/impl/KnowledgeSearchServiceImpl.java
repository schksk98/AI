package com.zhaopx.ai.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.KnnSearch;
import co.elastic.clients.elasticsearch._types.Rank;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.zhaopx.ai.bean.req.KnowledgeSearchRequest;
import com.zhaopx.ai.bean.res.KnowledgeSearchHit;
import com.zhaopx.ai.bean.res.KnowledgeSearchResponse;
import com.zhaopx.ai.service.KnowledgeSearchService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KnowledgeSearchServiceImpl implements KnowledgeSearchService {

    private static final String INDEX_CONTENT_FIELD = "content";
    private static final String INDEX_EMBEDDING_FIELD = "embedding";
    private static final String METADATA_SYSTEM_CODE_KEYWORD = "metadata.systemCode.keyword";
    private static final String METADATA_KB_ID_KEYWORD = "metadata.knowledgeBaseId.keyword";

    private final ElasticsearchClient elasticsearchClient;
    private final EmbeddingModel embeddingModel;

    @Value("${spring.ai.vectorstore.elasticsearch.index-name}")
    private String indexName;

    @Value("${search.hybrid.top-k:5}")
    private int defaultTopK;

    @Value("${search.hybrid.num-candidates:30}")
    private int defaultNumCandidates;

    @Value("${search.hybrid.rank-constant:60}")
    private long rankConstant;

    @Value("${search.hybrid.rank-window-size:100}")
    private long rankWindowSize;

    public KnowledgeSearchServiceImpl(VectorStore vectorStore, EmbeddingModel embeddingModel) {
        if (!(vectorStore instanceof ElasticsearchVectorStore elasticsearchVectorStore)) {
            throw new IllegalStateException("当前 VectorStore 不是 ElasticsearchVectorStore，无法执行 Hybrid Search");
        }
        this.elasticsearchClient = (ElasticsearchClient) elasticsearchVectorStore.getNativeClient()
                .orElseThrow(() -> new IllegalStateException("未获取到 Elasticsearch 原生客户端"));
        this.embeddingModel = embeddingModel;
    }

    @Override
    public KnowledgeSearchResponse search(KnowledgeSearchRequest request) {
        validateRequest(request);

        String query = request.getQuery().trim();
        int topK = Math.max(1, request.getTopK() == null ? defaultTopK : request.getTopK());
        int numCandidates = Math.max(topK, request.getNumCandidates() == null ? defaultNumCandidates : request.getNumCandidates());
        List<String> systemCodes = request.getSystemCodes().stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .collect(Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), ArrayList::new));
        float[] vector = embeddingModel.embed(query);

        try {
            SearchResponse<Map> response = elasticsearchClient.search(searchBuilder -> searchBuilder
                            .index(indexName)
                            .size(topK)
                            .query(buildTextQuery(query, systemCodes, request.getKnowledgeBaseId()))
                            .knn(buildKnnQuery(vector, topK, numCandidates, systemCodes, request.getKnowledgeBaseId()))
                            .rank(Rank.of(rank -> rank.rrf(rrf -> rrf
                                    .rankConstant(rankConstant)
                                    .rankWindowSize(rankWindowSize)))),
                    Map.class);

            List<KnowledgeSearchHit> hits = response.hits().hits().stream()
                    .map(this::toSearchHit)
                    .filter(Objects::nonNull)
                    .toList();

            log.info("Hybrid search finished. query={}, systemCodes={}, knowledgeBaseId={}, hits={}",
                    query, systemCodes, request.getKnowledgeBaseId(), hits.size());

            return KnowledgeSearchResponse.builder()
                    .query(query)
                    .hitCount(hits.size())
                    .searchType("HYBRID")
                    .hits(hits)
                    .build();
        } catch (IOException e) {
            throw new IllegalStateException("执行 Hybrid Search 失败", e);
        }
    }

    private void validateRequest(KnowledgeSearchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("检索请求不能为空");
        }
        if (StringUtils.isBlank(request.getQuery())) {
            throw new IllegalArgumentException("query 不能为空");
        }
        if (request.getSystemCodes() == null || request.getSystemCodes().isEmpty()) {
            throw new IllegalArgumentException("systemCodes 不能为空");
        }
    }

    private Query buildTextQuery(String query, List<String> systemCodes, String knowledgeBaseId) {
        return Query.of(q -> q.bool(bool -> bool
                .must(must -> must.multiMatch(mm -> mm
                        .query(query)
                        .fields(
                                INDEX_CONTENT_FIELD + "^6",
                                "metadata.documentName^3",
                                "metadata.tags^2",
                                "metadata.sourceType",
                                "metadata.bizId"
                        )))
                .filter(buildPermissionFilterList(systemCodes, knowledgeBaseId))));
    }

    private KnnSearch buildKnnQuery(float[] vector, int topK, int numCandidates,
                                    List<String> systemCodes, String knowledgeBaseId) {
        return KnnSearch.of(knn -> knn
                .field(INDEX_EMBEDDING_FIELD)
                .queryVector(toFloatList(vector))
                .k(topK)
                .numCandidates(numCandidates)
                .boost(1.0f)
                .filter(buildPermissionFilterList(systemCodes, knowledgeBaseId)));
    }

    private List<Query> buildPermissionFilterList(List<String> systemCodes, String knowledgeBaseId) {
        List<Query> filters = new ArrayList<>();
        filters.add(Query.of(q -> q.terms(t -> t
                .field(METADATA_SYSTEM_CODE_KEYWORD)
                .terms(tf -> tf.value(systemCodes.stream().map(FieldValue::of).toList())))));

        if (StringUtils.isNotBlank(knowledgeBaseId)) {
            filters.add(Query.of(q -> q.term(t -> t
                    .field(METADATA_KB_ID_KEYWORD)
                    .value(knowledgeBaseId.trim()))));
        }
        return filters;
    }

    @SuppressWarnings("unchecked")
    private KnowledgeSearchHit toSearchHit(Hit<Map> hit) {
        if (hit == null || hit.source() == null) {
            return null;
        }

        Map<String, Object> source = hit.source();
        Map<String, Object> metadata = source.get("metadata") instanceof Map<?, ?> rawMetadata
                ? (Map<String, Object>) rawMetadata : Collections.emptyMap();
        Object chunkIndex = metadata.get("chunkIndex");
        Integer chunkIndexValue = chunkIndex instanceof Number number ? number.intValue() : null;

        return KnowledgeSearchHit.builder()
                .id(hit.id())
                .score(hit.score())
                .rank(hit.rank())
                .content(source.get(INDEX_CONTENT_FIELD) == null ? null : String.valueOf(source.get(INDEX_CONTENT_FIELD)))
                .systemCode(metadata.get("systemCode") == null ? null : String.valueOf(metadata.get("systemCode")))
                .knowledgeBaseId(metadata.get("knowledgeBaseId") == null ? null : String.valueOf(metadata.get("knowledgeBaseId")))
                .documentName(metadata.get("documentName") == null ? null : String.valueOf(metadata.get("documentName")))
                .chunkIndex(chunkIndexValue)
                .metadata(metadata)
                .build();
    }

    private List<Float> toFloatList(float[] vector) {
        List<Float> values = new ArrayList<>(vector.length);
        for (float item : vector) {
            values.add(item);
        }
        return values;
    }
}
