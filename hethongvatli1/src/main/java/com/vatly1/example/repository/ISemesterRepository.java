package com.vatly1.example.repository;

import com.vatly1.example.entity.Semester;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ISemesterRepository extends JpaRepository<Semester, UUID> {
    boolean existsBySemesterNameAndAcademicYear(String semesterName, String academicYear);

    @Modifying
    @Query("UPDATE Semester s SET s.isCurrent = false WHERE s.isCurrent = true")
    void clearCurrentSemester();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Semester s SET s.isCurrent = CASE WHEN s.semesterId = :id THEN true ELSE false END")
    int setSingleCurrentSemesterAtomic(@Param("id") UUID id);
}