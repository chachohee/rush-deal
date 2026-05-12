package com.rushcrew.timedeal.search.service;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.rushcrew.timedeal.search.document.TimeDealDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TimeDealSearchService {

    private final ElasticsearchOperations elasticsearchOperations;

    public Page<TimeDealDocument> search(String q, Pageable pageable) {
        Query query = Query.of(b -> b.bool(bool -> bool
            .should(s -> s.match(m -> m.field("title").query(q).boost(2.0f)))
            .should(s -> s.match(m -> m.field("description").query(q)))
            .minimumShouldMatch("1")
        ));

        NativeQuery nativeQuery = NativeQuery.builder()
            .withQuery(query)
            .withPageable(pageable)
            .build();

        SearchHits<TimeDealDocument> hits =
            elasticsearchOperations.search(nativeQuery, TimeDealDocument.class);

        return new PageImpl<>(
            hits.stream().map(h -> h.getContent()).toList(),
            pageable,
            hits.getTotalHits()
        );
    }
}
