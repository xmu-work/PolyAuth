package com.polyauth.oop.interfaces.controller;

import com.polyauth.oop.application.usecase.AuditQueryUseCase;
import com.polyauth.oop.domain.model.AuditEntry;
import com.polyauth.oop.domain.model.AuditQuery;
import com.polyauth.oop.domain.model.DomainPage;
import com.polyauth.oop.interfaces.common.ApiResult;
import com.polyauth.oop.interfaces.vo.LogPageVO;
import com.polyauth.oop.interfaces.vo.LogVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 统一审计日志 HTTP 适配器。
 */
@Validated
@RestController
@RequestMapping("/logs")
public class LogController {

    private final AuditQueryUseCase useCase;

    /**
     * 注入审计查询用例。
     */
    public LogController(AuditQueryUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    public ApiResult<LogPageVO> list(
            @RequestParam(defaultValue = "1")
            @Min(1)
            int page,
            @RequestParam(defaultValue = "20")
            @Min(1)
            @Max(100)
            int size,
            @RequestParam(required = false)
            @Positive
            Long userId,
            @RequestParam(required = false)
            String systemCode,
            @RequestParam(required = false)
            String logType,
            @RequestParam(required = false)
            @Size(max = 128)
            String username,
            @RequestParam(required = false)
            @Size(max = 128)
            String action,
            @RequestParam(required = false)
            @Min(0)
            @Max(1)
            Integer success,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime endTime) {
        DomainPage<AuditEntry> result = useCase.page(new AuditQuery(
                        userId, systemCode, logType, username, action, success,
                        startTime, endTime), page, size);
        return ApiResult.success(new LogPageVO(
                result.total(),
                result.page(),
                result.pageSize(),
                result.items().stream().map(LogVO::from).toList()));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false)
            @Positive
            Long userId,
            @RequestParam(required = false)
            @Size(max = 64)
            String systemCode,
            @RequestParam(required = false)
            @Size(max = 32)
            String logType,
            @RequestParam(required = false)
            @Size(max = 128)
            String username,
            @RequestParam(required = false)
            @Size(max = 128)
            String action,
            @RequestParam(required = false)
            @Min(0)
            @Max(1)
            Integer success,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime endTime) {
        List<AuditEntry> entries = useCase.export(new AuditQuery(
                userId, systemCode, logType, username, action, success,
                startTime, endTime));
        String filename = "audit-logs-"
                + LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".csv";
        byte[] body = ("\uFEFF" + useCase.csv(entries))
                .getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename)
                                .build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }
}
