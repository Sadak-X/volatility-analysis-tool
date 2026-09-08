package com.volatility.modules.file.entity;

import com.volatility.common.util.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "data_source_file")
public class DataSourceFileEntity extends BaseEntity {

    @Column(nullable = false, unique = true, length = 32)
    private String fileNo;

    @Column(nullable = false, length = 255)
    private String originalFileName;

    @Column(nullable = false, length = 255)
    private String storedFileName;

    @Column(nullable = false, length = 500)
    private String filePath;

    @Column(length = 128)
    private String contentType;

    @Column(nullable = false)
    private Long fileSize;

    @Column(nullable = false, length = 64)
    private String fileSha256;

    @Column(nullable = false, length = 16)
    private String sourceType;

    @Column(nullable = false, length = 16)
    private String status;
}
