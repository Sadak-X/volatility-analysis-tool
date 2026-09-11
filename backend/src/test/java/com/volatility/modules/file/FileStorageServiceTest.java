package com.volatility.modules.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.volatility.common.config.AppProperties;
import com.volatility.common.exception.BusinessException;
import com.volatility.modules.file.dto.FileUploadResponse;
import com.volatility.modules.file.entity.DataSourceFileEntity;
import com.volatility.modules.file.repository.DataSourceFileRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class FileStorageServiceTest {

    @Mock
    private DataSourceFileRepository dataSourceFileRepository;

    @Mock
    private AppProperties appProperties;

    private FileStorageService fileStorageService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        when(appProperties.getUploadPath()).thenReturn(tempDir.toString());
        fileStorageService = new FileStorageService(dataSourceFileRepository, appProperties);
    }

    // TC-FILE-01
    @Test
    void uploadExcelSavesXlsxFileWhenValid() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("test.xlsx");
        when(file.getSize()).thenReturn(5L * 1024 * 1024);
        when(file.getContentType()).thenReturn("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream("dummy xlsx content".getBytes()));

        FileUploadResponse response = fileStorageService.uploadExcel(file);

        assertThat(response.originalFileName()).isEqualTo("test.xlsx");
        assertThat(response.fileSize()).isEqualTo(5L * 1024 * 1024);
        assertThat(response.contentType()).isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(response.fileId()).startsWith("F");

        ArgumentCaptor<DataSourceFileEntity> captor = ArgumentCaptor.forClass(DataSourceFileEntity.class);
        verify(dataSourceFileRepository).save(captor.capture());
        DataSourceFileEntity saved = captor.getValue();
        assertThat(saved.getFileNo()).isEqualTo(response.fileId());
        assertThat(saved.getOriginalFileName()).isEqualTo("test.xlsx");
        assertThat(saved.getFileSize()).isEqualTo(5L * 1024 * 1024);
        assertThat(saved.getContentType()).isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(saved.getStatus()).isEqualTo("UPLOADED");
        assertThat(saved.getSourceType()).isEqualTo("EXCEL");
        assertThat(saved.getFileSha256()).isNotBlank();
        assertThat(Files.exists(tempDir.resolve(saved.getStoredFileName()))).isTrue();
    }

    // TC-FILE-02
    @Test
    void uploadExcelSavesXlsFileWhenValid() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("test.xls");
        when(file.getSize()).thenReturn(5L * 1024 * 1024);
        when(file.getContentType()).thenReturn("application/vnd.ms-excel");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream("dummy xls content".getBytes()));

        FileUploadResponse response = fileStorageService.uploadExcel(file);

        assertThat(response.originalFileName()).isEqualTo("test.xls");
        assertThat(response.fileSize()).isEqualTo(5L * 1024 * 1024);
        assertThat(response.contentType()).isEqualTo("application/vnd.ms-excel");
        assertThat(response.fileId()).startsWith("F");

        ArgumentCaptor<DataSourceFileEntity> captor = ArgumentCaptor.forClass(DataSourceFileEntity.class);
        verify(dataSourceFileRepository).save(captor.capture());
        DataSourceFileEntity saved = captor.getValue();
        assertThat(saved.getOriginalFileName()).isEqualTo("test.xls");
        assertThat(saved.getFileSize()).isEqualTo(5L * 1024 * 1024);
        assertThat(saved.getContentType()).isEqualTo("application/vnd.ms-excel");
        assertThat(saved.getStatus()).isEqualTo("UPLOADED");
        assertThat(saved.getSourceType()).isEqualTo("EXCEL");
    }

    // TC-FILE-03
    @Test
    void uploadExcelThrowsWhenFileFormatIsUnsupported() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("test.pdf");

        assertThatThrownBy(() -> fileStorageService.uploadExcel(file))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 2001)
                .hasMessage("仅支持 xls/xlsx 文件");
    }

    // TC-FILE-04
    @Test
    void uploadExcelThrowsWhenFileIsEmpty() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(true);

        assertThatThrownBy(() -> fileStorageService.uploadExcel(file))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 2001)
                .hasMessage("请上传 Excel 文件");
    }

    // TC-FILE-05
    @Test
    void uploadExcelSucceedsWhenFileSizeIsExactly20MB() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("test.xlsx");
        when(file.getSize()).thenReturn(20L * 1024 * 1024);
        when(file.getContentType()).thenReturn("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream("dummy content".getBytes()));

        FileUploadResponse response = fileStorageService.uploadExcel(file);

        assertThat(response.originalFileName()).isEqualTo("test.xlsx");
        assertThat(response.fileSize()).isEqualTo(20L * 1024 * 1024);
        assertThat(response.fileId()).startsWith("F");

        ArgumentCaptor<DataSourceFileEntity> captor = ArgumentCaptor.forClass(DataSourceFileEntity.class);
        verify(dataSourceFileRepository).save(captor.capture());
        DataSourceFileEntity saved = captor.getValue();
        assertThat(saved.getFileSize()).isEqualTo(20L * 1024 * 1024);
    }

    // TC-FILE-06
    @Test
    void uploadExcelThrowsWhenFileSizeExceeds20MB() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("test.xlsx");
        when(file.getSize()).thenReturn((long) (20.1 * 1024 * 1024)); // 20.1MB

        assertThatThrownBy(() -> fileStorageService.uploadExcel(file))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 2001)
                .hasMessage("文件大小不能超过 20MB");
    }

    // TC-FILE-07
    @Test
    void getByFileNoReturnsEntityWhenFound() {
        DataSourceFileEntity entity = new DataSourceFileEntity();
        entity.setFileNo("F123456");
        when(dataSourceFileRepository.findByFileNo("F123456")).thenReturn(Optional.of(entity));

        DataSourceFileEntity result = fileStorageService.getByFileNo("F123456");

        assertThat(result).isEqualTo(entity);
    }

    // TC-FILE-08
    @Test
    void getByFileNoThrowsWhenNotFound() {
        when(dataSourceFileRepository.findByFileNo("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileStorageService.getByFileNo("nonexistent"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 1002)
                .hasMessage("上传文件不存在");
    }
}