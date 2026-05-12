package com.rushcrew.timedeal.search.document;

import com.rushcrew.timedeal.domain.entity.TimeDeal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

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

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Long)
    private Long price;

    @Field(type = FieldType.Date)
    private Instant startAt;

    @Field(type = FieldType.Date)
    private Instant endAt;

    public static TimeDealDocument from(TimeDeal td) {
        return TimeDealDocument.builder()
            .id(td.getId().toString())
            .title(td.getTimeDealInfo().getTitle())
            .description(td.getTimeDealInfo().getDescription())
            .status(td.getStatus().name())
            .price(td.getPrice().getAmount())
            .startAt(td.getPeriod().getStartAt())
            .endAt(td.getPeriod().getEndAt())
            .build();
    }
}
