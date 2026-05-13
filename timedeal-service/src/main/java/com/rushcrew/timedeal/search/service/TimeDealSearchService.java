package com.rushcrew.timedeal.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.rushcrew.timedeal.search.document.TimeDealDocument;
import com.rushcrew.timedeal.search.dto.SearchHitItem;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightFieldParameters;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimeDealSearchService {

    private static final List<String> HIGHLIGHT_FIELDS =
        List.of("title", "productName", "companyName", "description", "categoryLabel");

    // 1글자 토큰으로 분해되는 합성어(예: nori 가 "핫딜"→"딜")가
    // 회사명 등의 흔한 토큰과 부분 매칭되어 노이즈가 끼는 것을 차단.
    // 점수가 임계값 이상인 문서만 결과에 포함.
    // - 정확 매칭은 보통 1.0 이상
    // - 회사명 1개 토큰 매칭(idf 가 낮은 공통 토큰)은 ~0.3
    // - "핫딜→딜" 같은 1글자 부분 매칭 노이즈는 ~0.08
    // 회사명 동질 매칭은 보존하고 1글자 노이즈만 차단하도록 0.2.
    private static final float MIN_SCORE = 0.2f;

    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchClient elasticsearchClient;

    public Page<SearchHitItem> search(String q, Pageable pageable) {
        Query query = Query.of(b -> b.bool(bool -> bool
            .should(s -> s.match(m -> m.field("title").query(q).boost(2.0f)))
            .should(s -> s.match(m -> m.field("productName").query(q).boost(1.5f)))
            .should(s -> s.match(m -> m.field("companyName").query(q).boost(1.2f)))
            .should(s -> s.match(m -> m.field("description").query(q).boost(0.8f)))
            .should(s -> s.match(m -> m.field("categoryLabel").query(q).boost(1.0f)))
            .should(s -> s.term(t -> t.field("category").value(q.toUpperCase()).boost(0.5f)))
            .minimumShouldMatch("1")
        ));

        Highlight highlight = new Highlight(
            HighlightParameters.builder()
                .withPreTags("<mark>")
                .withPostTags("</mark>")
                .build(),
            HIGHLIGHT_FIELDS.stream()
                .map(f -> new HighlightField(f,
                    HighlightFieldParameters.builder().withNumberOfFragments(0).build()))
                .toList()
        );

        NativeQuery nativeQuery = NativeQuery.builder()
            .withQuery(query)
            .withPageable(pageable)
            .withMinScore(MIN_SCORE)
            .withHighlightQuery(new HighlightQuery(highlight, TimeDealDocument.class))
            .build();

        SearchHits<TimeDealDocument> hits =
            elasticsearchOperations.search(nativeQuery, TimeDealDocument.class);

        List<SearchHitItem> items = hits.stream().map(this::toItem).toList();
        return new PageImpl<>(items, pageable, hits.getTotalHits());
    }

    public List<String> suggest(String prefix, int size) {
        try {
            SearchRequest request = SearchRequest.of(b -> b
                .index("timedeal")
                .suggest(s -> s
                    .suggesters("timedeal-suggest", sg -> sg
                        .prefix(prefix)
                        .completion(c -> c.field("suggest").skipDuplicates(true).size(size))
                    )
                )
            );

            SearchResponse<Void> response = elasticsearchClient.search(request, Void.class);

            return response.suggest().values().stream()
                .flatMap(List::stream)
                .flatMap(s -> s.completion().options().stream())
                .map(opt -> opt.text())
                .distinct()
                .limit(size)
                .toList();
        } catch (IOException e) {
            log.error("[Search] suggest 실패: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private SearchHitItem toItem(SearchHit<TimeDealDocument> hit) {
        Map<String, List<String>> highlightFields = hit.getHighlightFields();
        return SearchHitItem.from(hit.getContent(), highlightFields);
    }
}
