package com.vatly1.example.service;

import com.vatly1.example.model.response.DashboardSnapshotDTO;

import java.util.UUID;

public interface IDashboardService {
    DashboardSnapshotDTO getClassDashboard(UUID classId, UUID requesterId, String role);
    DashboardSnapshotDTO getStudentDashboard(UUID classId, UUID studentId, UUID requesterId, String role);
    DashboardSnapshotDTO getMyDashboard(UUID studentId);
    DashboardSnapshotDTO regenerateClassSnapshot(UUID classId);
}