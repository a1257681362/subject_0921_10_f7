package com.evops.certificate.common;

import com.evops.certificate.dto.CertNumberParams;

/**
 * 编号拼装：年度 + 批次 + 流水 + 末位校验码。
 * 主体只含字母数字（分隔符在拼校验码时剔除），保证校验码口径稳定，可被外部验真端独立复算。
 */
public final class CertNumberFormatter {

    private CertNumberFormatter() {
    }

    /** 年度段：yearDigits=2 取末两位，4 取四位。 */
    public static String yearPart(String year, int yearDigits) {
        String y = year == null ? "" : year.trim();
        if (y.length() < 4) {
            // 允许 "26" 这种输入，按 2 位原样
            if (yearDigits == 2 && y.length() == 2) {
                return y;
            }
            throw new IllegalArgumentException("年度须为 4 位数字：" + year);
        }
        if (yearDigits == 2) {
            return y.substring(2);
        }
        return y;
    }

    /** 流水段：定宽补零，超宽自然扩展（不截断，不丢号）。 */
    public static String seqPart(int seq, int seqLength) {
        String s = Integer.toString(seq);
        if (s.length() >= seqLength) {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0, pad = seqLength - s.length(); i < pad; i++) {
            sb.append('0');
        }
        return sb.append(s).toString();
    }

    /** 拼编号主体（不含校验码）。 */
    public static String body(CertNumberParams p, String year, String batchCode, int seq) {
        String sep = p.getSeparator() == null ? "" : p.getSeparator();
        return p.getPrefix()
                + sep + yearPart(year, p.getYearDigits())
                + sep + normalizeSegment(batchCode, "批次编码")
                + sep + seqPart(seq, p.getSeqLength());
    }

    /** 完整编号：主体 + 校验码（NONE 时主体即完整编号）。 */
    public static String fullNo(CertNumberParams p, String year, String batchCode, int seq) {
        String body = body(p, year, batchCode, seq);
        if (CertConstants.CHECKSUM_NONE.equals(p.getChecksumAlgo())) {
            return body;
        }
        // 校验码挂在主体末尾；带分隔符的编号，校验码前也补一个分隔符，肉眼/抄录时边界清楚
        String sep = p.getSeparator() == null ? "" : p.getSeparator();
        return body + sep + ChecksumCodec.compute(stripSep(body, sep));
    }

    /** 从完整编号还原无分隔符主体（用于复算校验码）。 */
    public static String stripSep(String no, String sep) {
        return sep == null || sep.isEmpty() ? no : no.replace(sep, "");
    }

    /** 校验完整编号的校验码是否正确（NONE 算法恒为 false——无校验码编号不做格式背书）。 */
    public static boolean checksumValid(CertNumberParams p, String fullNo) {
        if (CertConstants.CHECKSUM_NONE.equals(p.getChecksumAlgo())) {
            return true;
        }
        String sep = p.getSeparator() == null ? "" : p.getSeparator();
        String compact = stripSep(fullNo, sep);
        return ChecksumCodec.isValid(compact);
    }

    private static String normalizeSegment(String s, String label) {
        if (s == null || s.trim().isEmpty()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        String v = s.trim().toUpperCase();
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z');
            if (!ok) {
                throw new IllegalArgumentException(label + "只允许字母与数字：" + s);
            }
        }
        return v;
    }
}
