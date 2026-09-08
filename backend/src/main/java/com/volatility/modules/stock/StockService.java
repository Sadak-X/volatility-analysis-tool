package com.volatility.modules.stock;

import com.volatility.common.exception.BusinessException;
import com.volatility.modules.stock.dto.StockBasicDto;
import com.volatility.modules.stock.dto.StockPageItem;
import com.volatility.modules.stock.entity.StockBasicEntity;
import com.volatility.modules.stock.repository.StockBasicRepository;
import com.volatility.modules.task.PythonClient;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class StockService {

    private final StockBasicRepository stockBasicRepository;
    private final PythonClient pythonClient;

    public StockService(StockBasicRepository stockBasicRepository, PythonClient pythonClient) {
        this.stockBasicRepository = stockBasicRepository;
        this.pythonClient = pythonClient;
    }

    public Page<StockPageItem> page(
            String keyword,
            String stFilter,
            String latestPriceRange,
            String changeRateRange,
            String predictedVolatilityRange,
            String riskLevel,
            String sortField,
            String sortOrder,
            int pageNo,
            int pageSize) {
        Sort sort = resolveSort(sortField, sortOrder);
        Pageable pageable = PageRequest.of(Math.max(pageNo - 1, 0), pageSize, sort);
        return stockBasicRepository.findAll(
                buildStockSpecification(keyword, stFilter, latestPriceRange, changeRateRange, predictedVolatilityRange, riskLevel),
                pageable
        ).map(this::toItem);
    }

    public List<StockBasicDto> search(String keyword) {
        try {
            Map<String, Object> response = pythonClient.get("/py-api/stock/search", Map.of("keyword", keyword == null ? "" : keyword));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) response.getOrDefault("items", List.of());
            return items.stream().limit(20).map(item -> {
                String stockCode = String.valueOf(item.getOrDefault("stockCode", ""));
                return stockBasicRepository.findByStockCode(stockCode)
                        .map(this::toDto)
                        .orElseGet(() -> new StockBasicDto(
                        stockCode,
                        String.valueOf(item.getOrDefault("stockName", stockCode)),
                        null,
                        String.valueOf(item.getOrDefault("marketType", "CN")),
                        null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null
                        ));
            }).toList();
        } catch (Exception ex) {
            return stockBasicRepository.findAll().stream()
                    .filter(stock -> keyword == null || keyword.isBlank()
                            || stock.getStockCode().contains(keyword)
                            || stock.getStockName().contains(keyword))
                    .limit(20)
                    .map(this::toDto)
                    .toList();
        }
    }

    public StockBasicDto basic(String stockCode) {
        return stockBasicRepository.findByStockCode(stockCode)
                .map(this::toDto)
                .orElseThrow(() -> new BusinessException(1002, "股票不存在"));
    }

    private Sort resolveSort(String sortField, String sortOrder) {
        String field = switch (sortField == null ? "" : sortField) {
            case "predictedVolatility" -> "predictedVolatility";
            case "changeRate" -> "changeRate";
            case "latestPrice" -> "latestPrice";
            case "volume" -> "volume";
            case "totalScore" -> "totalScore";
            default -> "updateTime";
        };
        Sort.Direction direction = "asc".equalsIgnoreCase(sortOrder) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }

    private Specification<StockBasicEntity> buildStockSpecification(
            String keyword,
            String stFilter,
            String latestPriceRange,
            String changeRateRange,
            String predictedVolatilityRange,
            String riskLevel) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            String search = keyword == null ? "" : keyword.trim().toLowerCase();
            if (!search.isBlank()) {
                Expression<String> stockCode = builder.lower(root.get("stockCode"));
                Expression<String> stockName = builder.lower(root.get("stockName"));
                String pattern = "%" + search + "%";
                predicates.add(builder.or(
                        builder.like(stockCode, pattern),
                        builder.like(stockName, pattern)
                ));
            }

            if (StringUtils.hasText(stFilter)) {
                List<String> stValues = Arrays.asList(stFilter.split(","));
                List<Predicate> stPredicates = new ArrayList<>();
                for (String val : stValues) {
                    if ("include_st".equals(val)) {
                        stPredicates.add(builder.like(root.get("stockName"), "%*ST%"));
                    } else if ("exclude_st".equals(val)) {
                        stPredicates.add(builder.notLike(root.get("stockName"), "%*ST%"));
                    }
                }
                if (!stPredicates.isEmpty()) {
                    predicates.add(builder.or(stPredicates.toArray(new Predicate[0])));
                }
            }

            if (StringUtils.hasText(latestPriceRange)) {
                List<String> ranges = Arrays.asList(latestPriceRange.split(","));
                List<Predicate> pricePredicates = new ArrayList<>();
                for (String range : ranges) {
                    switch (range) {
                        case "lt10":
                            pricePredicates.add(builder.lessThan(root.get("latestPrice"), new BigDecimal("10")));
                            break;
                        case "10_50":
                            pricePredicates.add(builder.between(root.get("latestPrice"), new BigDecimal("10"), new BigDecimal("50")));
                            break;
                        case "50_100":
                            pricePredicates.add(builder.between(root.get("latestPrice"), new BigDecimal("50"), new BigDecimal("100")));
                            break;
                        case "ge100":
                            pricePredicates.add(builder.greaterThanOrEqualTo(root.get("latestPrice"), new BigDecimal("100")));
                            break;
                    }
                }
                if (!pricePredicates.isEmpty()) {
                    predicates.add(builder.or(pricePredicates.toArray(new Predicate[0])));
                }
            }

            if (StringUtils.hasText(changeRateRange)) {
                List<String> ranges = Arrays.asList(changeRateRange.split(","));
                List<Predicate> changePredicates = new ArrayList<>();
                for (String range : ranges) {
                    switch (range) {
                        case "up":
                            changePredicates.add(builder.greaterThan(root.get("changeRate"), BigDecimal.ZERO));
                            break;
                        case "down":
                            changePredicates.add(builder.lessThan(root.get("changeRate"), BigDecimal.ZERO));
                            break;
                        case "flat":
                            changePredicates.add(builder.equal(root.get("changeRate"), BigDecimal.ZERO));
                            break;
                        case "ge5":
                            changePredicates.add(builder.greaterThanOrEqualTo(root.get("changeRate"), new BigDecimal("5")));
                            break;
                        case "le_minus5":
                            changePredicates.add(builder.lessThanOrEqualTo(root.get("changeRate"), new BigDecimal("-5")));
                            break;
                    }
                }
                if (!changePredicates.isEmpty()) {
                    predicates.add(builder.or(changePredicates.toArray(new Predicate[0])));
                }
            }

            if (StringUtils.hasText(predictedVolatilityRange)) {
                List<String> ranges = Arrays.asList(predictedVolatilityRange.split(","));
                List<Predicate> volPredicates = new ArrayList<>();
                for (String range : ranges) {
                    switch (range) {
                        case "lt20":
                            volPredicates.add(builder.lessThan(root.get("predictedVolatility"), new BigDecimal("0.20")));
                            break;
                        case "20_30":
                            volPredicates.add(builder.between(root.get("predictedVolatility"), new BigDecimal("0.20"), new BigDecimal("0.30")));
                            break;
                        case "ge30":
                            volPredicates.add(builder.greaterThanOrEqualTo(root.get("predictedVolatility"), new BigDecimal("0.30")));
                            break;
                    }
                }
                if (!volPredicates.isEmpty()) {
                    predicates.add(builder.or(volPredicates.toArray(new Predicate[0])));
                }
            }

            if (StringUtils.hasText(riskLevel)) {
                List<String> levels = Arrays.asList(riskLevel.split(","));
                predicates.add(root.get("riskLevel").in(levels));
            }

            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }


    private StockPageItem toItem(StockBasicEntity entity) {
        return new StockPageItem(
                entity.getStockCode(),
                entity.getStockName(),
                entity.getIndustryName(),
                entity.getLatestPrice(),
                entity.getChangeRate(),
                entity.getVolume(),
                entity.getHistoricalVolatility(),
                entity.getImpliedVolatility(),
                entity.getPredictedVolatility(),
                entity.getTotalScore(),
                entity.getRiskLevel(),
                entity.getUpdateTime()
        );
    }

    private StockBasicDto toDto(StockBasicEntity entity) {
        return new StockBasicDto(
                entity.getStockCode(),
                entity.getStockName(),
                entity.getIndustryName(),
                entity.getMarketType(),
                entity.getLatestPrice(),
                entity.getChangeRate(),
                entity.getVolume(),
                entity.getHistoricalVolatility(),
                entity.getImpliedVolatility(),
                entity.getPredictedVolatility(),
                entity.getTotalScore(),
                entity.getRiskLevel(),
                entity.getUpdateTime()
        );
    }
}
