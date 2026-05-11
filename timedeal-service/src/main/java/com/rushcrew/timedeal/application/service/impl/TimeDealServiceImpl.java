package com.rushcrew.timedeal.application.service.impl;

import com.rushcrew.common.enums.UserRole;
import com.rushcrew.common.exception.BusinessException;
import com.rushcrew.common.global.error.CommonErrorCode;
import com.rushcrew.timedeal.application.command.CreateTimeDealCommand;
import com.rushcrew.timedeal.application.command.UpdateTimeDealCommand;
import com.rushcrew.timedeal.application.event.TimeDealScheduledEvent;
import com.rushcrew.timedeal.application.event.TimeDealsEndedEvent;
import com.rushcrew.timedeal.application.event.TimeDealsStartedEvent;
import com.rushcrew.timedeal.application.model.ProductInfo;
import com.rushcrew.timedeal.application.result.CreateTimeDealResult;
import com.rushcrew.timedeal.application.result.TimeDealDetailResult;
import com.rushcrew.timedeal.application.result.TimeDealForOrderResult;
import com.rushcrew.timedeal.application.result.TimeDealProductResult;
import com.rushcrew.timedeal.application.result.TimeDealResult;
import com.rushcrew.timedeal.application.result.UpdateTimeDealResult;
import com.rushcrew.timedeal.application.service.TimeDealPolicy;
import com.rushcrew.timedeal.application.service.TimeDealService;
import com.rushcrew.timedeal.domain.entity.TimeDeal;
import com.rushcrew.timedeal.domain.exception.TimeDealErrorCode;
import com.rushcrew.timedeal.domain.model.CreateTimeDealParams;
import com.rushcrew.timedeal.domain.model.UpdateTimeDealParams;
import com.rushcrew.timedeal.domain.port.ProductClient;
import com.rushcrew.timedeal.domain.port.TimeDealQueueKey;
import com.rushcrew.timedeal.domain.repository.TimeDealRepository;
import com.rushcrew.timedeal.domain.vo.TimeDealInfo;
import com.rushcrew.timedeal.domain.vo.TimeDealStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TimeDealServiceImpl implements TimeDealService {

    private final TimeDealRepository timeDealRepository;
    private final ProductClient productClient;
    private final TimeDealPolicy timeDealPolicy;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public CreateTimeDealResult createTimeDeal(
        Long userId, String role, CreateTimeDealCommand command
    ) {
        ProductInfo productInfo = productClient.getProductItemIds(command.productId());

        Long sellerId = productInfo.sellerId();
        if (role.equals(UserRole.SELLER.getDescription()) && !Objects.equals(userId, sellerId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }

        if (command.discountPrice().isMoreExpensiveThan(productInfo.price())) {
            throw new BusinessException(TimeDealErrorCode.MUST_BE_CHEAPER);
        }

        CreateTimeDealParams params = new CreateTimeDealParams(
            TimeDealInfo.of(command.title(), command.description(), sellerId),
            command.discountPrice(), command.limitQuantity(), command.period(), command.status(),
            command.productId(), productInfo.optionIds()
        );
        TimeDeal newTimeDeal = TimeDeal.create(params);
        timeDealRepository.save(newTimeDeal);

        Instant startAt = command.period().getStartAt();
        Instant endAt = command.period().getEndAt();
        eventPublisher.publishEvent(
            new TimeDealScheduledEvent(newTimeDeal.getId(), startAt, TimeDealQueueKey.START));
        eventPublisher.publishEvent(
            new TimeDealScheduledEvent(newTimeDeal.getId(), endAt, TimeDealQueueKey.END));

        return CreateTimeDealResult.from(newTimeDeal);
    }

    @Override
    @Transactional
    public UpdateTimeDealResult updateTimeDeal(
        Long userId, String role, UUID timeDealId, UpdateTimeDealCommand command
    ) {
        TimeDeal timeDeal = timeDealRepository.findById(timeDealId)
            .orElseThrow(() -> new BusinessException(TimeDealErrorCode.NOT_FOUND_TIME_DEAL));
        timeDealPolicy.validateSellerPermission(timeDeal, userId, role);

        UpdateTimeDealParams params = new UpdateTimeDealParams(
            command.title(), command.description(), command.discountPrice(),
            command.limitQuantity(), command.startAt(), command.endAt()
        );
        timeDeal.update(params);

        if (command.startAt() != null) {
            eventPublisher.publishEvent(
                new TimeDealScheduledEvent(
                    timeDeal.getId(), command.startAt(), TimeDealQueueKey.START)
            );
        }
        if (command.endAt() != null) {
            eventPublisher.publishEvent(
                new TimeDealScheduledEvent(
                    timeDeal.getId(), command.endAt(), TimeDealQueueKey.END)
            );
        }

        return UpdateTimeDealResult.from(timeDeal);
    }

    @Override
    @Transactional
    public void forceEndTimeDeal(UUID timeDealId) {
        TimeDeal timeDeal = timeDealRepository.findById(timeDealId)
            .orElseThrow(() -> new BusinessException(TimeDealErrorCode.NOT_FOUND_TIME_DEAL));
        timeDeal.forceEnd();
    }

    @Override
    public Page<TimeDealResult> getTimeDeals(TimeDealStatus status, Pageable pageable) {
        return timeDealRepository.findNotEndedByStatus(status, pageable);
    }

    @Override
    public Page<TimeDealResult> getAllTimeDealsForAdmin(TimeDealStatus status, Pageable pageable) {
        return timeDealRepository.findAllByStatus(status, pageable);
    }

    @Override
    public TimeDealDetailResult getTimeDealDetail(UUID timeDealId) {
        TimeDeal timeDeal = timeDealRepository.findByIdAndStatusNot(timeDealId,
                TimeDealStatus.ENDED)
            .orElseThrow(() -> new BusinessException(TimeDealErrorCode.NOT_FOUND_TIME_DEAL));

        List<TimeDealProductResult> timeDealProdutResultList =
            timeDeal.getTimeDealProducts().stream().map(TimeDealProductResult::from).toList();

        return TimeDealDetailResult.of(timeDeal, timeDealProdutResultList);
    }

    @Override
    @Transactional
    public void startTimeDeals(List<String> timeDealIds) {
        List<TimeDeal> updatedTimeDeals = executeStatusUpdate(timeDealIds,
            TimeDealStatus.IN_PROGRESS);

        Map<String, Instant> startMap = updatedTimeDeals.stream()
            .collect(Collectors.toMap(
                timeDeal -> timeDeal.getId().toString(),
                timeDeal -> timeDeal.getPeriod().getStartAt()
            ));

        eventPublisher.publishEvent(new TimeDealsStartedEvent(startMap));
    }

    @Override
    @Transactional
    public void endTimeDeals(List<String> timeDealIds) {
        List<TimeDeal> updatedTimeDeals = executeStatusUpdate(timeDealIds, TimeDealStatus.ENDED);

        Map<String, Instant> endMap = updatedTimeDeals.stream()
            .collect(Collectors.toMap(
                timeDeal -> timeDeal.getId().toString(),
                timeDeal -> timeDeal.getPeriod().getEndAt()
            ));

        eventPublisher.publishEvent(new TimeDealsEndedEvent(endMap));
    }

    private List<TimeDeal> executeStatusUpdate(
        List<String> timeDealIds,
        TimeDealStatus newStatus
    ) {
        List<UUID> idList = timeDealIds.stream().map(UUID::fromString).toList();

        List<TimeDeal> timeDealList = timeDealRepository.findAllById(idList);
        List<TimeDeal> updatedTimeDeals = new ArrayList<>();

        for (TimeDeal timeDeal : timeDealList) {
            TimeDealStatus previousStatus = timeDeal.getStatus();

            switch (newStatus) {
                case IN_PROGRESS -> {
                    if (previousStatus == TimeDealStatus.SCHEDULED) {
                        timeDeal.updateStatus(newStatus);
                        updatedTimeDeals.add(timeDeal);
                    }
                }
                case ENDED -> {
                    if (previousStatus == TimeDealStatus.IN_PROGRESS
                        || previousStatus == TimeDealStatus.SOLD_OUT
                    ) {
                        timeDeal.updateStatus(newStatus);
                        updatedTimeDeals.add(timeDeal);
                    }
                }
            }
        }

        return updatedTimeDeals;
    }

    @Override
    @Transactional(readOnly = true)
    public TimeDealForOrderResult getTimeDealForOrder(UUID timeDealId) {
        return timeDealRepository.findForOrder(timeDealId)
            .orElseThrow(() -> new BusinessException(TimeDealErrorCode.NOT_FOUND_TIME_DEAL));
    }
}
