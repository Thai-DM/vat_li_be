package com.vatly1.example.service;

import com.vatly1.example.model.response.StudentImportResultDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface IStudentExcelService {

    /**
     * Tải tệp Excel mẫu chuẩn hóa để quản trị viên / giảng viên nhập danh sách sinh viên.
     *
     * @return mảng byte của tệp Excel (.xlsx)
     */
    byte[] downloadStudentExcelTemplate();

    /**
     * Đọc tệp Excel (.xlsx, .xls) và tạo tài khoản sinh viên hàng loạt.
     *
     * @param file tệp bảng tính tải lên
     * @param defaultPassword mật khẩu mặc định nếu cột mật khẩu để trống (mặc định: Vatly1@123)
     * @param classId ID lớp học phần tùy chọn để tự động ghi danh sinh viên sau khi tạo tài khoản
     * @return kết quả chi tiết quá trình tạo sinh viên hàng loạt
     */
    StudentImportResultDTO importStudentsFromExcel(MultipartFile file, String defaultPassword, UUID classId);
}
