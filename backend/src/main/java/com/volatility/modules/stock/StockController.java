package com.volatility.modules.stock;

import com.volatility.common.api.ApiResponse;
import com.volatility.common.config.RequestIdFilter;
import com.volatility.modules.stock.dto.StockBasicDto;
import com.volatility.modules.stock.dto.StockPageItem;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/stock")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping("/page")
    public ApiResponse<Map<String, Object>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String stFilter,
            @RequestParam(required = false) String latestPriceRange,
            @RequestParam(required = false) String changeRateRange,
            @RequestParam(required = false) String predictedVolatilityRange,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            HttpServletRequest request) {
        Page<StockPageItem> page = stockService.page(
                keyword,
                stFilter,
                latestPriceRange,
                changeRateRange,
                predictedVolatilityRange,
                riskLevel,
                sortField,
                sortOrder,
                pageNo,
                pageSize
        );
        Map<String, Object> data = Map.of(
                "records", page.getContent(),
                "total", page.getTotalElements(),
                "pageNo", pageNo,
                "pageSize", pageSize
        );
        return ApiResponse.success(data, requestId(request));
    }

    @GetMapping("/search")
    public ApiResponse<List<StockBasicDto>> search(@RequestParam(required = false) String keyword, HttpServletRequest request) {
        return ApiResponse.success(stockService.search(keyword), requestId(request));
    }

    @GetMapping("/{code}/basic")
    public ApiResponse<StockBasicDto> basic(@PathVariable("code") String code, HttpServletRequest request) {
        return ApiResponse.success(stockService.basic(code), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(RequestIdFilter.REQUEST_ID));
    }
}
