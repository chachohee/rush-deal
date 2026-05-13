package com.rushcrew.timedeal.infrastructure.kafka.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushcrew.timedeal.application.port.out.event.TimeDealEndedEvent;
import com.rushcrew.timedeal.application.port.out.event.TimeDealStartedEvent;
import com.rushcrew.timedeal.infrastructure.kafka.dto.TimeDealEndMessage;
import com.rushcrew.timedeal.infrastructure.kafka.dto.TimeDealStartMessage;
import com.rushcrew.timedeal.infrastructure.kafka.dto.TimeDealStartNotifyMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TimeDealEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishTimeDealStart(TimeDealStartedEvent event) {
        try {
            TimeDealStartMessage message =
                new TimeDealStartMessage(event.timeDealId(), event.startAt());

            kafkaTemplate.send("time-deal-start", event.timeDealId().toString(),
                objectMapper.writeValueAsString(message));
            log.info("time-deal-start 이벤트 발행 완료 - timeDealId={}", event.timeDealId());
        } catch (Exception e) {
            log.error("time-deal-start 이벤트 발행 실패 - timeDealId={}", event.timeDealId(), e);
        }
    }

    public void publishTimeDealStartNotify(TimeDealStartNotifyMessage message) {
        try {
            kafkaTemplate.send("timedeal.start.notify", message.timeDealId().toString(),
                objectMapper.writeValueAsString(message));
            log.info("timedeal.start.notify 발행 완료 - timeDealId={}, seller={}, users={}",
                message.timeDealId(), message.sellerId(),
                message.interestedUserIds() == null ? 0 : message.interestedUserIds().size());
        } catch (Exception e) {
            log.error("timedeal.start.notify 발행 실패 - timeDealId={}", message.timeDealId(), e);
        }
    }

    public void publishTimeDealEnd(TimeDealEndedEvent event) {
        try {
            TimeDealEndMessage message =
                new TimeDealEndMessage(event.timeDealId(), event.productId(), event.endAt());

            kafkaTemplate.send("time-deal-end", event.timeDealId().toString(),
                objectMapper.writeValueAsString(message));
            log.info("time-deal-end 이벤트 발행 완료 - timeDealId={}", event.timeDealId());
        } catch (Exception e) {
            log.error("time-deal-end 이벤트 발행 실패 - timeDealId={}", event.timeDealId(), e);
        }
    }
}
