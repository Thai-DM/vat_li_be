package com.vatly1.example.repository;

import com.vatly1.example.entity.ClassSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface IClassScheduleRepository extends JpaRepository<ClassSchedule, UUID> {

    List<ClassSchedule> findByClassIdOrderByDayOfWeekAscStartTimeAsc(UUID classId);

    List<ClassSchedule> findByClassIdInOrderByDayOfWeekAscStartTimeAsc(Collection<UUID> classIds);

    void deleteByClassId(UUID classId);
}
