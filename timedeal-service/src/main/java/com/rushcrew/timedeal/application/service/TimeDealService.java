package com.rushcrew.timedeal.application.service;

import com.rushcrew.timedeal.application.command.CreateTimeDealCommand;
import com.rushcrew.timedeal.application.command.UpdateTimeDealCommand;
import com.rushcrew.timedeal.application.result.CreateTimeDealResult;
import com.rushcrew.timedeal.application.result.TimeDealDetailResult;
import com.rushcrew.timedeal.application.result.TimeDealForOrderResult;
import com.rushcrew.timedeal.application.result.TimeDealResult;
import com.rushcrew.timedeal.application.result.UpdateTimeDealResult;
import com.rushcrew.timedeal.domain.vo.TimeDealStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TimeDealService {

    CreateTimeDealResult createTimeDeal(Long userId, String role, CreateTimeDealCommand command);

    UpdateTimeDealResult updateTimeDeal(
        Long userId, String role, UUID timeDealId, UpdateTimeDealCommand command);

    void forceEndTimeDeal(UUID timeDealId);

    Page<TimeDealResult> getTimeDeals(TimeDealStatus status, Pageable pageable);

    Page<TimeDealResult> getAllTimeDealsForAdmin(TimeDealStatus status, Pageable pageable);

    TimeDealDetailResult getTimeDealDetail(UUID timeDealId);

    void startTimeDeals(List<String> timeDealIds);

    void endTimeDeals(List<String> timeDealIds);

    TimeDealForOrderResult getTimeDealForOrder(UUID timeDealId);
}
