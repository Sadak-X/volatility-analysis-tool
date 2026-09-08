package com.volatility.modules.stock.repository;

import com.volatility.modules.stock.entity.StockBasicEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface StockBasicRepository extends JpaRepository<StockBasicEntity, Long>, JpaSpecificationExecutor<StockBasicEntity> {

    Optional<StockBasicEntity> findByStockCode(String stockCode);

    List<StockBasicEntity> findByStockCodeIn(List<String> stockCodes);
}
