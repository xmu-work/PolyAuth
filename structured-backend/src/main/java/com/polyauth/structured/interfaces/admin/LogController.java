package com.polyauth.structured.interfaces.admin;

import com.polyauth.structured.application.log.LogService;
import com.polyauth.structured.common.result.PageResult;
import com.polyauth.structured.common.result.Result;
import com.polyauth.structured.interfaces.admin.vo.LogVO;
import com.polyauth.structured.infrastructure.persistence.entity.IamLog;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 统一日志查询控制器。普通操作日志和权限变更日志均从 {@code /logs} 查询。
 */
@Validated
@RestController
@RequestMapping("/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;

    @GetMapping
    public Result<PageResult<LogVO>> list(
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
        Page<IamLog> result = logService.queryLogs(
                PageRequest.of(Math.max(page, 1) - 1, size, Sort.by(Sort.Direction.DESC, "operateTime")),
                userId, systemCode, logType, username, action, success, startTime, endTime);
        return Result.success(PageResult.of(result.getTotalElements(), page, size,
                result.getContent().stream().map(LogVO::from).toList()));
    }

    /**
     * 导出筛选后的审计日志为 UTF-8 CSV。
     */
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
        List<IamLog> logs = logService.exportLogs(
                userId, systemCode, logType, username, action, success, startTime, endTime);
        String filename = "audit-logs-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".csv";
        byte[] body = ("\uFEFF" + logService.toCsv(logs)).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }
}
