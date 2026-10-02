package com.evops.certificate.common;

import com.evops.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 验真窗口小流量限流：按来源 IP 滑动窗口（每分钟 N 次，N 可配，默认 30）。
 * 用人单位下周先小流量接接口；需要放大时改 evops.cert.verify.rate-limit-per-minute，
 * 设为 0 或负数表示不限流（仅建议在内网压测时临时使用）。
 */
@Component
public class VerifyRateLimiter {

    private final int limitPerMinute;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public VerifyRateLimiter(
            @Value("${evops.cert.verify.rate-limit-per-minute:30}") int limitPerMinute) {
        this.limitPerMinute = limitPerMinute;
    }

    public void check(String clientIp) {
        if (limitPerMinute <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        long windowStart = now - 60_000L;
        Deque<Long> deque = hits.computeIfAbsent(clientIp, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst() < windowStart) {
                deque.pollFirst();
            }
            if (deque.size() >= limitPerMinute) {
                throw new BusinessException(CertConstants.VERIFY_RATE_LIMITED,
                        "验真请求过于频繁，已限流：每分钟限 " + limitPerMinute
                                + " 次，请稍后再试");
            }
            deque.addLast(now);
        }
        // 顺手清理长期空闲 IP，避免映射无限增长
        if (hits.size() > 10_000) {
            evictIdle(now);
        }
    }

    private void evictIdle(long now) {
        for (Map.Entry<String, Deque<Long>> entry : hits.entrySet()) {
            Deque<Long> deque = entry.getValue();
            synchronized (deque) {
                while (!deque.isEmpty() && deque.peekFirst() < now - 300_000L) {
                    deque.pollFirst();
                }
                if (deque.isEmpty()) {
                    hits.remove(entry.getKey());
                }
            }
        }
    }
}
