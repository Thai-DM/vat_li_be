package com.vatly1.example.app;

import lombok.RequiredArgsConstructor;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.enums.UserRole;
import com.vatly1.example.entity.enums.UserStatus;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

import com.vatly1.example.repository.IUserRepository;
import com.vatly1.example.repository.IUserProfileRepository;
import com.vatly1.example.repository.ISubjectRepository;
import com.vatly1.example.repository.ISemesterRepository;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.IClassStaffRepository;
import com.vatly1.example.repository.IClassEnrollmentRepository;
import com.vatly1.example.repository.ExperimentRepository;
import com.vatly1.example.entity.Experiment;
import com.vatly1.example.entity.Subject;
import com.vatly1.example.entity.Semester;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.ClassStaff;
import com.vatly1.example.entity.ClassEnrollment;
import com.vatly1.example.entity.UserProfile;
import com.vatly1.example.entity.enums.ClassStatus;
import com.vatly1.example.entity.enums.ClassStaffRole;
import com.vatly1.example.entity.enums.EnrollmentStatus;
import com.vatly1.example.service.IUserService;

import java.time.LocalDate;
import java.util.UUID;

@SpringBootApplication
@ComponentScan(basePackages = "com.vatly1.example")
@EnableJpaRepositories(basePackages = "com.vatly1.example.repository")
@EntityScan(basePackages = "com.vatly1.example.entity")
@RequiredArgsConstructor
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class JwtAuthServiceApp implements CommandLineRunner {

  private final IUserService userService;
  private final IUserRepository userRepository;
  private final IUserProfileRepository userProfileRepository;
  private final ISubjectRepository subjectRepository;
  private final ISemesterRepository semesterRepository;
  private final IClassRepository classRepository;
  private final IClassStaffRepository classStaffRepository;
  private final IClassEnrollmentRepository classEnrollmentRepository;
  private final com.vatly1.example.repository.ExperimentRepository experimentRepository;
  private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

  public static void main(String[] args) {
    SpringApplication.run(JwtAuthServiceApp.class, args);
  }

  @Override
  public void run(String... params) {
    seedUsers();
    seedSubjects();
    seedSemesters();
    seedClassesAndEnrollments();
    seedExperiments();
  }

  private void seedUsers() {
    createUserIfNotExists("admin", "admin@email.com", UserRole.ADMIN, "Quản trị viên", null);
    createUserIfNotExists("gv_nguyen", "gv_nguyen@email.com", UserRole.INSTRUCTOR, "Nguyễn Văn Giảng Viên", null);
    createUserIfNotExists("gv_tran", "gv_tran@email.com", UserRole.INSTRUCTOR, "Trần Thị Giảng Viên", null);
    createUserIfNotExists("ta_hung", "ta_hung@email.com", UserRole.TA, "Phạm Hùng (Trợ giảng)", null);
    createUserIfNotExists("sv_an", "sv_an@email.com", UserRole.STUDENT, "Lê Văn An", "SV001");
    createUserIfNotExists("sv_binh", "sv_binh@email.com", UserRole.STUDENT, "Nguyễn Thị Bình", "SV002");
    createUserIfNotExists("sv_cuong", "sv_cuong@email.com", UserRole.STUDENT, "Trần Hùng Cường", "SV003");
  }

  private void createUserIfNotExists(String username, String email, UserRole role, String fullName, String studentCode) {
    if (!userRepository.existsByUsername(username)) {
      User user = new User();
      user.setUsername(username);
      user.setPasswordHash(username + "123456");
      if (username.equals("admin")) {
        user.setPasswordHash("admin123456");
      }
      user.setEmail(email);
      user.setRole(role);
      user.setStatus(UserStatus.ACTIVE);
      userService.register(user);
      
      User savedUser = userRepository.findByUsername(username);
      if (savedUser != null && !userProfileRepository.existsById(savedUser.getUserId())) {
          UserProfile profile = UserProfile.builder()
              .userId(savedUser.getUserId())
              .fullName(fullName)
              .studentCode(studentCode)
              .build();
          userProfileRepository.save(profile);
      }
    }
  }

  private void seedSubjects() {
    if (!subjectRepository.existsBySubjectCode("PHY101")) {
      subjectRepository.save(Subject.builder().subjectCode("PHY101").subjectName("Vật lý 1").isActive(true).build());
    }
    if (!subjectRepository.existsBySubjectCode("PHY102")) {
      subjectRepository.save(Subject.builder().subjectCode("PHY102").subjectName("Vật lý 2").isActive(true).build());
    }
    if (!subjectRepository.existsBySubjectCode("PHY_LAB")) {
      subjectRepository.save(Subject.builder().subjectCode("PHY_LAB").subjectName("Thí nghiệm Vật lý").isActive(false).build());
    }
  }

  private void seedSemesters() {
    if (!semesterRepository.existsBySemesterNameAndAcademicYear("Học kỳ 1", "2024-2025")) {
      semesterRepository.save(Semester.builder().semesterCode("HK1_2024_2025").semesterName("Học kỳ 1").academicYear("2024-2025").startDate(LocalDate.of(2024, 9, 1)).endDate(LocalDate.of(2025, 1, 15)).isCurrent(false).build());
    }
    if (!semesterRepository.existsBySemesterNameAndAcademicYear("Học kỳ 2", "2024-2025")) {
      semesterRepository.save(Semester.builder().semesterCode("HK2_2024_2025").semesterName("Học kỳ 2").academicYear("2024-2025").startDate(LocalDate.of(2025, 2, 1)).endDate(LocalDate.of(2025, 6, 15)).isCurrent(false).build());
    }
    if (!semesterRepository.existsBySemesterNameAndAcademicYear("Học kỳ 1", "2025-2026")) {
      semesterRepository.save(Semester.builder().semesterCode("HK1_2025_2026").semesterName("Học kỳ 1").academicYear("2025-2026").startDate(LocalDate.of(2025, 9, 1)).endDate(LocalDate.of(2026, 1, 15)).isCurrent(true).build());
    }
  }

  private void seedClassesAndEnrollments() {
    if (classRepository.count() > 0) return;

    Subject phy101 = subjectRepository.findAll().stream().filter(s -> s.getSubjectCode().equals("PHY101")).findFirst().orElse(null);
    Subject phy102 = subjectRepository.findAll().stream().filter(s -> s.getSubjectCode().equals("PHY102")).findFirst().orElse(null);
    Semester hk1_2526 = semesterRepository.findAll().stream().filter(s -> s.getSemesterCode().equals("HK1_2025_2026")).findFirst().orElse(null);

    User gvNguyen = userRepository.findByUsername("gv_nguyen");
    User gvTran = userRepository.findByUsername("gv_tran");
    User taHung = userRepository.findByUsername("ta_hung");
    User svAn = userRepository.findByUsername("sv_an");
    User svBinh = userRepository.findByUsername("sv_binh");
    User svCuong = userRepository.findByUsername("sv_cuong");

    if (phy101 != null && hk1_2526 != null && gvNguyen != null && gvTran != null) {
      // Class PHY101 - Lớp 01
      Class c1 = Class.builder()
          .subjectId(phy101.getSubjectId())
          .semesterId(hk1_2526.getSemesterId())
          .classCode("PHY101-01")
          .instructorId(gvNguyen.getUserId())
          .maxStudents(50)
          .status(ClassStatus.ACTIVE)
          .build();
      c1 = classRepository.save(c1);

      // Assign TA
      if (taHung != null) {
        classStaffRepository.save(ClassStaff.builder().classId(c1.getClassId()).userId(taHung.getUserId()).roleInClass(ClassStaffRole.TA).build());
      }

      // Enroll students
      if (svAn != null) classEnrollmentRepository.save(ClassEnrollment.builder().classId(c1.getClassId()).studentId(svAn.getUserId()).status(EnrollmentStatus.ACTIVE).build());
      if (svBinh != null) classEnrollmentRepository.save(ClassEnrollment.builder().classId(c1.getClassId()).studentId(svBinh.getUserId()).status(EnrollmentStatus.ACTIVE).build());
      if (svCuong != null) classEnrollmentRepository.save(ClassEnrollment.builder().classId(c1.getClassId()).studentId(svCuong.getUserId()).status(EnrollmentStatus.ACTIVE).build());

      // Class PHY101 - Lớp 02
      Class c2 = Class.builder()
          .subjectId(phy101.getSubjectId())
          .semesterId(hk1_2526.getSemesterId())
          .classCode("PHY101-02")
          .instructorId(gvTran.getUserId())
          .maxStudents(40)
          .status(ClassStatus.ACTIVE)
          .build();
      c2 = classRepository.save(c2);
      
      // Enroll svAn to class 2
      if (svAn != null) classEnrollmentRepository.save(ClassEnrollment.builder().classId(c2.getClassId()).studentId(svAn.getUserId()).status(EnrollmentStatus.ACTIVE).build());

      // Class PHY102 - Lớp 01
      if (phy102 != null) {
        Class c3 = Class.builder()
            .subjectId(phy102.getSubjectId())
            .semesterId(hk1_2526.getSemesterId())
            .classCode("PHY102-01")
            .instructorId(gvNguyen.getUserId())
            .maxStudents(30)
            .status(ClassStatus.DRAFT)
            .build();
        classRepository.save(c3);
      }
    }
  }

  private void seedExperiments() {
    Subject phy101 = subjectRepository.findAll().stream()
        .filter(s -> "PHY101".equals(s.getSubjectCode()))
        .findFirst().orElse(null);
    if (phy101 == null) return;

    if (experimentRepository.findBySubjectIdOrderByOrderIndexAsc(phy101.getSubjectId()).isEmpty()) {
      try {
        // 1. Rơi tự do
        String rubricJson1 = """
            {
              "type": "FREE_FALL_3D",
              "simulation_engine": "ThreeJS / WebGL",
              "assets": ["free_fall_stand.glb", "photogate_sensor.glb", "steel_ball.glb"],
              "rubric": [
                {"criteria": "Thao tác cài đặt & thu thập số liệu", "max_score": 3.0},
                {"criteria": "Xử lý số liệu, vẽ đồ thị s = f(t^2)", "max_score": 3.0},
                {"criteria": "Xác định gia tốc g và tính sai số", "max_score": 2.0},
                {"criteria": "Phân tích nguyên nhân sai số", "max_score": 2.0}
              ]
            }
            """;
        experimentRepository.save(Experiment.builder()
            .subjectId(phy101.getSubjectId())
            .title("Bài 1: Khảo sát chuyển động rơi tự do và xác định gia tốc trọng trường g")
            .description("Khảo sát chuyển động rơi tự do của vật dưới tác dụng của trọng lực, đo thời gian t qua quãng đường s và xác định gia tốc g.")
            .sceneAssetUrl("https://sim.vatly1.edu.vn/free-fall-3d")
            .sceneAssetsJson(objectMapper.readTree(rubricJson1))
            .instructions("1. Đặt quả cầu thép ở độ cao s. 2. Đo thời gian rơi t qua cổng quang. 3. Vẽ đồ thị s = f(t^2) và tính sai số gia tốc g.")
            .orderIndex(1)
            .build());

        // 2. Con lắc đơn
        String rubricJson2 = """
            {
              "type": "SIMPLE_PENDULUM_3D",
              "simulation_engine": "ThreeJS / WebGL",
              "assets": ["pendulum_rig.glb", "optical_counter.glb"],
              "rubric": [
                {"criteria": "Thiết lập chiều dài dây l và kích thích dao động", "max_score": 2.5},
                {"criteria": "Đo chu kỳ 20 dao động toàn phần", "max_score": 2.5},
                {"criteria": "Hồi quy tuyến tính T^2 theo L và tính gia tốc g", "max_score": 3.0},
                {"criteria": "Phân tích điều kiện dao động điều hòa", "max_score": 2.0}
              ]
            }
            """;
        experimentRepository.save(Experiment.builder()
            .subjectId(phy101.getSubjectId())
            .title("Bài 2: Khảo sát dao động và xác định gia tốc rơi tự do bằng con lắc đơn")
            .description("Khảo sát chu kỳ dao động T của con lắc đơn theo chiều dài dây l ở góc lệch nhỏ và xác định gia tốc rơi tự do g.")
            .sceneAssetUrl("https://sim.vatly1.edu.vn/simple-pendulum-3d")
            .sceneAssetsJson(objectMapper.readTree(rubricJson2))
            .instructions("1. Chỉnh chiều dài dây L. 2. Kéo lệch góc nhỏ rồi thả nhẹ. 3. Đo thời gian 20 chu kỳ T. 4. Vẽ đồ thị T^2 theo L, tính g.")
            .orderIndex(2)
            .build());

        // 3. Mô-men quán tính đĩa tròn
        String rubricJson3 = """
            {
              "type": "ROTATIONAL_INERTIA_3D",
              "simulation_engine": "ThreeJS / WebGL",
              "assets": ["rotational_apparatus.glb", "hanging_mass_carrier.glb"],
              "rubric": [
                {"criteria": "Căn chỉnh hệ đĩa quay và quả nặng", "max_score": 2.5},
                {"criteria": "Thu thập gia tốc góc beta với các tải trọng", "max_score": 2.5},
                {"criteria": "Xác định mô-men quán tính I", "max_score": 3.0},
                {"criteria": "Đánh giá ảnh hưởng của ma sát", "max_score": 2.0}
              ]
            }
            """;
        experimentRepository.save(Experiment.builder()
            .subjectId(phy101.getSubjectId())
            .title("Bài 3: Khảo sát chuyển động quay và xác định mô-men quán tính của đĩa tròn")
            .description("Khảo sát phương trình chuyển động quay của vật rắn quanh trục cố định, nghiệm lại định luật bảo toàn cơ năng và xác định mô-men I.")
            .sceneAssetUrl("https://sim.vatly1.edu.vn/rotational-inertia-3d")
            .sceneAssetsJson(objectMapper.readTree(rubricJson3))
            .instructions("1. Quấn dây quanh trục đĩa quay, treo quả nặng m. 2. Thả rơi có gia tốc kéo đĩa quay. 3. Đo thời gian, tính gia tốc góc beta và mô-men I.")
            .orderIndex(3)
            .build());

        // 4. Va chạm đệm khí
        String rubricJson4 = """
            {
              "type": "AIR_TRACK_COLLISION_3D",
              "simulation_engine": "ThreeJS / WebGL",
              "assets": ["air_track_bench.glb", "glider_a.glb", "glider_b.glb", "photogates.glb"],
              "rubric": [
                {"criteria": "Cân bằng băng đệm khí và triệt tiêu ma sát", "max_score": 2.5},
                {"criteria": "Đo vận tốc xe trượt trước và sau va chạm", "max_score": 2.5},
                {"criteria": "Tính toán độ bảo toàn động lượng và động năng", "max_score": 3.0},
                {"criteria": "Kết luận về định luật bảo toàn trong hệ kín", "max_score": 2.0}
              ]
            }
            """;
        experimentRepository.save(Experiment.builder()
            .subjectId(phy101.getSubjectId())
            .title("Bài 4: Khảo sát va chạm trên đệm không khí và kiểm chứng định luật bảo toàn động lượng")
            .description("Khảo sát va chạm đàn hồi và va chạm mềm giữa hai xe trượt không ma sát trên đệm khí. Kiểm chứng định luật bảo toàn động lượng và bảo toàn động năng.")
            .sceneAssetUrl("https://sim.vatly1.edu.vn/air-track-collision-3d")
            .sceneAssetsJson(objectMapper.readTree(rubricJson4))
            .instructions("1. Khởi động đệm khí. 2. Bố trí cổng quang đo thời gian xe trượt. 3. Đo vận tốc trước và sau va chạm. 4. Tính động lượng và sai số.")
            .orderIndex(4)
            .build());
      } catch (Exception e) {
        System.err.println("SEED EXPERIMENTS NOTE: " + e.getMessage());
      }
    }
  }
}