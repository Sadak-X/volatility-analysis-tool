package com.volatility.modules.file;

import com.volatility.common.config.AppProperties;
import com.volatility.common.exception.BusinessException;
import com.volatility.modules.file.dto.FileUploadResponse;
import com.volatility.modules.file.entity.DataSourceFileEntity;
import com.volatility.modules.file.repository.DataSourceFileRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("xls", "xlsx");

    private final DataSourceFileRepository dataSourceFileRepository;
    private final AppProperties appProperties;

    public FileStorageService(DataSourceFileRepository dataSourceFileRepository, AppProperties appProperties) {
        this.dataSourceFileRepository = dataSourceFileRepository;
        this.appProperties = appProperties;
    }

    public FileUploadResponse uploadExcel(MultipartFile file) {
        validateExcel(file);

        try {
            Path uploadDir = Path.of(appProperties.getUploadPath()).toAbsolutePath().normalize();
            Files.createDirectories(uploadDir);

            String extension = getExtension(file.getOriginalFilename());
            String fileNo = "F" + DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(LocalDateTime.now());
            String storedFileName = fileNo + "-" + UUID.randomUUID().toString().replace("-", "") + "." + extension;
            Path target = uploadDir.resolve(storedFileName);

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }

            DataSourceFileEntity entity = new DataSourceFileEntity();
            entity.setFileNo(fileNo);
            entity.setOriginalFileName(file.getOriginalFilename());
            entity.setStoredFileName(storedFileName);
            entity.setFilePath(target.toString());
            entity.setContentType(file.getContentType());
            entity.setFileSize(file.getSize());
            entity.setFileSha256(sha256(target));
            entity.setSourceType("EXCEL");
            entity.setStatus("UPLOADED");
            dataSourceFileRepository.save(entity);

            return new FileUploadResponse(entity.getFileNo(), entity.getOriginalFileName(), entity.getFileSize(), entity.getContentType());
        } catch (IOException ex) {
            throw new BusinessException(2001, "文件保存失败");
        }
    }

    public DataSourceFileEntity getByFileNo(String fileNo) {
        return dataSourceFileRepository.findByFileNo(fileNo)
                .orElseThrow(() -> new BusinessException(1002, "上传文件不存在"));
    }

    private void validateExcel(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(2001, "请上传 Excel 文件");
        }
        String extension = getExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new BusinessException(2001, "仅支持 xls/xlsx 文件");
        }
        if (file.getSize() > 20L * 1024 * 1024) {
            throw new BusinessException(2001, "文件大小不能超过 20MB");
        }
    }

    private String getExtension(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    private String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = Files.readAllBytes(file);
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception ex) {
            throw new IOException("计算文件摘要失败", ex);
        }
    }
}
