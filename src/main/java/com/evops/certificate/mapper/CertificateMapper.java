package com.evops.certificate.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evops.certificate.entity.Certificate;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CertificateMapper extends BaseMapper<Certificate> {
}
