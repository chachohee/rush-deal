package com.rushcrew.timedeal;

import static org.assertj.core.api.Assertions.assertThat;

import com.rushcrew.timedeal.application.result.TimeDealResult;
import com.rushcrew.timedeal.interest.application.InterestedDealService;
import com.rushcrew.timedeal.interest.domain.InterestedDealRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class InterestedDealIntegrationTest extends IntegrationTestBase {

    @Autowired InterestedDealService service;
    @Autowired InterestedDealRepository repository;

    @AfterEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("register는 (userId, timeDealId) 쌍 한 건을 저장한다")
    void register_savesOne() {
        Long userId = 1L;
        UUID timeDealId = UUID.randomUUID();

        service.register(userId, timeDealId);

        assertThat(service.isInterested(userId, timeDealId)).isTrue();
    }

    @Test
    @DisplayName("같은 (userId, timeDealId) 중복 등록은 idempotent — 두 번 호출해도 1건만")
    void register_isIdempotent() {
        Long userId = 1L;
        UUID timeDealId = UUID.randomUUID();

        service.register(userId, timeDealId);
        service.register(userId, timeDealId);

        assertThat(service.findUserIdsByTimeDealId(timeDealId)).hasSize(1);
    }

    @Test
    @DisplayName("unregister 후에는 isInterested가 false를 반환한다")
    void unregister_clearsInterest() {
        Long userId = 1L;
        UUID timeDealId = UUID.randomUUID();
        service.register(userId, timeDealId);

        service.unregister(userId, timeDealId);

        assertThat(service.isInterested(userId, timeDealId)).isFalse();
    }

    @Test
    @DisplayName("findUserIdsByTimeDealId는 특정 타임딜에 등록된 모든 userId를 반환한다")
    void findUserIdsByTimeDealId_returnsAllInterested() {
        UUID td = UUID.randomUUID();
        service.register(1L, td);
        service.register(2L, td);
        service.register(3L, td);
        service.register(99L, UUID.randomUUID()); // 다른 타임딜

        List<Long> users = service.findUserIdsByTimeDealId(td);

        assertThat(users).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    @DisplayName("listMyInterested는 타임딜 존재 없으면 빈 리스트를 반환한다 (참조 무결성 깨져도 안전)")
    void listMyInterested_handlesMissingTimeDeal() {
        service.register(1L, UUID.randomUUID());

        List<TimeDealResult> list = service.listMyInterested(1L);

        assertThat(list).isEmpty();
    }
}
