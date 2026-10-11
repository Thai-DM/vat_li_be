package com.vatly1.example.service;

import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.ClassEnrollment;
import com.vatly1.example.entity.Semester;
import com.vatly1.example.entity.Subject;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.UserProfile;
import com.vatly1.example.entity.enums.ClassStatus;
import com.vatly1.example.entity.enums.EnrollmentStatus;
import com.vatly1.example.entity.enums.UserRole;
import com.vatly1.example.entity.enums.UserStatus;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.model.request.BulkEnrollmentDTO;
import com.vatly1.example.model.request.SingleEnrollmentDTO;
import com.vatly1.example.model.request.UpdateEnrollmentStatusDTO;
import com.vatly1.example.model.response.StudentImportResultDTO;
import com.vatly1.example.repository.IClassEnrollmentRepository;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.ISemesterRepository;
import com.vatly1.example.repository.ISubjectRepository;
import com.vatly1.example.repository.IUserProfileRepository;
import com.vatly1.example.repository.IUserRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@ActiveProfiles("test")
public class ClassEnrollmentIntegrityIntegrationTest {

    @Autowired
    private IClassEnrollmentService classEnrollmentService;

    @Autowired
    private IStudentExcelService studentExcelService;

    @Autowired
    private IClassRepository classRepository;

    @Autowired
    private IClassEnrollmentRepository classEnrollmentRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private IUserProfileRepository userProfileRepository;

    @Autowired
    private ISubjectRepository subjectRepository;

    @Autowired
    private ISemesterRepository semesterRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User adminUser;
    private User teacher1;
    private User teacher2;
    private Class class1;
    private Class class2;
    private Subject subjectPhys;
    private Semester semester1;

    @BeforeEach
    void setUp() {
        adminUser = userRepository.findByUsername("admin");
        teacher1 = userRepository.findByUsername("gv_nguyen");
        teacher2 = userRepository.findByUsername("gv_tran");

        class1 = classRepository.findByClassCode("PHY101-01").orElseThrow();
        class2 = classRepository.findByClassCode("PHY101-02").orElseThrow();

        subjectPhys = subjectRepository.findById(class1.getSubjectId()).orElseThrow();
        semester1 = semesterRepository.findById(class1.getSemesterId()).orElseThrow();
    }

    private User createTestStudent(String username, String studentCode) {
        User user = User.builder()
                .username(username)
                .email(username + "@student.edu.vn")
                .passwordHash(passwordEncoder.encode("Password123@"))
                .role(UserRole.STUDENT)
                .status(UserStatus.ACTIVE)
                .build();
        user = userRepository.save(user);

        UserProfile profile = UserProfile.builder()
                .userId(user.getUserId())
                .fullName("Sinh viên " + username)
                .studentCode(studentCode)
                .build();
        userProfileRepository.save(profile);
        return user;
    }

    // =========================================================================
    // TC 01: Đăng ký hợp lệ
    // =========================================================================
    @Test
    @DisplayName("TC-01: Đăng ký hợp lệ vào lớp học phần thành công với đầy đủ subject_id và semester_id")
    void test01_validSingleEnrollment() {
        User student = createTestStudent("tc01_student", "SV_TC01");

        SingleEnrollmentDTO dto = SingleEnrollmentDTO.builder()
                .studentId(student.getUserId())
                .build();

        classEnrollmentService.enrollSingleStudent(class1.getClassId(), dto, adminUser.getUserId(), "ADMIN");

        Optional<ClassEnrollment> enrollment = classEnrollmentRepository.findByClassIdAndStudentId(class1.getClassId(), student.getUserId());
        assertTrue(enrollment.isPresent());
        assertEquals(EnrollmentStatus.ACTIVE, enrollment.get().getStatus());
        assertEquals(class1.getSubjectId(), enrollment.get().getSubjectId());
        assertEquals(class1.getSemesterId(), enrollment.get().getSemesterId());
    }

    // =========================================================================
    // TC 02: Chặn đăng ký hai lớp cùng môn/kỳ
    // =========================================================================
    @Test
    @DisplayName("TC-02: Chặn đăng ký vào 2 lớp khác nhau của cùng một môn học trong cùng học kỳ (409 Conflict)")
    void test02_blockDuplicateEnrollmentSameSubjectSemester() {
        User student = createTestStudent("tc02_student", "SV_TC02");

        // Ghi danh vào lớp 1 (PHY101-01)
        SingleEnrollmentDTO dto1 = SingleEnrollmentDTO.builder().studentId(student.getUserId()).build();
        classEnrollmentService.enrollSingleStudent(class1.getClassId(), dto1, adminUser.getUserId(), "ADMIN");

        // Cố gắng ghi danh vào lớp 2 (PHY101-02) cùng môn PHY101, cùng kỳ 2025.1
        SingleEnrollmentDTO dto2 = SingleEnrollmentDTO.builder().studentId(student.getUserId()).build();
        CustomException exception = assertThrows(CustomException.class, () ->
                classEnrollmentService.enrollSingleStudent(class2.getClassId(), dto2, adminUser.getUserId(), "ADMIN")
        );

        assertEquals(HttpStatus.CONFLICT, exception.getHttpStatus());
        assertTrue(exception.getMessage().contains("lớp khác"));

        // Xác minh lớp 2 không có enrollment của sinh viên này
        assertFalse(classEnrollmentRepository.existsByClassIdAndStudentId(class2.getClassId(), student.getUserId()));
    }

    // =========================================================================
    // TC 03: Cho phép cùng môn khác kỳ
    // =========================================================================
    @Test
    @DisplayName("TC-03: Cho phép đăng ký cùng một môn học nhưng ở hai học kỳ khác nhau")
    void test03_allowSameSubjectDifferentSemester() {
        User student = createTestStudent("tc03_student", "SV_TC03");

        // Tạo học kỳ 2 (2025.2)
        Semester semester2 = semesterRepository.save(Semester.builder()
                .semesterCode("2025.2")
                .semesterName("Học kỳ 2 2025-2026")
                .startDate(java.time.LocalDate.of(2026, 2, 1))
                .endDate(java.time.LocalDate.of(2026, 6, 30))
                .build());

        // Tạo lớp học thuộc semester 2 nhưng cùng subject PHY101
        Class classSem2 = classRepository.save(Class.builder()
                .subjectId(subjectPhys.getSubjectId())
                .semesterId(semester2.getSemesterId())
                .classCode("PHY101-03")
                .instructorId(teacher1.getUserId())
                .maxStudents(50)
                .status(ClassStatus.ACTIVE)
                .build());

        // Ghi danh vào lớp 1 (kỳ 1)
        classEnrollmentService.enrollSingleStudent(class1.getClassId(), new SingleEnrollmentDTO(student.getUserId()), adminUser.getUserId(), "ADMIN");

        // Ghi danh vào lớp kỳ 2 cùng môn -> Thành công
        assertDoesNotThrow(() ->
                classEnrollmentService.enrollSingleStudent(classSem2.getClassId(), new SingleEnrollmentDTO(student.getUserId()), adminUser.getUserId(), "ADMIN")
        );

        assertTrue(classEnrollmentRepository.existsByClassIdAndStudentId(class1.getClassId(), student.getUserId()));
        assertTrue(classEnrollmentRepository.existsByClassIdAndStudentId(classSem2.getClassId(), student.getUserId()));
    }

    // =========================================================================
    // TC 04: Cho phép khác môn cùng kỳ
    // =========================================================================
    @Test
    @DisplayName("TC-04: Cho phép đăng ký hai môn học khác nhau trong cùng một học kỳ")
    void test04_allowDifferentSubjectSameSemester() {
        User student = createTestStudent("tc04_student", "SV_TC04");

        // Tạo môn học thứ 2 (Hóa học đại cương CHM101)
        Subject subjectChem = subjectRepository.save(Subject.builder()
                .subjectCode("CHM101")
                .subjectName("Hóa học đại cương 1")
                .isActive(true)
                .build());

        // Tạo lớp học môn CHM101 trong cùng học kỳ 1
        Class classChem = classRepository.save(Class.builder()
                .subjectId(subjectChem.getSubjectId())
                .semesterId(semester1.getSemesterId())
                .classCode("CHM101-01")
                .instructorId(teacher2.getUserId())
                .maxStudents(50)
                .status(ClassStatus.ACTIVE)
                .build());

        // Ghi danh môn Lý kỳ 1
        classEnrollmentService.enrollSingleStudent(class1.getClassId(), new SingleEnrollmentDTO(student.getUserId()), adminUser.getUserId(), "ADMIN");

        // Ghi danh môn Hóa kỳ 1 -> Thành công
        assertDoesNotThrow(() ->
                classEnrollmentService.enrollSingleStudent(classChem.getClassId(), new SingleEnrollmentDTO(student.getUserId()), adminUser.getUserId(), "ADMIN")
        );

        assertTrue(classEnrollmentRepository.existsByClassIdAndStudentId(class1.getClassId(), student.getUserId()));
        assertTrue(classEnrollmentRepository.existsByClassIdAndStudentId(classChem.getClassId(), student.getUserId()));
    }

    // =========================================================================
    // TC 05: DROPPED → ACTIVE có xung đột
    // =========================================================================
    @Test
    @DisplayName("TC-05: Chuyển trạng thái DROPPED sang ACTIVE bị từ chối (409 Conflict) nếu đang ACTIVE ở lớp khác cùng môn/kỳ")
    void test05_conflictWhenReactivatingDroppedToActive() {
        User student = createTestStudent("tc05_student", "SV_TC05");

        // 1. Ghi danh lớp 1 rồi chuyển sang DROPPED
        classEnrollmentService.enrollSingleStudent(class1.getClassId(), new SingleEnrollmentDTO(student.getUserId()), adminUser.getUserId(), "ADMIN");
        classEnrollmentService.updateEnrollmentStatus(class1.getClassId(), student.getUserId(),
                new UpdateEnrollmentStatusDTO(EnrollmentStatus.DROPPED), adminUser.getUserId(), "ADMIN");

        // 2. Ghi danh vào lớp 2 (PHY101-02) -> Thành công vì lớp 1 đã DROPPED
        classEnrollmentService.enrollSingleStudent(class2.getClassId(), new SingleEnrollmentDTO(student.getUserId()), adminUser.getUserId(), "ADMIN");

        // 3. Cố gắng kích hoạt lại lớp 1 từ DROPPED -> ACTIVE -> Phải bị từ chối 409 Conflict
        CustomException ex = assertThrows(CustomException.class, () ->
                classEnrollmentService.updateEnrollmentStatus(class1.getClassId(), student.getUserId(),
                        new UpdateEnrollmentStatusDTO(EnrollmentStatus.ACTIVE), adminUser.getUserId(), "ADMIN")
        );
        assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());

        // Trạng thái lớp 1 vẫn giữ DROPPED
        ClassEnrollment enr1 = classEnrollmentRepository.findByClassIdAndStudentId(class1.getClassId(), student.getUserId()).orElseThrow();
        assertEquals(EnrollmentStatus.DROPPED, enr1.getStatus());
    }

    // =========================================================================
    // TC 06: Đăng ký lại vào chính lớp đã DROPPED
    // =========================================================================
    @Test
    @DisplayName("TC-06: Đăng ký lại vào chính lớp đã DROPPED thành công (tái kích hoạt mà không vi phạm UNIQUE class_id, student_id)")
    void test06_reEnrollIntoSameDroppedClass() {
        User student = createTestStudent("tc06_student", "SV_TC06");

        // Ghi danh rồi DROP
        classEnrollmentService.enrollSingleStudent(class1.getClassId(), new SingleEnrollmentDTO(student.getUserId()), adminUser.getUserId(), "ADMIN");
        classEnrollmentService.updateEnrollmentStatus(class1.getClassId(), student.getUserId(),
                new UpdateEnrollmentStatusDTO(EnrollmentStatus.DROPPED), adminUser.getUserId(), "ADMIN");

        // Đăng ký lại vào chính lớp 1
        assertDoesNotThrow(() ->
                classEnrollmentService.enrollSingleStudent(class1.getClassId(), new SingleEnrollmentDTO(student.getUserId()), adminUser.getUserId(), "ADMIN")
        );

        ClassEnrollment reactivated = classEnrollmentRepository.findByClassIdAndStudentId(class1.getClassId(), student.getUserId()).orElseThrow();
        assertEquals(EnrollmentStatus.ACTIVE, reactivated.getStatus());
    }

    // =========================================================================
    // TC 07: Bulk import có enrollment bị trùng (All-or-nothing rollback)
    // =========================================================================
    @Test
    @DisplayName("TC-07: Ghi danh hàng loạt có một sinh viên bị trùng môn/kỳ -> Bị từ chối 409 Conflict và rollback toàn bộ batch")
    void test07_bulkEnrollmentWithConflictRollsBackAll() {
        User studentA = createTestStudent("tc07_student_a", "SV_TC07_A");
        User studentB = createTestStudent("tc07_student_b", "SV_TC07_B");

        // studentA đã ACTIVE ở lớp 1 (PHY101-01)
        classEnrollmentService.enrollSingleStudent(class1.getClassId(), new SingleEnrollmentDTO(studentA.getUserId()), adminUser.getUserId(), "ADMIN");

        // Thực hiện bulk enroll cả studentA và studentB vào lớp 2 (PHY101-02)
        BulkEnrollmentDTO bulkDTO = BulkEnrollmentDTO.builder()
                .studentIds(List.of(studentA.getUserId(), studentB.getUserId()))
                .build();

        CustomException ex = assertThrows(CustomException.class, () ->
                classEnrollmentService.enrollBulkStudents(class2.getClassId(), bulkDTO, adminUser.getUserId(), "ADMIN")
        );
        assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());

        // Kiểm tra All-or-Nothing: Không có sinh viên nào được thêm vào lớp 2
        assertFalse(classEnrollmentRepository.existsByClassIdAndStudentId(class2.getClassId(), studentA.getUserId()));
        assertFalse(classEnrollmentRepository.existsByClassIdAndStudentId(class2.getClassId(), studentB.getUserId()));
    }

    // =========================================================================
    // TC 08: Excel import với nhiều dòng hợp lệ và không hợp lệ (Partial success)
    // =========================================================================
    @Test
    @DisplayName("TC-08: Nhập Excel với nhiều dòng -> Dòng hợp lệ thành công, dòng xung đột được báo lỗi chi tiết và cô lập độc lập")
    void test08_excelImportPartialSuccessWithDetailedErrors() throws Exception {
        // studentConflict đã ACTIVE ở lớp 1
        User studentConflict = createTestStudent("tc08_conflict", "SV_TC08_CONF");
        classEnrollmentService.enrollSingleStudent(class1.getClassId(), new SingleEnrollmentDTO(studentConflict.getUserId()), adminUser.getUserId(), "ADMIN");

        // Tạo Excel workbook với 2 dòng:
        // Dòng 1: Sinh viên mới hợp lệ (đăng ký vào PHY101-02)
        // Dòng 2: Sinh viên trùng môn (SV_TC08_CONF đăng ký vào PHY101-02)
        byte[] excelBytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Students");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("STT");
            header.createCell(1).setCellValue("Mã sinh viên");
            header.createCell(2).setCellValue("Họ và tên");
            header.createCell(3).setCellValue("Email");
            header.createCell(4).setCellValue("Tên đăng nhập");
            header.createCell(5).setCellValue("Mật khẩu");
            header.createCell(6).setCellValue("Ngày sinh");
            header.createCell(7).setCellValue("Giới tính");
            header.createCell(8).setCellValue("Số điện thoại");
            header.createCell(9).setCellValue("Mã lớp");

            // Row 1: Valid
            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue(1);
            r1.createCell(1).setCellValue("SV_TC08_VALID");
            r1.createCell(2).setCellValue("Sinh viên Hợp lệ");
            r1.createCell(3).setCellValue("valid08@student.edu.vn");
            r1.createCell(4).setCellValue("sv_tc08_valid");
            r1.createCell(5).setCellValue("");
            r1.createCell(6).setCellValue("10/10/2004");
            r1.createCell(7).setCellValue("Nam");
            r1.createCell(8).setCellValue("0912345678");
            r1.createCell(9).setCellValue("PHY101-02");

            // Row 2: Conflict (SV_TC08_CONF đã ở PHY101-01, nay cố ghi danh PHY101-02)
            Row r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue(2);
            r2.createCell(1).setCellValue("SV_TC08_CONF");
            r2.createCell(2).setCellValue("Sinh viên Xung đột");
            r2.createCell(3).setCellValue("tc08_conflict@student.edu.vn");
            r2.createCell(4).setCellValue("tc08_conflict");
            r2.createCell(5).setCellValue("");
            r2.createCell(6).setCellValue("15/05/2004");
            r2.createCell(7).setCellValue("Nam");
            r2.createCell(8).setCellValue("0987654321");
            r2.createCell(9).setCellValue("PHY101-02");

            workbook.write(baos);
            excelBytes = baos.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file", "import_test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes
        );

        StudentImportResultDTO result = studentExcelService.importStudentsFromExcel(file, null, null, adminUser.getUserId(), "ADMIN");

        assertEquals(2, result.getTotalRows());
        assertEquals(1, result.getTotalCreated()); // Dòng 1 tạo thành công
        assertEquals(1, result.getTotalEnrolled()); // Dòng 1 ghi danh thành công
        assertEquals(1, result.getErrors().size()); // Dòng 2 ghi nhận lỗi
        assertTrue(result.getErrors().get(0).contains("Xung đột") || result.getErrors().get(0).contains("lớp khác"));

        // Xác minh sinh viên hợp lệ đã có mặt trong lớp 2
        User validUser = userRepository.findByUsername("sv_tc08_valid");
        assertNotNull(validUser);
        assertTrue(classEnrollmentRepository.existsByClassIdAndStudentId(class2.getClassId(), validUser.getUserId()));

        // Xác minh sinh viên xung đột KHÔNG bị ghi danh vào lớp 2
        assertFalse(classEnrollmentRepository.existsByClassIdAndStudentId(class2.getClassId(), studentConflict.getUserId()));
    }

    // =========================================================================
    // TC 09: Giáo viên import vào lớp không phụ trách bị từ chối
    // =========================================================================
    @Test
    @DisplayName("TC-09: Giáo viên import vào lớp không phụ trách bị từ chối (403 Forbidden)")
    void test09_instructorUnauthorizedImportForbidden() {
        MockMultipartFile dummyFile = new MockMultipartFile(
                "file", "dummy.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{1, 2, 3}
        );

        // Teacher 2 (gv_tran) cố gắng import vào class 1 (do gv_nguyen phụ trách)
        CustomException ex = assertThrows(CustomException.class, () ->
                studentExcelService.importStudentsFromExcel(dummyFile, null, class1.getClassId(), teacher2.getUserId(), "INSTRUCTOR")
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("không có quyền"));
    }

    // =========================================================================
    // TC 10: Hai request đồng thời đăng ký cùng sinh viên vào hai lớp khác nhau
    // =========================================================================
    @Test
    @DisplayName("TC-10: Concurrency - 2 request đồng thời đăng ký cùng 1 sinh viên vào 2 lớp cùng môn/kỳ -> Đúng 1 thành công, 1 thất bại")
    void test10_concurrentEnrollmentSameSubjectSemester() throws Exception {
        User student = createTestStudent("tc10_concurrent", "SV_TC10");

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        Callable<Void> task1 = () -> {
            readyLatch.countDown();
            startLatch.await();
            try {
                classEnrollmentService.enrollSingleStudent(class1.getClassId(), new SingleEnrollmentDTO(student.getUserId()), adminUser.getUserId(), "ADMIN");
                successCount.incrementAndGet();
            } catch (CustomException ce) {
                if (ce.getHttpStatus() == HttpStatus.CONFLICT) {
                    conflictCount.incrementAndGet();
                }
            } catch (Exception e) {
                if (e instanceof DataIntegrityViolationException || e.getCause() instanceof DataIntegrityViolationException) {
                    conflictCount.incrementAndGet();
                }
            }
            return null;
        };

        Callable<Void> task2 = () -> {
            readyLatch.countDown();
            startLatch.await();
            try {
                classEnrollmentService.enrollSingleStudent(class2.getClassId(), new SingleEnrollmentDTO(student.getUserId()), adminUser.getUserId(), "ADMIN");
                successCount.incrementAndGet();
            } catch (CustomException ce) {
                if (ce.getHttpStatus() == HttpStatus.CONFLICT) {
                    conflictCount.incrementAndGet();
                }
            } catch (Exception e) {
                if (e instanceof DataIntegrityViolationException || e.getCause() instanceof DataIntegrityViolationException) {
                    conflictCount.incrementAndGet();
                }
            }
            return null;
        };

        Future<Void> f1 = executor.submit(task1);
        Future<Void> f2 = executor.submit(task2);

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Phát lệnh chạy đồng thời

        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Kiểm tra kết quả: Đúng 1 thread thành công, 1 thread bị chặn
        assertEquals(1, successCount.get(), "Chính xác một tiến trình phải thành công");
        assertEquals(1, conflictCount.get(), "Tiến trình còn lại phải bị từ chối do xung đột");

        // Kiểm tra trong database: Chỉ duy nhất 1 bản ghi ACTIVE cho môn/kỳ này
        List<ClassEnrollment> activeEnrs = classEnrollmentRepository.findByStudentIdAndSubjectIdAndSemesterId(
                student.getUserId(), subjectPhys.getSubjectId(), semester1.getSemesterId());
        long activeCount = activeEnrs.stream().filter(e -> e.getStatus() == EnrollmentStatus.ACTIVE).count();
        assertEquals(1, activeCount, "Chỉ duy nhất một bản ghi ACTIVE được phép tồn tại");
    }

    // =========================================================================
    // TC 11: Database từ chối bản ghi có subject_id/semester_id không khớp class_id
    // =========================================================================
    @Test
    @DisplayName("TC-11: Composite FK bảo vệ tính nhất quán - Từ chối bản ghi enrollment có subject_id/semester_id sai lệch so với class_id")
    void test11_compositeForeignKeyIntegrityCheck() {
        User student = createTestStudent("tc11_student", "SV_TC11");

        // Tạo môn học khác
        Subject otherSubject = subjectRepository.save(Subject.builder()
                .subjectCode("MTH101")
                .subjectName("Giải tích 1")
                .isActive(true)
                .build());

        // Cố tình chèn vào class_enrollments với class_id = class1, nhưng subject_id = otherSubject (lệch với class1)
        assertThrows(Exception.class, () -> {
            jdbcTemplate.update(
                    "INSERT INTO class_enrollments (enrollment_id, class_id, student_id, subject_id, semester_id, status, enrolled_at) " +
                            "VALUES (?, ?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP)",
                    UUID.randomUUID(), class1.getClassId(), student.getUserId(), otherSubject.getSubjectId(), class1.getSemesterId()
            );
        });
    }

    // =========================================================================
    // TC 12: Ứng dụng khởi động với seed data hợp lệ
    // =========================================================================
    @Test
    @DisplayName("TC-12: Seed data hợp lệ - Khởi động ứng dụng không có xung đột và dữ liệu mẫu tuân thủ ràng buộc")
    void test12_seedDataIntegrityVerification() {
        List<ClassEnrollment> allEnrollments = classEnrollmentRepository.findAll();
        assertFalse(allEnrollments.isEmpty(), "Seed enrollments phải có dữ liệu");

        // Xác minh mỗi enrollment đều có subjectId và semesterId trùng khớp với lớp của nó
        for (ClassEnrollment enr : allEnrollments) {
            Class clazz = classRepository.findById(enr.getClassId()).orElseThrow();
            assertEquals(clazz.getSubjectId(), enr.getSubjectId(), "subject_id phải khớp với classes");
            assertEquals(clazz.getSemesterId(), enr.getSemesterId(), "semester_id phải khớp với classes");
        }

        // Xác minh không có sinh viên nào có nhiều hơn 1 enrollment ACTIVE cho cùng môn/kỳ
        Map<String, Integer> activeStudentSubjectSemCount = new HashMap<>();
        for (ClassEnrollment enr : allEnrollments) {
            if (enr.getStatus() == EnrollmentStatus.ACTIVE) {
                String key = enr.getStudentId() + "_" + enr.getSubjectId() + "_" + enr.getSemesterId();
                activeStudentSubjectSemCount.put(key, activeStudentSubjectSemCount.getOrDefault(key, 0) + 1);
            }
        }

        for (Map.Entry<String, Integer> entry : activeStudentSubjectSemCount.entrySet()) {
            assertEquals(1, entry.getValue(), "Không được phép có quá 1 bản ghi ACTIVE cho cùng student/subject/semester: " + entry.getKey());
        }
    }
}
