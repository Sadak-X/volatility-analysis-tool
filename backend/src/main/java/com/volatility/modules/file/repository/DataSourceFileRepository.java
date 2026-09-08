package com.volatility.modules.file.repository;

import com.volatility.modules.file.entity.DataSourceFileEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataSourceFileRepository extends JpaRepository<DataSourceFileEntity, Long> {

    Optional<DataSourceFileEntity> findByFileNo(String fileNo);
}
