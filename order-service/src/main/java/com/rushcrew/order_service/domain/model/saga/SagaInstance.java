package com.rushcrew.order_service.domain.model.saga;

import java.time.Instant;
import java.util.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushcrew.order_service.application.saga.dto.OrderCreationSagaData;
import com.rushcrew.order_service.domain.enums.SagaStatus;
import com.rushcrew.order_service.domain.enums.SagaStepName;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "p_saga_instance", schema = "order_schema")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class SagaInstance {

	@Id
	private UUID sagaId;

	private UUID orderId;

	@Column(nullable = false, length = 50)
	private String sagaType;

	@Column(nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SagaStatus status;

	@Column(nullable = false)
	private Instant createdAt;

	private Instant completedAt;

	private Instant failedAt;

	@Column(columnDefinition = "TEXT")
	private String errorMessage;

	@OneToMany(mappedBy = "sagaInstance", cascade = CascadeType.ALL, orphanRemoval = true)
	@Builder.Default
	private List<SagaStep> steps = new ArrayList<>();

	@Column(columnDefinition = "TEXT")
	private String sagaData; // JSON 형태로 저장

	@Transient
	private static final ObjectMapper objectMapper = new ObjectMapper();

	public static SagaInstance create(String sagaType, Long userId) {
		return SagaInstance.builder()
			.sagaId(UUID.randomUUID())
			.sagaType(sagaType)
			.userId(userId)
			.status(SagaStatus.RUNNING)
			.createdAt(Instant.now())
			.build();
	}

	public void addStep(SagaStepName stepName, SagaStatus status) {
		this.steps.add(SagaStep.create(this, stepName.name(), status));
	}

	public void complete() {
		this.status = SagaStatus.COMPLETED;
		this.completedAt = Instant.now();
	}

	public void fail(String errorMessage) {
		this.status = SagaStatus.FAILED;
		this.failedAt = Instant.now();
		this.errorMessage = errorMessage;
	}

	public boolean isCompleted() {
		return status == SagaStatus.COMPLETED;
	}

	public boolean isFailed() {
		return status == SagaStatus.FAILED;
	}

	// SagaData 저장
	public void saveData(OrderCreationSagaData data) {
		try {
			this.sagaData = objectMapper.writeValueAsString(data);
		} catch (JsonProcessingException e) {
			throw new RuntimeException("SagaData 직렬화 실패", e);
		}
	}

	// SagaData 복원
	public OrderCreationSagaData restoreData() {
		if (this.sagaData == null) return null;
		try {
			return objectMapper.readValue(this.sagaData, OrderCreationSagaData.class);
		} catch (JsonProcessingException e) {
			throw new RuntimeException("SagaData 역직렬화 실패", e);
		}
	}

	/**
	 * 특정 Step이 완료되었는지 확인
	 * @param sagaStepName 확인할 Step 이름
	 * @return 완료 여부
	 */
	public boolean hasCompletedStep(SagaStepName sagaStepName) {
		return this.steps.stream()
			.anyMatch(step ->
				step.getStepName().equals(sagaStepName.name())
					&& step.getStatus() == SagaStatus.COMPLETED
			);
	}

	/**
	 * 특정 Step의 상태 확인
	 * @param sagaStepName 확인할 Step 이름
	 * @return Step의 상태 (없으면 null)
	 */
	public SagaStatus getStepStatus(SagaStepName sagaStepName) {
		return this.steps.stream()
			.filter(step -> step.getStepName().equals(sagaStepName.name()))
			.findFirst()
			.map(SagaStep::getStatus)
			.orElse(null);
	}

	/**
	 * 완료된 모든 Step 목록 조회
	 */
	public List<String> getCompletedSteps() {
		return this.steps.stream()
			.filter(step -> step.getStatus() == SagaStatus.COMPLETED)
			.map(SagaStep::getStepName)
			.toList();
	}
}
