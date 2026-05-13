package com.rushcrew.timedeal.application.service;

import com.rushcrew.timedeal.application.command.ConfirmStockCommand;
import com.rushcrew.timedeal.application.command.CreateStockCommand;
import com.rushcrew.timedeal.application.command.ReserveStockCommand;
import com.rushcrew.timedeal.application.command.RestoreStockCommand;
import com.rushcrew.timedeal.application.command.UpdateStockCountCommand;
import com.rushcrew.timedeal.application.result.ConfirmStockResult;
import com.rushcrew.timedeal.application.result.CreateStockResult;
import com.rushcrew.timedeal.application.result.ReserveStockResult;
import com.rushcrew.timedeal.application.result.StockLogResult;
import com.rushcrew.timedeal.application.result.StockResult;
import com.rushcrew.timedeal.application.result.UpdateStockCountResult;
import com.rushcrew.timedeal.domain.vo.TimeDealStockStatus;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface StockService {

    CreateStockResult createStock(CreateStockCommand command);

    UpdateStockCountResult changeStockCount(UUID stockId, UpdateStockCountCommand command);

    void deleteStock(UUID stockId, Long userId);

    Page<StockResult> getStocks(
        String keyword, UUID productId, TimeDealStockStatus status, Pageable pageable);

    List<StockResult> getLowStock(Long sellerId, Long threshold, int limit);

    StockResult getStock(Long userId, String role, UUID stockId);

    ReserveStockResult reserveStock(ReserveStockCommand command);

    ConfirmStockResult confirmStock(ConfirmStockCommand command);

    void restoreStock(RestoreStockCommand command);

    Page<StockLogResult> getStockLogs(UUID stockId, String eventType, Pageable pageable);

	void reserveStocksBatch(List<ReserveStockCommand> commands);

	void restoreStocksBatch(List<RestoreStockCommand> commands);
}
