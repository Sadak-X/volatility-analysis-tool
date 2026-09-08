package com.volatility.modules.system;

import com.volatility.modules.auth.AuthService;
import com.volatility.modules.auth.entity.UserEntity;
import com.volatility.modules.auth.repository.UserRepository;
import com.volatility.modules.dashboard.entity.RankSnapshotEntity;
import com.volatility.modules.dashboard.repository.RankSnapshotRepository;
import com.volatility.modules.stock.entity.StockBasicEntity;
import com.volatility.modules.stock.repository.StockBasicRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Comparator;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DemoDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final AuthService authService;
    private final StockBasicRepository stockBasicRepository;
    private final RankSnapshotRepository rankSnapshotRepository;

    public DemoDataInitializer(
            UserRepository userRepository,
            AuthService authService,
            StockBasicRepository stockBasicRepository,
            RankSnapshotRepository rankSnapshotRepository) {
        this.userRepository = userRepository;
        this.authService = authService;
        this.stockBasicRepository = stockBasicRepository;
        this.rankSnapshotRepository = rankSnapshotRepository;
    }

    @Override
    public void run(String... args) {
        initAdmin();
        initStocks();
        initRanks();
    }

    private void initAdmin() {
        if (userRepository.findByUsername("admin").isPresent()) {
            return;
        }
        UserEntity user = new UserEntity();
        user.setUsername("admin");
        user.setPasswordHash(authService.encode("admin123"));
        user.setNickname("系统管理员");
        user.setStatus(1);
        user.setRoleCode("ADMIN");
        userRepository.save(user);
    }

    private void initStocks() {
        if (stockBasicRepository.count() > 0) {
            return;
        }
        List<StockBasicEntity> stocks = List.of(
                stock("600519", "贵州茅台", "白酒", 1688.80, 1.25, 2315000, 0.182300, 0.205200, 0.221400, 79.50, "MEDIUM"),
                stock("002594", "比亚迪", "新能源汽车", 243.60, 2.36, 6251000, 0.246800, 0.264500, 0.291100, 85.20, "HIGH"),
                stock("300750", "宁德时代", "新能源电池", 191.20, -0.88, 5128000, 0.229400, 0.248100, 0.268700, 76.80, "MEDIUM"),
                stock("601318", "中国平安", "保险", 47.62, 0.73, 3812000, 0.143200, 0.156100, 0.167300, 64.90, "LOW"),
                stock("000333", "美的集团", "家电", 67.18, 1.10, 2845000, 0.132100, 0.144800, 0.151200, 60.30, "LOW")
        );
        stockBasicRepository.saveAll(stocks);
    }

    private void initRanks() {
        LocalDate bizDate = LocalDate.now();
        if (!rankSnapshotRepository.findBySortScopeAndBizDateOrderByRankNoAsc("WEEK", bizDate).isEmpty()) {
            return;
        }
        List<StockBasicEntity> stocks = stockBasicRepository.findAll().stream()
                .sorted(Comparator.comparing(StockBasicEntity::getPredictedVolatility,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        for (String scope : List.of("WEEK", "MONTH", "YEAR")) {
            rankSnapshotRepository.deleteBySortScopeAndBizDate(scope, bizDate);
            for (int index = 0; index < stocks.size(); index++) {
                StockBasicEntity stock = stocks.get(index);
                RankSnapshotEntity snapshot = new RankSnapshotEntity();
                snapshot.setBizDate(bizDate);
                snapshot.setSortScope(scope);
                snapshot.setStockCode(stock.getStockCode());
                snapshot.setStockName(stock.getStockName());
                snapshot.setPredVolatility(stock.getPredictedVolatility());
                snapshot.setChangeRate(stock.getChangeRate());
                snapshot.setLastPrice(stock.getLatestPrice());
                snapshot.setRiskLevel(stock.getRiskLevel());
                snapshot.setRankNo(index + 1);
                snapshot.setSnapshotTime(LocalDateTime.now());
                rankSnapshotRepository.save(snapshot);
            }
        }
    }

    private StockBasicEntity stock(
            String code,
            String name,
            String industry,
            double latestPrice,
            double changeRate,
            double volume,
            double historicalVolatility,
            double impliedVolatility,
            double predictedVolatility,
            double totalScore,
            String riskLevel) {
        StockBasicEntity entity = new StockBasicEntity();
        entity.setStockCode(code);
        entity.setStockName(name);
        entity.setIndustryName(industry);
        entity.setMarketType("CN");
        entity.setLatestPrice(BigDecimal.valueOf(latestPrice));
        entity.setChangeRate(BigDecimal.valueOf(changeRate));
        entity.setVolume(BigDecimal.valueOf(volume));
        entity.setHistoricalVolatility(BigDecimal.valueOf(historicalVolatility));
        entity.setImpliedVolatility(BigDecimal.valueOf(impliedVolatility));
        entity.setPredictedVolatility(BigDecimal.valueOf(predictedVolatility));
        entity.setTotalScore(BigDecimal.valueOf(totalScore));
        entity.setRiskLevel(riskLevel);
        return entity;
    }
}
