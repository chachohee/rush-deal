package com.rushcrew.timedeal.search.document;

import com.rushcrew.timedeal.application.model.ProductSearchInfo;
import com.rushcrew.timedeal.domain.entity.TimeDeal;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.CompletionField;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;
import org.springframework.data.elasticsearch.core.suggest.Completion;

@Document(indexName = "timedeal")
@Setting(settingPath = "elasticsearch/timedeal-settings.json")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TimeDealDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "korean")
    private String title;

    @Field(type = FieldType.Text, analyzer = "korean")
    private String description;

    @Field(type = FieldType.Text, analyzer = "korean")
    private String productName;

    @Field(type = FieldType.Text, analyzer = "korean")
    private String companyName;

    @Field(type = FieldType.Keyword)
    private String category;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Long)
    private Long price;

    @Field(type = FieldType.Keyword, index = false)
    private String imageUrl;

    @Field(type = FieldType.Date, format = DateFormat.date_optional_time)
    private Instant startAt;

    @Field(type = FieldType.Date, format = DateFormat.date_optional_time)
    private Instant endAt;

    @CompletionField(maxInputLength = 64)
    private Completion suggest;

    public static TimeDealDocument from(TimeDeal td, ProductSearchInfo productInfo) {
        String title = td.getTimeDealInfo().getTitle();
        String productName = productInfo != null ? productInfo.productName() : null;
        String companyName = productInfo != null ? productInfo.companyName() : null;
        String category = productInfo != null ? productInfo.category() : null;
        String imageUrl = productInfo != null ? productInfo.imageUrl() : null;

        return TimeDealDocument.builder()
            .id(td.getId().toString())
            .title(title)
            .description(td.getTimeDealInfo().getDescription())
            .productName(productName)
            .companyName(companyName)
            .category(category)
            .imageUrl(imageUrl)
            .status(td.getStatus().name())
            .price(td.getPrice().getAmount())
            .startAt(td.getPeriod().getStartAt())
            .endAt(td.getPeriod().getEndAt())
            .suggest(new Completion(
                List.of(title, productName, companyName).stream()
                    .filter(s -> s != null && !s.isBlank())
                    .toArray(String[]::new)
            ))
            .build();
    }
}
