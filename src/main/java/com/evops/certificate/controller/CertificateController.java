package com.evops.certificate.controller;

import com.evops.certificate.dto.CertIssueRequest;
import com.evops.certificate.dto.CertRevokeRequest;
import com.evops.certificate.entity.Certificate;
import com.evops.certificate.entity.CertificateRevoke;
import com.evops.certificate.service.CertificateService;
import com.evops.certificate.vo.CertIssuePreview;
import com.evops.certificate.vo.CertIssueResult;
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

/**
 * 证书发证 / 作废 / 查档（内部管理接口，走 Shiro HTTP Basic）。
 * 发证两步预检；发证后信息只读；作废留记录、不可撤销。
 */
@RestController
@RequestMapping("/api/certificates")
public class CertificateController {

    private final CertificateService certificateService;

    public CertificateController(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    /** 发证预检：看可发/跳过与拟发编号。 */
    @PostMapping("/issue/preview")
    public ApiResponse<CertIssuePreview> issuePreview(@RequestBody CertIssueRequest request) {
        return ApiResponse.ok(certificateService.previewIssue(request));
    }

    /** 发证执行：编号 = 年度 + 批次 + 流水 + 校验码；整批一事务，撞号顺延不重号。 */
    @PostMapping("/issue")
    public ApiResponse<CertIssueResult> issue(@RequestBody CertIssueRequest request) {
        return ApiResponse.ok(certificateService.executeIssue(request));
    }

    /** 按编号精确查证书（管理视图）。 */
    @GetMapping("/by-cert-no")
    public ApiResponse<Certificate> byCertNo(@RequestParam String certNo) {
        return ApiResponse.ok(certificateService.getByCertNo(certNo));
    }

    /** 按批次列证书。 */
    @GetMapping
    public ApiResponse<List<Certificate>> listByBatch(@RequestParam Long batchId) {
        return ApiResponse.ok(certificateService.listByBatch(batchId));
    }

    @GetMapping("/{id}")
    public ApiResponse<Certificate> get(@PathVariable Long id) {
        return ApiResponse.ok(certificateService.requireCertificate(id));
    }

    /** 作废：只追加留记录，已作废不可重复作废。 */
    @PostMapping("/{id}/revoke")
    public ApiResponse<Certificate> revoke(@PathVariable Long id,
                                           @Valid @RequestBody CertRevokeRequest request) {
        return ApiResponse.ok(certificateService.revoke(id, request.getReason()));
    }

    /** 查作废记录（作废留痕）。 */
    @GetMapping("/{id}/revokes")
    public ApiResponse<List<CertificateRevoke>> revokes(@PathVariable Long id) {
        return ApiResponse.ok(certificateService.listRevokes(id));
    }
}
