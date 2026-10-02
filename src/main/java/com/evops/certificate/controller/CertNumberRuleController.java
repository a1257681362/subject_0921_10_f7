package com.evops.certificate.controller;

import com.evops.certificate.dto.CertNumberTrialRequest;
import com.evops.certificate.dto.CertRuleRequest;
import com.evops.certificate.dto.CertRuleVersionRequest;
import com.evops.certificate.entity.CertNumberRule;
import com.evops.certificate.entity.CertNumberRuleVersion;
import com.evops.certificate.service.CertNumberRuleService;
import com.evops.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * 编号规则模块：规则按项目建档，版本带状态（草稿/生效/停用），编号参数发布即冻结。
 */
@RestController
@RequestMapping("/api/cert-number-rules")
public class CertNumberRuleController {

    private final CertNumberRuleService ruleService;

    public CertNumberRuleController(CertNumberRuleService ruleService) {
        this.ruleService = ruleService;
    }

    // ---------- 规则模块 ----------

    @PostMapping
    public ApiResponse<CertNumberRule> createRule(@Valid @RequestBody CertRuleRequest request) {
        return ApiResponse.ok(ruleService.createRule(request));
    }

    @GetMapping
    public ApiResponse<List<CertNumberRule>> listRules(@RequestParam(required = false) String projectCode) {
        return ApiResponse.ok(ruleService.listRules(projectCode));
    }

    @GetMapping("/{ruleId}")
    public ApiResponse<CertNumberRule> getRule(@PathVariable Long ruleId) {
        return ApiResponse.ok(ruleService.getRule(ruleId));
    }

    // ---------- 规则版本 ----------

    @PostMapping("/{ruleId}/versions")
    public ApiResponse<CertNumberRuleVersion> createDraft(@PathVariable Long ruleId,
                                                          @Valid @RequestBody CertRuleVersionRequest request) {
        return ApiResponse.ok(ruleService.createDraftVersion(ruleId, request));
    }

    @GetMapping("/{ruleId}/versions")
    public ApiResponse<List<CertNumberRuleVersion>> listVersions(@PathVariable Long ruleId) {
        return ApiResponse.ok(ruleService.listVersions(ruleId));
    }

    @GetMapping("/versions/{versionId}")
    public ApiResponse<CertNumberRuleVersion> getVersion(@PathVariable Long versionId) {
        return ApiResponse.ok(ruleService.getVersion(versionId));
    }

    /** 发布：草稿→生效，自动停用同规则旧生效版。 */
    @PostMapping("/versions/{versionId}/publish")
    public ApiResponse<CertNumberRuleVersion> publish(@PathVariable Long versionId) {
        return ApiResponse.ok(ruleService.publishVersion(versionId));
    }

    /** 人工停用生效版本。 */
    @PostMapping("/versions/{versionId}/disable")
    public ApiResponse<CertNumberRuleVersion> disable(@PathVariable Long versionId) {
        return ApiResponse.ok(ruleService.disableVersion(versionId));
    }

    /** 按版本试算编号（年度+批次+流水+校验码），不写数据。 */
    @PostMapping("/versions/{versionId}/trial")
    public ApiResponse<Map<String, Object>> trial(@PathVariable Long versionId,
                                                  @Valid @RequestBody CertNumberTrialRequest request) {
        return ApiResponse.ok(ruleService.trial(versionId, request.getYear(),
                request.getBatchCode(), request.getSeq()));
    }
}
