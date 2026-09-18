package com.vatly1.example.repository;

import com.vatly1.example.entity.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ISystemSettingRepository extends JpaRepository<SystemSetting, String> {
    Optional<SystemSetting> findBySettingKey(String settingKey);
}
