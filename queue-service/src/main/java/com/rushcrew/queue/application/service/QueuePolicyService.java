package com.rushcrew.queue.application.service;

import com.rushcrew.common.exception.BusinessException;
import com.rushcrew.queue.application.command.policy.CreatePolicyCommand;
import com.rushcrew.queue.application.command.policy.SearchPolicyCommand;
import com.rushcrew.queue.application.command.policy.UpdatePolicyCommand;
import com.rushcrew.queue.application.dto.PageQuery;
import com.rushcrew.queue.application.dto.QueuePolicyQueryResponse;
import com.rushcrew.queue.application.port.in.QueuePolicyPort;
import com.rushcrew.queue.application.validator.QueuePolicyValidator;
import com.rushcrew.queue.common.QueueErrorCode;
import com.rushcrew.queue.domain.dto.SearchPolicyCondition;
import com.rushcrew.queue.domain.entity.QueuePolicy;
import com.rushcrew.queue.domain.enums.QueuePolicyStatus;
import com.rushcrew.queue.domain.repository.QueuePolicyRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QueuePolicyService implements QueuePolicyPort {

    private final QueuePolicyRepository queuePolicyRepository;
    private final QueuePolicyValidator queuePolicyValidator;

    public QueuePolicyService(QueuePolicyRepository queuePolicyRepository,
        QueuePolicyValidator queuePolicyValidator) {
        this.queuePolicyRepository = queuePolicyRepository;
        this.queuePolicyValidator = queuePolicyValidator;
    }

    /**
     * 타임딜 정책 생성 : MASTER 권한만 가능
     */
    @Override
    @Transactional
    public QueuePolicyQueryResponse createQueuePolicy(CreatePolicyCommand command, Long userId) {
        return queuePolicyRepository.findByProductId(command.productId())
            .map(existing -> {
                if (existing.getStatus() != QueuePolicyStatus.STOPPED) {
                    throw new BusinessException(QueueErrorCode.POLICY_ALREADY_EXISTS);
                }
                existing.update(command.dealName(), command.status(),
                    command.timePeriod(), command.trafficSetting());
                return QueuePolicyQueryResponse.from(existing);
            })
            .orElseGet(() -> {
                QueuePolicy created = QueuePolicy.create(
                    command.productId(), command.dealName(), command.status(),
                    command.timePeriod(), command.trafficSetting()
                );
                return QueuePolicyQueryResponse.from(queuePolicyRepository.save(created));
            });
    }

    /**
     * 타임딜 정책 목록 페이징 조회
     * Application DTO인 QueuePolicyQueryResponse의 Page를 반환
     */
    @Override
    @Transactional(readOnly = true)
    public Page<QueuePolicyQueryResponse> searchPolicies(PageQuery query, SearchPolicyCommand command,
        Long userId) {
        QueuePolicyStatus status = command.getQueuePolicyStatus();
        SearchPolicyCondition condition = new SearchPolicyCondition(
            command.productId(),
            status
        );

        PageRequest pageable = query.toPageable();

        // specification 동적 쿼리 반환
        Page<QueuePolicy> policyPage = queuePolicyRepository.findAllByCondition(
            condition.productId(),
            condition.status(),
            pageable
        );

        // Page 변환하여 반환
        return policyPage.map(QueuePolicyQueryResponse::from);
    }

    /**
     * 특정 타임딜 정책 조회
     */
    @Override
    @Transactional(readOnly = true)
    public QueuePolicyQueryResponse getQueuePolicyInfo(UUID policyId, Long userId) {
        QueuePolicy queuePolicy = getQueuePolicy(policyId);
        return QueuePolicyQueryResponse.from(queuePolicy);
    }

    /**
     * 타임딜 정책 정보 수정
     * 권한 : 마스터(MASTER)
     */
    @Override
    @Transactional
    public QueuePolicyQueryResponse updateQueuePolicy(UpdatePolicyCommand command, UUID policyId, Long userId) {
        QueuePolicy queuePolicy = getQueuePolicy(policyId);
        queuePolicy.update(command.timeDealName(),
            command.status(),
            command.timePeriod(),
            command.trafficSetting());
        return QueuePolicyQueryResponse.from(queuePolicy);
    }

    /**
     * 타임딜 정책 삭제 : MASTER 권한만 가능
     * */
    @Override
    @Transactional
    public void deleteQueuePolicy(UUID policyId, Long userId) {
        QueuePolicy queuePolicy = getQueuePolicy(policyId);

        if (queuePolicy.isDeleted()) {
            throw new BusinessException(QueueErrorCode.POLICY_ALREADY_DELETED);
        }
        queuePolicy.softDelete(userId);
    }

    @Transactional
    public void deactivateByProductId(UUID productId) {
        queuePolicyRepository.findByProductId(productId).ifPresent(policy ->
            policy.update(policy.getTimeDealName(), QueuePolicyStatus.STOPPED,
                policy.getTimePeriod(), policy.getTrafficSetting())
        );
    }

    private QueuePolicy getQueuePolicy(UUID queuePolicyId) {
        return queuePolicyRepository.findById(queuePolicyId)
            .orElseThrow(() -> new BusinessException(QueueErrorCode.POLICY_NOT_FOUND));
    }
}
