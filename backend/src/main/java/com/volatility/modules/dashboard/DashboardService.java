package com.volatility.modules.dashboard;

import com.volatility.modules.dashboard.dto.RankSnapshotDto;
import com.volatility.modules.dashboard.repository.RankSnapshotRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private final RankSnapshotRepository rankSnapshotRepository;

    public DashboardService(RankSnapshotRepository rankSnapshotRepository) {
        this.rankSnapshotRepository = rankSnapshotRepository;
    }

    public List<RankSnapshotDto>highVolatility(String scope, int limit) {
        LocalDate bizDate = LocalDate.now();
        return rankSnapshotRepository.findBySortScopeAndBizDateOrderByRankNoAsc(scope.toUpperCase(), bizDate).stream()
                .limit(limit)
                .map(snapshot -> new RankSnapshotDto(
                        snapshot.getBizDate(),
                        snapshot.getSortScope(),
                        snapshot.getStockCode(),
                        snapshot.getStockName(),
                        snapshot.getPredVolatility(),
                        snapshot.getChangeRate(),
                        snapshot.getLastPrice(),
                        snapshot.getRiskLevel(),
                        snapshot.getRankNo(),
                        snapshot.getSnapshotTime()
                ))
                .toList();
    }
}
