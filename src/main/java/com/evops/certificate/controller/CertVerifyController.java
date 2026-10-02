package com.evops.certificate.controller;

import com.evops.certificate.service.CertVerifyService;
import com.evops.certificate.vo.CertVerifyView;
import com.evops.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * 对外验真窗口：用人单位凭编号 + 姓名核验。
 * 仍走 HTTP Basic（bootstrap/bootstrap 或后续为用人单位单独开户），只回有效性与批次，
 * 不带证件号/电话等隐私字段；按来源 IP 小流量限流（evops.cert.verify.rate-limit-per-minute）。
 */
@RestController
public class CertVerifyController {

    private final CertVerifyService verifyService;

    public CertVerifyController(CertVerifyService verifyService) {
        this.verifyService = verifyService;
    }

    /** GET 便于用人单位直接对接；参数含中文时做 URL 编码（curl -G --data-urlencode）。 */
    @GetMapping("/api/cert-verify")
    public ApiResponse<CertVerifyView> verifyByGet(@RequestParam String certNo,
                                                   @RequestParam String name,
                                                   HttpServletRequest request) {
        return ApiResponse.ok(verifyService.verify(certNo, name, clientIp(request)));
    }

    /** POST 表单形式同样开放，便于不便于把姓名拼进 URL 的对接方。 */
    @PostMapping("/api/cert-verify")
    public ApiResponse<CertVerifyView> verifyByPost(@RequestParam String certNo,
                                                    @RequestParam String name,
                                                    HttpServletRequest request) {
        return ApiResponse.ok(verifyService.verify(certNo, name, clientIp(request)));
    }

    /** 取真实来源 IP：先走代理链 X-Forwarded-For 首个，再回落到直连地址。 */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.trim().isEmpty()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.trim().isEmpty()) {
            return real.trim();
        }
        return request.getRemoteAddr();
    }
}
