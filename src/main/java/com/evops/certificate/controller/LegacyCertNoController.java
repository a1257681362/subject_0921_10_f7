package com.evops.certificate.controller;

import com.evops.certificate.dto.LegacyCertNoRequest;
import com.evops.certificate.entity.LegacyCertNo;
import com.evops.certificate.service.LegacyCertNoService;
import com.evops.certificate.vo.LegacyImportPreview;
import com.evops.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 旧编号占用登记：把手工排号时代已发出、收不回的证号登记进系统，
 * 发号主动避让、验真可查（内部管理接口，走 Shiro HTTP Basic）。
 */
@RestController
@RequestMapping("/api/cert-legacy-nos")
public class LegacyCertNoController {

    private final LegacyCertNoService legacyService;

    public LegacyCertNoController(LegacyCertNoService legacyService) {
        this.legacyService = legacyService;
    }

    @PostMapping("/import/preview")
    public ApiResponse<LegacyImportPreview> preview(@RequestBody LegacyCertNoRequest request) {
        return ApiResponse.ok(legacyService.preview(request));
    }

    @PostMapping("/import")
    public ApiResponse<LegacyImportPreview> execute(@RequestBody LegacyCertNoRequest request) {
        return ApiResponse.ok(legacyService.execute(request));
    }

    @GetMapping
    public ApiResponse<List<LegacyCertNo>> list(@RequestParam(required = false) String projectCode,
                                                @RequestParam(required = false) String status) {
        return ApiResponse.ok(legacyService.list(projectCode, status));
    }
}
