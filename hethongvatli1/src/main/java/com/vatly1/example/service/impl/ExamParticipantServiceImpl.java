package com.vatly1.example.service.impl;

import com.vatly1.example.converter.ClassEnrollmentConverter;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.ClassEnrollment;
import com.vatly1.example.entity.Exam;
import com.vatly1.example.entity.ExamParticipant;
import com.vatly1.example.entity.Subject;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.UserProfile;
import com.vatly1.example.entity.enums.EnrollmentStatus;
import com.vatly1.example.entity.enums.NotificationType;
import com.vatly1.example.entity.enums.UserRole;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.model.dto.EnrollmentDTO;
import com.vatly1.example.model.dto.ExamDTO;
import com.vatly1.example.model.dto.ExamParticipantDTO;
import com.vatly1.example.model.dto.ExamRosterDTO;
import com.vatly1.example.model.request.TransferStudentDTO;
import com.vatly1.example.repository.IClassEnrollmentRepository;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.IClassStaffRepository;
import com.vatly1.example.repository.IExamParticipantRepository;
import com.vatly1.example.repository.IExamRepository;
import com.vatly1.example.repository.ISubjectRepository;
import com.vatly1.example.repository.IUserProfileRepository;
import com.vatly1.example.repository.IUserRepository;
import com.vatly1.example.service.IExamParticipantService;
import com.vatly1.example.service.INotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExamParticipantServiceImpl implements IExamParticipantService {

    private final IExamRepository examRepository;
    private final IClassRepository classRepository;
    private final IClassEnrollmentRepository classEnrollmentRepository;
    private final IClassStaffRepository classStaffRepository;
    private final IExamParticipantRepository examParticipantRepository;
    private final IUserRepository userRepository;
    private final IUserProfileRepository userProfileRepository;
    private final ISubjectRepository subjectRepository;
    private final ClassEnrollmentConverter classEnrollmentConverter;
    private final INotificationService notificationService;

    @Override
    @Transactional
    public ExamParticipantDTO transferStudentToExam(UUID examId, TransferStudentDTO dto, UUID approverId, String approverRole) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new CustomException("Không tìm thấy ca thi", HttpStatus.NOT_FOUND));

        Class targetClass = classRepository.findById(exam.getClassId())
                .orElseThrow(() -> new CustomException("Không tìm thấy lớp học phần của ca thi này", HttpStatus.NOT_FOUND));

        validateInstructorOrAdmin(targetClass, approverId, approverRole, "Bạn không có quyền quản lý thí sinh của ca thi này");

        User student = userRepository.findById(dto.getStudentId())
                .orElseThrow(() -> new CustomException("Không tìm thấy sinh viên", HttpStatus.NOT_FOUND));

        if (student.getRole() != UserRole.STUDENT && student.getRole() != UserRole.ADMIN) {
            throw new CustomException("Người dùng được chỉ định không phải là sinh viên", HttpStatus.BAD_REQUEST);
        }

        // Kiểm tra xem sinh viên đã là thành viên chính thức của lớp này chưa
        if (classEnrollmentRepository.existsByClassIdAndStudentId(targetClass.getClassId(), dto.getStudentId())) {
            throw new CustomException("Sinh viên đã là thành viên chính thức của lớp học này, không cần thi ghép", HttpStatus.BAD_REQUEST);
        }

        // Kiểm tra xem sinh viên đã có trong danh sách thi ghép của ca thi này chưa
        if (examParticipantRepository.existsByExamIdAndStudentId(examId, dto.getStudentId())) {
            throw new CustomException("Sinh viên đã được đăng ký thi ghép trong ca thi này từ trước", HttpStatus.CONFLICT);
        }

        UUID targetSubjectId = targetClass.getSubjectId();
        UUID originalClassId = dto.getOriginalClassId();
        Class originalClass = null;

        if (originalClassId != null) {
            originalClass = classRepository.findById(originalClassId)
                    .orElseThrow(() -> new CustomException("Không tìm thấy lớp học gốc được chỉ định", HttpStatus.NOT_FOUND));

            if (!Objects.equals(originalClass.getSubjectId(), targetSubjectId)) {
                throw new CustomException("Lớp học gốc không thuộc cùng môn học với ca thi này. Sinh viên chỉ có thể thi ghép các lớp trong cùng môn học!", HttpStatus.BAD_REQUEST);
            }

            if (!classEnrollmentRepository.existsByClassIdAndStudentId(originalClassId, dto.getStudentId())) {
                throw new CustomException("Sinh viên không tham gia lớp học gốc đã chỉ định", HttpStatus.BAD_REQUEST);
            }
        } else {
            // Tự động tìm lớp học gốc cùng môn học của sinh viên
            List<ClassEnrollment> enrollments = classEnrollmentRepository.findByStudentIdAndStatus(dto.getStudentId(), EnrollmentStatus.ACTIVE);
            for (ClassEnrollment en : enrollments) {
                Class c = classRepository.findById(en.getClassId()).orElse(null);
                if (c != null && Objects.equals(c.getSubjectId(), targetSubjectId)) {
                    originalClass = c;
                    originalClassId = c.getClassId();
                    break;
                }
            }

            if (originalClass == null) {
                throw new CustomException("Sinh viên không tham gia lớp học phần nào thuộc môn học này, không thể chuyển ca thi!", HttpStatus.BAD_REQUEST);
            }
        }

        String reason = dto.getReason() != null && !dto.getReason().isBlank() ? dto.getReason() : "Chuyển ca thi hợp lệ";

        ExamParticipant participant = ExamParticipant.builder()
                .examId(examId)
                .studentId(dto.getStudentId())
                .originalClassId(originalClassId)
                .reason(reason)
                .approvedBy(approverId)
                .build();

        ExamParticipant saved = examParticipantRepository.save(participant);

        // Tự động thông báo cho sinh viên
        try {
            notificationService.sendNotification(
                    dto.getStudentId(),
                    "Thông báo chuyển ca thi: " + exam.getTitle(),
                    String.format("Bạn đã được chuyển sang thi tại ca thi '%s' (Lớp %s). Lý do: %s. Vui lòng kiểm tra thời gian làm bài đúng hạn.",
                            exam.getTitle(), targetClass.getClassCode(), reason),
                    NotificationType.EXAM_NEW,
                    examId,
                    "EXAM"
            );
        } catch (Exception e) {
            log.warn("Failed to send transfer notification to student {}: {}", dto.getStudentId(), e.getMessage());
        }

        return toParticipantDTO(saved, exam, targetClass, originalClass, student);
    }

    @Override
    @Transactional
    public void removeTransferredStudent(UUID examId, UUID studentId, UUID approverId, String approverRole) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new CustomException("Không tìm thấy ca thi", HttpStatus.NOT_FOUND));

        Class targetClass = classRepository.findById(exam.getClassId())
                .orElseThrow(() -> new CustomException("Không tìm thấy lớp học phần của ca thi này", HttpStatus.NOT_FOUND));

        validateInstructorOrAdmin(targetClass, approverId, approverRole, "Bạn không có quyền quản lý thí sinh của ca thi này");

        ExamParticipant participant = examParticipantRepository.findByExamIdAndStudentId(examId, studentId)
                .orElseThrow(() -> new CustomException("Sinh viên không có trong danh sách thi ghép của ca thi này", HttpStatus.NOT_FOUND));

        examParticipantRepository.delete(participant);
    }

    @Override
    public ExamRosterDTO getExamRoster(UUID examId, UUID currentUserId, String currentUserRole) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new CustomException("Không tìm thấy ca thi", HttpStatus.NOT_FOUND));

        Class clazz = classRepository.findById(exam.getClassId())
                .orElseThrow(() -> new CustomException("Không tìm thấy lớp học", HttpStatus.NOT_FOUND));

        validateInstructorOrAdminOrTA(clazz, currentUserId, currentUserRole);

        // 1. Thí sinh chính thức từ lớp
        List<ClassEnrollment> enrollments = classEnrollmentRepository.findByClassIdAndStatus(clazz.getClassId(), EnrollmentStatus.ACTIVE);
        List<EnrollmentDTO> officialStudents = enrollments.stream().map(en -> {
            User u = userRepository.findById(en.getStudentId()).orElse(null);
            UserProfile p = u != null ? userProfileRepository.findById(u.getUserId()).orElse(null) : null;
            return classEnrollmentConverter.toEnrollmentDTO(en, u, p);
        }).collect(Collectors.toList());

        // 2. Thí sinh thi ghép
        List<ExamParticipant> transferred = examParticipantRepository.findByExamId(examId);
        List<ExamParticipantDTO> transferredStudents = transferred.stream().map(tp -> {
            User st = userRepository.findById(tp.getStudentId()).orElse(null);
            Class orig = tp.getOriginalClassId() != null ? classRepository.findById(tp.getOriginalClassId()).orElse(null) : null;
            return toParticipantDTO(tp, exam, clazz, orig, st);
        }).collect(Collectors.toList());

        return ExamRosterDTO.builder()
                .examId(examId)
                .examTitle(exam.getTitle())
                .classId(clazz.getClassId())
                .classCode(clazz.getClassCode())
                .totalParticipants(officialStudents.size() + transferredStudents.size())
                .officialStudentCount(officialStudents.size())
                .transferredStudentCount(transferredStudents.size())
                .officialStudents(officialStudents)
                .transferredStudents(transferredStudents)
                .build();
    }

    @Override
    public List<ExamDTO> getTransferredExamsForStudent(UUID studentId) {
        List<ExamParticipant> participants = examParticipantRepository.findByStudentId(studentId);
        if (participants.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> examIds = participants.stream()
                .map(ExamParticipant::getExamId)
                .distinct()
                .collect(Collectors.toList());

        List<Exam> exams = examRepository.findAllById(examIds);
        return exams.stream().map(this::toSimpleExamDTO).collect(Collectors.toList());
    }

    @Override
    public boolean isStudentEligibleForExam(UUID examId, UUID studentId) {
        Exam exam = examRepository.findById(examId).orElse(null);
        if (exam == null) {
            return false;
        }

        boolean isEnrolled = classEnrollmentRepository.existsByClassIdAndStudentId(exam.getClassId(), studentId);
        boolean isTransferred = examParticipantRepository.existsByExamIdAndStudentId(examId, studentId);
        return isEnrolled || isTransferred;
    }

    private ExamParticipantDTO toParticipantDTO(ExamParticipant p, Exam exam, Class targetClass, Class originalClass, User student) {
        UserProfile studentProfile = student != null ? userProfileRepository.findById(student.getUserId()).orElse(null) : null;
        User approver = p.getApprovedBy() != null ? userRepository.findById(p.getApprovedBy()).orElse(null) : null;
        UserProfile approverProfile = approver != null ? userProfileRepository.findById(approver.getUserId()).orElse(null) : null;

        String studentName = studentProfile != null && studentProfile.getFullName() != null ? studentProfile.getFullName() : (student != null ? student.getUsername() : null);
        String studentCode = studentProfile != null ? studentProfile.getStudentCode() : null;
        String approverName = approverProfile != null && approverProfile.getFullName() != null ? approverProfile.getFullName() : (approver != null ? approver.getUsername() : null);

        Subject subject = targetClass != null && targetClass.getSubjectId() != null ? subjectRepository.findById(targetClass.getSubjectId()).orElse(null) : null;

        return ExamParticipantDTO.builder()
                .id(p.getId())
                .examId(p.getExamId())
                .examTitle(exam != null ? exam.getTitle() : null)
                .studentId(p.getStudentId())
                .studentUsername(student != null ? student.getUsername() : null)
                .studentName(studentName)
                .studentCode(studentCode)
                .originalClassId(p.getOriginalClassId())
                .originalClassCode(originalClass != null ? originalClass.getClassCode() : null)
                .targetClassId(targetClass != null ? targetClass.getClassId() : null)
                .targetClassCode(targetClass != null ? targetClass.getClassCode() : null)
                .subjectName(subject != null ? subject.getSubjectName() : null)
                .reason(p.getReason())
                .approvedBy(p.getApprovedBy())
                .approverName(approverName)
                .createdAt(p.getCreatedAt())
                .build();
    }

    private ExamDTO toSimpleExamDTO(Exam exam) {
        return ExamDTO.builder()
                .examId(exam.getExamId())
                .classId(exam.getClassId())
                .matrixId(exam.getMatrixId())
                .title(exam.getTitle())
                .examType(exam.getExamType())
                .durationMinutes(exam.getDurationMinutes())
                .startTime(exam.getStartTime())
                .endTime(exam.getEndTime())
                .createdBy(exam.getCreatedBy())
                .createdAt(exam.getCreatedAt())
                .build();
    }

    private void validateInstructorOrAdmin(Class clazz, UUID currentUserId, String currentUserRole, String errorMessage) {
        UserRole role = UserRole.valueOf(currentUserRole.toUpperCase());
        if (role == UserRole.ADMIN) {
            return;
        }
        boolean isOwner = clazz.getInstructorId() != null && clazz.getInstructorId().equals(currentUserId);
        boolean isStaff = classStaffRepository.existsByClassIdAndUserId(clazz.getClassId(), currentUserId);
        if (!isOwner && !isStaff) {
            throw new CustomException(errorMessage, HttpStatus.FORBIDDEN);
        }
    }

    private void validateInstructorOrAdminOrTA(Class clazz, UUID currentUserId, String currentUserRole) {
        UserRole role = UserRole.valueOf(currentUserRole.toUpperCase());
        if (role == UserRole.ADMIN) {
            return;
        }
        boolean isOwner = clazz.getInstructorId() != null && clazz.getInstructorId().equals(currentUserId);
        boolean isStaff = classStaffRepository.existsByClassIdAndUserId(clazz.getClassId(), currentUserId);
        if (!isOwner && !isStaff) {
            throw new CustomException("Bạn không có quyền xem danh sách thí sinh của ca thi này", HttpStatus.FORBIDDEN);
        }
    }
}
