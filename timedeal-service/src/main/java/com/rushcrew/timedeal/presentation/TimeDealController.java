package com.rushcrew.timedeal.presentation;

import com.rushcrew.timedeal.application.command.CreateTimeDealCommand;
import com.rushcrew.timedeal.application.command.UpdateTimeDealCommand;
import com.rushcrew.timedeal.application.result.CreateTimeDealResult;
import com.rushcrew.timedeal.application.result.TimeDealDetailResult;
import com.rushcrew.timedeal.application.result.TimeDealForOrderResult;
import com.rushcrew.timedeal.application.result.TimeDealResult;
import com.rushcrew.timedeal.application.result.UpdateTimeDealResult;
import com.rushcrew.timedeal.application.service.TimeDealService;
import com.rushcrew.timedeal.domain.vo.TimeDealStatus;
import com.rushcrew.timedeal.global.security.model.UserDetailsImpl;
import com.rushcrew.timedeal.presentation.dto.request.CreateTimeDealRequest;
import com.rushcrew.timedeal.presentation.dto.request.UpdateTimeDealRequest;
import com.rushcrew.timedeal.presentation.dto.response.CreateTimeDealResponse;
import com.rushcrew.timedeal.presentation.dto.response.TimeDealDetailResponse;
import com.rushcrew.timedeal.presentation.dto.response.TimeDealForOrderResponse;
import com.rushcrew.timedeal.presentation.dto.response.TimeDealResponse;
import com.rushcrew.timedeal.presentation.dto.response.UpdateTimeDealResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/timedeals")
@RequiredArgsConstructor
public class TimeDealController {

    private final TimeDealService timeDealService;

    @PostMapping
    @PreAuthorize("hasAnyRole('MASTER', 'SELLER')")
    public ResponseEntity<CreateTimeDealResponse> createTimeDeal(
        @Valid @RequestBody CreateTimeDealRequest request,
        @AuthenticationPrincipal UserDetailsImpl principal
    ) {
        CreateTimeDealCommand command = request.toCommand();
        CreateTimeDealResult result =
            timeDealService.createTimeDeal(principal.userId(), principal.role(), command);
        return ResponseEntity.status(HttpStatus.CREATED).body(CreateTimeDealResponse.from(result));
    }

    @PatchMapping("/{timeDealId}")
    @PreAuthorize("hasAnyRole('MASTER', 'SELLER')")
    public ResponseEntity<UpdateTimeDealResponse> updateTimeDeal(
        @Valid UpdateTimeDealRequest request,
        @PathVariable UUID timeDealId,
        @AuthenticationPrincipal UserDetailsImpl principal
    ) {
        UpdateTimeDealCommand command = request.toCommand();
        UpdateTimeDealResult result =
            timeDealService.updateTimeDeal(
                principal.userId(), principal.role(), timeDealId, command);
        return ResponseEntity.ok(UpdateTimeDealResponse.from(result));
    }

    @PostMapping("/{timeDealId}/force-end")
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<Void> forceEndTimeDeal(
        @PathVariable UUID timeDealId
    ) {
        timeDealService.forceEndTimeDeal(timeDealId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<Page<TimeDealResponse>> getTimeDeals(
        @RequestParam(required = false) TimeDealStatus status, // 진행예정 or 진행중
        @SortDefault(sort = "createdAt", direction = Direction.DESC) Pageable pageable
    ) {
        Page<TimeDealResult> result = timeDealService.getTimeDeals(status, pageable);
        Page<TimeDealResponse> response = result.map(TimeDealResponse::from);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<Page<TimeDealResponse>> getAllTimeDealsForAdmin(
        @RequestParam(required = false) TimeDealStatus status,
        @SortDefault(sort = "createdAt", direction = Direction.DESC) Pageable pageable
    ) {
        Page<TimeDealResult> result = timeDealService.getAllTimeDealsForAdmin(status, pageable);
        Page<TimeDealResponse> response = result.map(TimeDealResponse::from);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{timeDealId}")
    public ResponseEntity<TimeDealDetailResponse> getTimeDealDetail(
        @PathVariable UUID timeDealId
    ) {
        TimeDealDetailResult result = timeDealService.getTimeDealDetail(timeDealId);
        return ResponseEntity.ok(TimeDealDetailResponse.from(result));
    }


	@GetMapping("/{timeDealId}/order")
	@PreAuthorize("hasAnyRole('USER', 'SELLER', 'MASTER')")
	public ResponseEntity<TimeDealForOrderResponse> getTimeDealForOrder(
		@PathVariable UUID timeDealId
	) {
		TimeDealForOrderResult result = timeDealService.getTimeDealForOrder(timeDealId);
		return ResponseEntity.ok(TimeDealForOrderResponse.from(result));
	}

}
