package com.vatly1.example;

import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.model.response.AuthResponseDTO;
import com.vatly1.example.model.response.StudentImportResultDTO;
import com.vatly1.example.service.IStudentExcelService;
import com.vatly1.example.service.IUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@ActiveProfiles("test")
public class TestStudentExcelImport {

    @Autowired
    private IStudentExcelService studentExcelService;

    @Autowired
    private IUserService userService;

    @Test
    void testExportTemplateAndImport() throws Exception {
        // 1. Cập nhật và lưu lại file mẫu chuẩn vào D:\vatli1\mau_import_sinh_vien.xlsx
        byte[] templateBytes = studentExcelService.downloadStudentExcelTemplate();
        assertNotNull(templateBytes);
        assertTrue(templateBytes.length > 0);

        File targetFile = new File("D:\\vatli1\\mau_import_sinh_vien.xlsx");
        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            fos.write(templateBytes);
        }
        assertTrue(targetFile.exists());

        // 2. Thực hiện import từ file mẫu vừa cập nhật
        StudentImportResultDTO result;
        try (FileInputStream fis = new FileInputStream(targetFile)) {
            MockMultipartFile multipartFile = new MockMultipartFile(
                    "file",
                    "mau_import_sinh_vien.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    fis
            );

            result = studentExcelService.importStudentsFromExcel(multipartFile, null, null);
        }

        System.out.println("=== IMPORT RESULT ===");
        System.out.println("Total rows: " + result.getTotalRows());
        System.out.println("Total created: " + result.getTotalCreated());
        System.out.println("Total skipped: " + result.getTotalSkipped());
        System.out.println("Total enrolled: " + result.getTotalEnrolled());
        System.out.println("Errors: " + result.getErrors());
        System.out.println("Warnings: " + result.getWarnings());

        assertEquals(3, result.getTotalRows());
        assertEquals(3, result.getTotalCreated());
        assertEquals(0, result.getErrors().size());

        // Kiểm tra mật khẩu của từng sinh viên được tạo
        var students = result.getStudents();
        assertNotNull(students);
        assertEquals(3, students.size());

        var sv1 = students.get(0);
        assertEquals("SV202601", sv1.getStudentCode());
        assertEquals("sv_an01", sv1.getUsername());
        assertEquals("20082004", sv1.getPassword());
        assertEquals("SUCCESS", sv1.getStatus());

        var sv2 = students.get(1);
        assertEquals("SV202602", sv2.getStudentCode());
        assertEquals("sv202602", sv2.getUsername());
        assertEquals("15112004", sv2.getPassword());
        assertEquals("SUCCESS", sv2.getStatus());

        var sv3 = students.get(2);
        assertEquals("SV202603", sv3.getStudentCode());
        assertEquals("sv202603", sv3.getUsername());
        assertEquals("05032004", sv3.getPassword());
        assertEquals("SUCCESS", sv3.getStatus());

        // 3. Kiểm tra đăng nhập với mật khẩu là ngày sinh (cả dạng 20082004 và 20/08/2004)
        AuthResponseDTO auth1 = userService.signin("sv_an01", "20082004");
        assertNotNull(auth1);
        assertNotNull(auth1.getAccessToken());

        AuthResponseDTO auth1Slash = userService.signin("sv_an01", "20/08/2004");
        assertNotNull(auth1Slash);
        assertNotNull(auth1Slash.getAccessToken());

        AuthResponseDTO auth2 = userService.signin("sv202602", "15112004");
        assertNotNull(auth2);

        AuthResponseDTO auth2Slash = userService.signin("sv202602", "15/11/2004");
        assertNotNull(auth2Slash);

        AuthResponseDTO auth3 = userService.signin("sv202603", "05032004");
        assertNotNull(auth3);

        AuthResponseDTO auth3Slash = userService.signin("sv202603", "05/03/2004");
        assertNotNull(auth3Slash);

        // 4. Kiểm tra re-import (nhập lại lần 2 không bị lỗi và vẫn cập nhật thành công)
        try (FileInputStream fis = new FileInputStream(targetFile)) {
            MockMultipartFile multipartFile = new MockMultipartFile(
                    "file",
                    "mau_import_sinh_vien.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    fis
            );

            StudentImportResultDTO reimportResult = studentExcelService.importStudentsFromExcel(multipartFile, null, null);
            System.out.println("=== RE-IMPORT RESULT ===");
            System.out.println("Re-import created/updated: " + reimportResult.getTotalCreated());
            System.out.println("Re-import skipped: " + reimportResult.getTotalSkipped());
            System.out.println("Re-import errors: " + reimportResult.getErrors());

            assertEquals(3, reimportResult.getTotalRows());
            assertEquals(0, reimportResult.getTotalCreated());
            assertEquals(3, reimportResult.getTotalSkipped());
            assertEquals(0, reimportResult.getErrors().size());
        }

        System.out.println(">>> TOÀN BỘ TEST IMPORT & LOGIN BẰNG NGÀY SINH ĐÃ THÀNH CÔNG RỰC RỠ! <<<");
    }
}
