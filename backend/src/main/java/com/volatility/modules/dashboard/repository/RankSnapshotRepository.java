package com.volatility.modules.dashboard.repository;

import com.volatility.modules.dashboard.entity.RankSnapshotEntity;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface RankSnapshotRepository extends JpaRepository<RankSnapshotEntity, Long> {

    List<RankSnapshotEntity> findBySortScopeAndBizDateOrderByRankNoAsc(String sortScope, LocalDate bizDate);

    @Transactional
    @Modifying
    @Query("delete from RankSnapshotEntity r where r.sortScope = :sortScope and r.bizDate = :bizDate")
    void deleteBySortScopeAndBizDate(@Param("sortScope") String sortScope, @Param("bizDate") LocalDate bizDate);
}
