package com.evops.certificate.common;

import com.evops.common.BusinessException;

/**
 * 证书编号校验码：ISO 7064:1983 MOD 37-2（36 字符集两位校验符，习惯称 MOD36-2）。
 * 字符集 0-9A-Z（数值 0..35），基数 36、模 37，可检出单字符错误与相邻换位，
 * 手工抄号 / 口头报号也能挡住误录。
 */
public final class ChecksumCodec {

    private static final int RADIX = 36;
    private static final int MOD = 37;
    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private ChecksumCodec() {
    }

    /**
     * 为编号主体计算两位校验符（大写）。
     * 标准递推：p = 模数 - 基数 + 1 = 2，s 从 p 起，逐字符 s = (s*36 + v) mod 37；
     * 校验值 C = 模数 + 1 - s（落在 37 时按 0），用两位 36 进制表达（36 → "10"）。
     */
    public static String compute(String body) {
        if (body == null || !isAlnum(body)) {
            throw new BusinessException("CHECKSUM_BODY_INVALID",
                    "校验码计算主体不能为空且只能含字母与数字");
        }
        int s = MOD - RADIX + 1;
        for (int i = 0; i < body.length(); i++) {
            s = (s * RADIX + valueOf(Character.toUpperCase(body.charAt(i)))) % MOD;
        }
        int check = MOD + 1 - s;
        if (check == MOD) {
            check = 0;
        }
        return "" + ALPHABET.charAt(check / RADIX) + ALPHABET.charAt(check % RADIX);
    }

    /** 校验完整编号（末两位为校验符）：整串递推结果为 1 即合法。格式非法返回 false，不抛异常。 */
    public static boolean isValid(String full) {
        if (full == null || full.length() < 3 || !isAlnum(full)) {
            return false;
        }
        int s = MOD - RADIX + 1;
        for (int i = 0; i < full.length(); i++) {
            s = (s * RADIX + valueOf(Character.toUpperCase(full.charAt(i)))) % MOD;
        }
        return s == 1;
    }

    private static int valueOf(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'A' && c <= 'Z') {
            return c - 'A' + 10;
        }
        throw new BusinessException("CHECKSUM_CHAR_ILLEGAL",
                "编号主体只允许字母与数字，非法字符：" + c);
    }

    private static boolean isAlnum(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z');
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
