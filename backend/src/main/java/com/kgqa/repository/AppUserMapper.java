package com.kgqa.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kgqa.model.entity.AppUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AppUserMapper extends BaseMapper<AppUser> {
}
