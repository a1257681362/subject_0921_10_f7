package com.evops.certificate.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.certificate.common.CertConstants;
import com.evops.certificate.common.CertNumberFormatter;
import com.evops.certificate.dto.CertNumberParams;
import com.evops.certificate.dto.CertRuleRequest;
import com.evops.certificate.dto.CertRuleVersionRequest;
import com.evops.certificate.entity.CertNumberRule;
import com.evops.certificate.entity.CertNumberRuleVersion;
import com.evops.certificate.mapper.CertNumberRuleMapper;
import com.evops.certificate.mapper.CertNumberRuleVersionMapper;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 编号规则：
 * - 规则按项目建档（一个项目一套），规则下挂多个版本；
 * - 版本状态 DRAFT → ACTIVE → DISABLED：发布即冻结编号参数，并自动停用同规则旧生效版；
 * - 发号只认项目当前 ACTIVE 版本，证书永久保留版本号——规则调整只管新证；
 * - 停用不删版本，历史编号口径可追溯。
 */
@Service
public class CertNumberRuleService {

    private final CertNumberRuleMapper ruleMapper;
    private final CertNumberRuleVersionMapper versionMapper;
    private final ObjectMapper objectMapper;

    public CertNumberRuleService(CertNumberRuleMapper ruleMapper,
                                 CertNumberRuleVersionMapper versionMapper,
                                 ObjectMapper objectMapper) {
        this.ruleMapper = ruleMapper;
        this.versionMapper = versionMapper;
        this.objectMapper = objectMapper;
    }

    // ---------- 规则模块 ----------

    @Transactional
    public CertNumberRule createRule(CertRuleRequest request) {
        String project = normalizeProject(request.getProjectCode());
        List<CertNumberRule> sameProject = ruleMapper.selectList(
                new QueryWrapper<CertNumberRule>().eq("project_code", project));
        if (!sameProject.isEmpty()) {
            throw new BusinessException("DUPLICATE_RULE_PROJECT",
                    "项目 " + project + " 已存在编号规则：" + sameProject.get(0).getCode()
                            + "，一个项目只建一套规则（要调口径走新版本）");
        }
        CertNumberRule rule = new CertNumberRule();
        rule.setCode(request.getCode().trim().toUpperCase());
        rule.setName(request.getName().trim());
        rule.setProjectCode(project);
        rule.setRemark(request.getRemark());
        if (!rule.getCode().chars().allMatch(c -> (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z'))) {
            throw new BusinessException("RULE_CODE_ILLEGAL", "规则编码只允许字母与数字：" + rule.getCode());
        }
        try {
            ruleMapper.insert(rule);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("DUPLICATE_RULE_CODE", "规则编码已存在：" + rule.getCode());
        }
        return rule;
    }

    public List<CertNumberRule> listRules(String projectCode) {
        QueryWrapper<CertNumberRule> wrapper = new QueryWrapper<CertNumberRule>().orderByAsc("project_code", "code");
        if (projectCode != null && !projectCode.trim().isEmpty()) {
            wrapper.eq("project_code", projectCode.trim().toUpperCase());
        }
        return ruleMapper.selectList(wrapper);
    }

    public CertNumberRule getRule(Long ruleId) {
        CertNumberRule rule = ruleMapper.selectById(ruleId);
        if (rule == null) {
            throw new BusinessException("RULE_NOT_FOUND", "编号规则不存在：id=" + ruleId);
        }
        return rule;
    }

    // ---------- 规则版本 ----------

    /** 新建草稿版本：校验编号参数后以 JSON 冻结落库（草稿也不允许半截口径）。 */
    @Transactional
    public CertNumberRuleVersion createDraftVersion(Long ruleId, CertRuleVersionRequest request) {
        CertNumberRule rule = getRule(ruleId);
        CertNumberParams params = validateParams(request == null ? null : request.getParams());

        CertNumberRuleVersion version = new CertNumberRuleVersion();
        version.setRuleId(rule.getId());
        version.setVersionNo(nextVersionNo(rule.getId()));
        version.setStatus(CertConstants.VERSION_DRAFT);
        version.setParams(writeParams(params));
        version.setRemark(request == null ? null : request.getRemark());
        version.setOperator(OperatorHolder.current());
        versionMapper.insert(version);
        return version;
    }

    /** 发布：DRAFT → ACTIVE；同一规则旧 ACTIVE 自动置 DISABLED，同规则同时刻仅一版生效。 */
    @Transactional
    public CertNumberRuleVersion publishVersion(Long versionId) {
        CertNumberRuleVersion version = requireVersion(versionId);
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();

        if (CertConstants.VERSION_ACTIVE.equals(version.getStatus())) {
            throw new BusinessException("VERSION_ALREADY_ACTIVE",
                    "版本已是生效状态，无需重复发布：" + version.getVersionNo());
        }
        if (CertConstants.VERSION_DISABLED.equals(version.getStatus())) {
            throw new BusinessException("VERSION_DISABLED", "停用版本不可重新发布，请新建版本：" + version.getVersionNo());
        }

        List<CertNumberRuleVersion> actives = versionMapper.selectList(new QueryWrapper<CertNumberRuleVersion>()
                .eq("rule_id", version.getRuleId())
                .eq("status", CertConstants.VERSION_ACTIVE));
        for (CertNumberRuleVersion active : actives) {
            active.setStatus(CertConstants.VERSION_DISABLED);
            active.setDisabledBy(operator);
            active.setDisabledAt(now);
            versionMapper.updateById(active);
        }

        version.setStatus(CertConstants.VERSION_ACTIVE);
        version.setPublishedBy(operator);
        version.setPublishedAt(now);
        versionMapper.updateById(version);
        return version;
    }

    /** 人工停用生效版（停用后该项目在新版本发布前无法发证）。 */
    @Transactional
    public CertNumberRuleVersion disableVersion(Long versionId) {
        CertNumberRuleVersion version = requireVersion(versionId);
        if (!CertConstants.VERSION_ACTIVE.equals(version.getStatus())) {
            throw new BusinessException("VERSION_NOT_ACTIVE", "仅生效版本可停用：" + version.getVersionNo());
        }
        version.setStatus(CertConstants.VERSION_DISABLED);
        version.setDisabledBy(OperatorHolder.current());
        version.setDisabledAt(LocalDateTime.now());
        versionMapper.updateById(version);
        return version;
    }

    @Transactional(readOnly = true)
    public List<CertNumberRuleVersion> listVersions(Long ruleId) {
        getRule(ruleId);
        return versionMapper.selectList(new QueryWrapper<CertNumberRuleVersion>()
                .eq("rule_id", ruleId)
                .orderByDesc("id"));
    }

    public CertNumberRuleVersion getVersion(Long versionId) {
        return requireVersion(versionId);
    }

    /**
     * 取项目当前生效版本。项目无规则或规则无生效版均抛错，避免发出口径不明的编号。
     */
    @Transactional(readOnly = true)
    public CertNumberRuleVersion requireActiveVersion(String projectCode) {
        String project = normalizeProject(projectCode);
        CertNumberRule rule = ruleMapper.selectOne(
                new QueryWrapper<CertNumberRule>().eq("project_code", project).last("LIMIT 1"));
        if (rule == null) {
            throw new BusinessException("NO_RULE_FOR_PROJECT",
                    "项目尚未配置证书编号规则：" + project + "，请先建档并发布规则版本");
        }
        CertNumberRuleVersion active = versionMapper.selectOne(new QueryWrapper<CertNumberRuleVersion>()
                .eq("rule_id", rule.getId())
                .eq("status", CertConstants.VERSION_ACTIVE)
                .orderByDesc("id")
                .last("LIMIT 1"));
        if (active == null) {
            throw new BusinessException("NO_ACTIVE_RULE_VERSION",
                    "规则 " + rule.getCode() + " 尚无生效版本，请先发布草稿");
        }
        return active;
    }

    /** 试算：按版本给年度/批次/流水拼编号，不写任何数据。 */
    @Transactional(readOnly = true)
    public Map<String, Object> trial(Long versionId, String year, String batchCode, Integer seq) {
        CertNumberRuleVersion version = requireVersion(versionId);
        getRule(version.getRuleId());
        CertNumberParams params = readParams(version.getParams());
        String y = normalizeYear(year);
        int s = seq == null ? params.getSeqStart() : seq;
        if (s < params.getSeqStart()) {
            throw new BusinessException("SEQ_OUT_OF_RANGE",
                    "试算流水不能小于版本起点 " + params.getSeqStart() + "：" + s);
        }
        String certNo = CertNumberFormatter.fullNo(params, y, batchCode, s);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", y);
        result.put("batchCode", batchCode.trim().toUpperCase());
        result.put("seq", s);
        result.put("certNo", certNo);
        result.put("checksumValid", CertNumberFormatter.checksumValid(params, certNo));
        result.put("versionNo", version.getVersionNo());
        return result;
    }

    // ---------- 编号参数校验（发布前冻结的口径） ----------

    public CertNumberParams validateParams(CertNumberParams raw) {
        CertNumberParams p = raw == null ? new CertNumberParams() : raw;

        String prefix = p.getPrefix() == null ? "ZS" : p.getPrefix().trim().toUpperCase();
        if (prefix.isEmpty() || prefix.length() > 8
                || !prefix.chars().allMatch(c -> (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z'))) {
            throw new BusinessException("PARAM_PREFIX_INVALID", "编号前缀须为 1~8 位字母数字：" + p.getPrefix());
        }

        Integer yearDigits = p.getYearDigits() == null ? 4 : p.getYearDigits();
        if (yearDigits != 2 && yearDigits != 4) {
            throw new BusinessException("PARAM_YEAR_DIGITS_INVALID", "年度位数只允许 2 或 4：" + yearDigits);
        }

        String separator = p.getSeparator() == null ? "-" : p.getSeparator();
        if (!"-".equals(separator) && !separator.isEmpty()) {
            throw new BusinessException("PARAM_SEPARATOR_INVALID", "分隔符只允许连字符 - 或空串（连写）");
        }

        Integer seqLength = p.getSeqLength() == null ? 4 : p.getSeqLength();
        if (seqLength < 1 || seqLength > 8) {
            throw new BusinessException("PARAM_SEQ_LENGTH_INVALID", "流水位数须在 1~8 之间：" + seqLength);
        }

        Integer seqStart = p.getSeqStart() == null ? 1 : p.getSeqStart();
        if (seqStart < 0 || seqStart > 99_999_999) {
            throw new BusinessException("PARAM_SEQ_START_INVALID", "流水起点须在 0~99999999 之间：" + seqStart);
        }

        String algo = p.getChecksumAlgo() == null ? CertConstants.CHECKSUM_MOD36_2 : p.getChecksumAlgo().trim();
        if (!CertConstants.CHECKSUM_MOD36_2.equals(algo) && !CertConstants.CHECKSUM_NONE.equals(algo)) {
            throw new BusinessException("PARAM_CHECKSUM_ALGO_INVALID",
                    "校验码算法只支持 MOD36-2 或 NONE：" + algo);
        }

        CertNumberParams norm = new CertNumberParams();
        norm.setPrefix(prefix);
        norm.setYearDigits(yearDigits);
        norm.setSeparator(separator);
        norm.setSeqLength(seqLength);
        norm.setSeqStart(seqStart);
        norm.setChecksumAlgo(algo);
        return norm;
    }

    public String writeParams(CertNumberParams params) {
        try {
            return objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            throw new BusinessException("PARAMS_SERIALIZE_FAILED", "编号参数序列化失败：" + e.getMessage());
        }
    }

    public CertNumberParams readParams(String json) {
        try {
            CertNumberParams params = objectMapper.readValue(json, new TypeReference<CertNumberParams>() {
            });
            if (params == null || params.getPrefix() == null) {
                throw new BusinessException("PARAMS_BROKEN", "规则版本编号参数为空或已损坏");
            }
            return params;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("PARAMS_BROKEN", "规则版本编号参数无法解析：" + e.getMessage());
        }
    }

    public String normalizeYear(String year) {
        String y = year == null ? Integer.toString(LocalDateTime.now().getYear()) : year.trim();
        if (!y.matches("\\d{4}")) {
            throw new BusinessException("YEAR_INVALID", "年度须为 4 位数字：" + year);
        }
        return y;
    }

    // ---------- 内部 ----------

    private CertNumberRuleVersion requireVersion(Long versionId) {
        if (versionId == null) {
            throw new BusinessException("VERSION_ID_REQUIRED", "规则版本 id 不能为空");
        }
        CertNumberRuleVersion version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException("RULE_VERSION_NOT_FOUND", "编号规则版本不存在：id=" + versionId);
        }
        return version;
    }

    /** 版本号：规则 code + -R + 递增序号（如 ZS-R1）。 */
    private String nextVersionNo(Long ruleId) {
        CertNumberRule rule = getRule(ruleId);
        int max = 0;
        String prefix = rule.getCode() + "-R";
        for (CertNumberRuleVersion v : versionMapper.selectList(
                new QueryWrapper<CertNumberRuleVersion>().eq("rule_id", ruleId))) {
            String no = v.getVersionNo();
            if (no != null && no.startsWith(prefix)) {
                try {
                    max = Math.max(max, Integer.parseInt(no.substring(prefix.length())));
                } catch (NumberFormatException ignored) {
                    // 非本规则序号忽略
                }
            }
        }
        return prefix + (max + 1);
    }

    private String normalizeProject(String projectCode) {
        if (projectCode == null || projectCode.trim().isEmpty()) {
            return CertConstants.PROJECT_DEFAULT;
        }
        return projectCode.trim().toUpperCase();
    }
}
