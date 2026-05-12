package com.rushcrew.timedeal.search.repository;

import com.rushcrew.timedeal.search.document.TimeDealDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface TimeDealSearchRepository
    extends ElasticsearchRepository<TimeDealDocument, String> {
}
