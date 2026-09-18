package com.vatly1.example.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;

import java.util.List;

/**
 * Cấu hình OpenAPI 3.0 / Swagger UI cho Hệ thống Quản lý Học tập & Thí nghiệm Ảo Vật lý 1.
 */
@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI api() {
    return new OpenAPI()
        .info(new Info()
            .title("Hệ thống Quản lý Học tập & Thí nghiệm Ảo Vật lý 1 - REST API")
            .description("Tài liệu kỹ thuật RESTful API cho Hệ thống Vật lý 1 (Physics 1 LMS & Virtual Lab).\n\n"
                + "### 1. Phân quyền & Vai trò (RBAC):\n"
                + "- `ROLE_ADMIN`: Quản trị viên hệ thống (quản trị tài khoản, cấu hình tham số, audit log, actuator).\n"
                + "- `ROLE_TEACHER`: Giảng viên (quản lý lớp, ngân hàng câu hỏi, tạo đề thi, chấm rubric thí nghiệm, xem analytics).\n"
                + "- `ROLE_STUDENT`: Sinh viên (luyện tập, thi trắc nghiệm, tương tác thí nghiệm ảo 3D, nộp minh chứng, trợ giảng Socratic AI).\n\n"
                + "### 2. Hướng dẫn Xác thực (JWT Bearer Token):\n"
                + "1. Gửi request đăng nhập tại `/api/v1/users/signin` để nhận `accessToken` và `refreshToken`.\n"
                + "2. Bấm nút **Authorize** ở góc trên bên phải Swagger UI.\n"
                + "3. Nhập token theo định dạng: `Bearer <accessToken>` hoặc `<accessToken>`.\n"
                + "4. Mọi request sau đó sẽ tự động đính kèm Authorization Header.")
            .version("1.0.0")
            .contact(new Contact()
                .name("Bộ môn Vật lý - Khoa Cơ bản 1")
                .email("vatly1@edu.vn"))
            .license(new License()
                .name("Bản quyền Đề án Nghiên cứu & Phát triển Giáo dục Đại học")
                .url("https://edu.vn")))
        .servers(List.of(
            new Server().url("http://localhost:8080").description("Máy chủ Cục bộ (Local Development)"),
            new Server().url("http://127.0.0.1:8080").description("Máy chủ Thử nghiệm (Staging / Testing)")
        ))
        .tags(List.of(
            new Tag().name("Users & Authentication").description("APIs Đăng nhập, Đăng ký, Quên mật khẩu, Refresh Token, Quản lý tài khoản & Hồ sơ"),
            new Tag().name("Semester Management").description("APIs Quản lý học kỳ, năm học"),
            new Tag().name("Subject Management").description("APIs Quản lý danh mục môn học (Vật lý 1 và mở rộng Khoa Cơ bản 1)"),
            new Tag().name("Topic Management").description("APIs Quản lý chương mục kiến thức Vật lý 1 (Cơ học, Động học, Năng lượng, Va chạm...)"),
            new Tag().name("Class Management").description("APIs Quản lý lớp học, phân công giảng viên, danh sách lớp"),
            new Tag().name("Student Class & Enrollment").description("APIs Đăng ký vào lớp, phê duyệt sinh viên tham gia học phần"),
            new Tag().name("Question Bank").description("APIs Ngân hàng câu hỏi trắc nghiệm, phân loại Bloom, nhập câu hỏi từ PDF"),
            new Tag().name("Exam Management").description("APIs Cấu hình kỳ thi, sinh ma trận đề, làm bài, nộp bài, kiểm soát số lượt (Multi-attempt)"),
            new Tag().name("Virtual Physics Lab").description("APIs Quản lý 04 bài thí nghiệm ảo 3D Vật lý 1, tiêu chí Rubric đánh giá"),
            new Tag().name("Evidence Management").description("APIs Nộp minh chứng kết quả đo thí nghiệm ảo, chấm điểm & xác nhận kết quả"),
            new Tag().name("AI Socratic Tutor").description("APIs Trợ giảng AI Socratic tiếng Việt: khởi tạo hội thoại, gửi câu hỏi gợi mở, phản hồi"),
            new Tag().name("Learning Material").description("APIs Quản lý học liệu số đã được Bộ môn phê duyệt"),
            new Tag().name("Learning Progress").description("APIs Theo dõi tiến độ học tập và hoàn thành học liệu của sinh viên"),
            new Tag().name("Student Progress").description("APIs Thống kê chỉ số hoàn thành tổng thể của sinh viên trong lớp"),
            new Tag().name("Student Activity Logs").description("APIs Ghi nhận nhật ký tương tác và hoạt động học tập của sinh viên"),
            new Tag().name("Analytics & CTT").description("APIs Phân tích học thuật CTT: Độ khó (p-value), Chỉ số phân biệt (DI), Cron Job tổng hợp"),
            new Tag().name("Dashboard").description("APIs Bảng điều khiển tổng hợp cho Giảng viên và Quản trị viên"),
            new Tag().name("Admin Logs").description("APIs Nhật ký kiểm toán bảo mật và hành động của Quản trị viên"),
            new Tag().name("System Settings").description("APIs Cấu hình tham số động hệ thống (Dynamic Configuration)")
        ))
        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
        .components(new Components()
            .addSecuritySchemes("bearerAuth",
                new SecurityScheme()
                    .name("bearerAuth")
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Nhập Access Token nhận được từ API /api/v1/users/signin")));
  }
}