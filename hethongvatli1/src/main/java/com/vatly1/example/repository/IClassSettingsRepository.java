package com.vatly1.example.repository;

import com.vatly1.example.entity.ClassSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface IClassSettingsRepository extends JpaRepository<ClassSettings, UUID> {
}
