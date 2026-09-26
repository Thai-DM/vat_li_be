package com.vatly1.example.service.impl;

import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.ClassEnrollment;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.UserProfile;
import com.vatly1.example.entity.enums.EnrollmentStatus;
import com.vatly1.example.entity.enums.GenderType;
import com.vatly1.example.entity.enums.UserRole;
import com.vatly1.example.entity.enums.UserStatus;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.model.dto.StudentImportItemDTO;
import com.vatly1.example.model.response.StudentImportResultDTO;
import com.vatly1.example.repository.IClassEnrollmentRepository;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.IUserProfileRepository;
import com.vatly1.example.repository.IUserRepository;
import com.vatly1.example.service.IStudentExcelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class StudentExcelServiceImpl implements IStudentExcelService {

    private final IUserRepository userRepository;
    private final IUserProfileRepository userProfileRepository;
    private final IClassRepository classRepository;
    private final IClassEnrollmentRepository classEnrollmentRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String DEFAULT_FALLBACK_PASSWORD = "Vatly1@123";
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy")
    );

    @Override
    public byte[] downloadStudentExcelTemplate() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("DanhSachSinhVien");

            // Header Style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setFontHeightInPoints((short) 11);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            // Data Style
            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            CellStyle centerStyle = workbook.createCellStyle();
            centerStyle.cloneStyleFrom(dataStyle);
            centerStyle.setAlignment(HorizontalAlignment.CENTER);

            // Header labels
            String[] headers = {
                    "STT",
                    "Mã sinh viên (*)",
                    "Họ và tên (*)",
                    "Email (*)",
                    "Tên đăng nhập (Tùy chọn)",
                    "Mật khẩu (Tùy chọn)",
                    "Ngày sinh (dd/MM/yyyy)",
                    "Giới tính (Nam/Nữ)",
                    "Số điện thoại",
                    "Mã lớp (Tùy chọn)"
            };

            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(28);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Dữ liệu mẫu (3 sinh viên)
            Object[][] sampleData = {
                    {1, "SV202601", "Nguyễn Văn An", "an.nguyen@student.edu.vn", "sv_an01", "Vatly1@123", "20/08/2004", "Nam", "0987654321", "PHY101-01"},
                    {2, "SV202602", "Trần Thị Bình", "binh.tran@student.edu.vn", "", "", "15/11/2004", "Nữ", "0912345678", "PHY101-01"},
                    {3, "SV202603", "Lê Hùng Cường", "cuong.le@student.edu.vn", "", "", "05/03/2004", "Nam", "0909123456", "PHY101-02"}
            };

            for (int r = 0; r < sampleData.length; r++) {
                Row row = sheet.createRow(r + 1);
                row.setHeightInPoints(22);
                for (int c = 0; c < sampleData[r].length; c++) {
                    Cell cell = row.createCell(c);
                    Object val = sampleData[r][c];
                    if (val instanceof Number) {
                        cell.setCellValue(((Number) val).doubleValue());
                        cell.setCellStyle(centerStyle);
                    } else {
                        cell.setCellValue(val.toString());
                        cell.setCellStyle((c == 1 || c == 4 || c == 6 || c == 7 || c == 8 || c == 9) ? centerStyle : dataStyle);
                    }
                }
            }

            // Ghi chú hướng dẫn ở dòng cuối
            int noteStartRow = sampleData.length + 2;
            Row noteRow1 = sheet.createRow(noteStartRow);
            noteRow1.createCell(0).setCellValue("HƯỚNG DẪN ĐIỀN THÔNG TIN:");
            Row noteRow2 = sheet.createRow(noteStartRow + 1);
            noteRow2.createCell(0).setCellValue("1. Các cột có dấu (*) là bắt buộc. Mã sinh viên và Email không được trùng lặp.");
            Row noteRow3 = sheet.createRow(noteStartRow + 2);
            noteRow3.createCell(0).setCellValue("2. Cột 'Tên đăng nhập' nếu để trống sẽ tự động lấy theo Mã sinh viên (chữ thường).");
            Row noteRow4 = sheet.createRow(noteStartRow + 3);
            noteRow4.createCell(0).setCellValue("3. Cột 'Mật khẩu' nếu để trống sẽ tự động dùng mật khẩu mặc định (mặc định: Vatly1@123).");
            Row noteRow5 = sheet.createRow(noteStartRow + 4);
            noteRow5.createCell(0).setCellValue("4. Cột 'Mã lớp' nếu điền mã lớp hợp lệ thì sinh viên sẽ được tự động ghi danh vào lớp đó.");

            // Tự động căn chỉnh độ rộng cột
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.max(sheet.getColumnWidth(i) + 1200, 3500));
            }

            workbook.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate student excel template: {}", e.getMessage(), e);
            throw new CustomException("Không thể tạo tệp Excel mẫu: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    @Transactional
    public StudentImportResultDTO importStudentsFromExcel(MultipartFile file, String defaultPassword, UUID classId) {
        if (file == null || file.isEmpty()) {
            throw new CustomException("Tệp tải lên không được để trống", HttpStatus.BAD_REQUEST);
        }

        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.toLowerCase().endsWith(".xlsx") && !filename.toLowerCase().endsWith(".xls"))) {
            throw new CustomException("Định dạng tệp không hợp lệ. Vui lòng tải lên tệp Excel (.xlsx hoặc .xls)", HttpStatus.BAD_REQUEST);
        }

        String effectiveDefaultPassword = (defaultPassword != null && !defaultPassword.isBlank()) ? defaultPassword.trim() : DEFAULT_FALLBACK_PASSWORD;

        // Lớp học phần mặc định nếu truyền vào qua parameter
        Class defaultClass = null;
        if (classId != null) {
            defaultClass = classRepository.findById(classId).orElse(null);
        }

        List<StudentImportItemDTO> items = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        int totalCreated = 0;
        int totalSkipped = 0;
        int totalEnrolled = 0;

        try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new CustomException("Tệp Excel không chứa bất kỳ trang tính nào", HttpStatus.BAD_REQUEST);
            }

            Sheet sheet = workbook.getSheetAt(0);
            int firstRowNum = sheet.getFirstRowNum();
            int lastRowNum = sheet.getLastRowNum();

            if (lastRowNum < firstRowNum) {
                throw new CustomException("Trang tính không chứa dữ liệu", HttpStatus.BAD_REQUEST);
            }

            // Tìm dòng header
            int headerRowIndex = -1;
            Map<String, Integer> colMap = null;
            for (int r = firstRowNum; r <= Math.min(firstRowNum + 10, lastRowNum); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                Map<String, Integer> potentialMap = buildColumnMapping(row);
                if (potentialMap.containsKey("studentCode") || potentialMap.containsKey("fullName") || potentialMap.containsKey("email")) {
                    headerRowIndex = r;
                    colMap = potentialMap;
                    break;
                }
            }

            if (headerRowIndex == -1 || colMap == null) {
                throw new CustomException("Không tìm thấy dòng tiêu đề hợp lệ trong tệp Excel. Vui lòng tải file mẫu để xem cấu trúc.", HttpStatus.BAD_REQUEST);
            }

            for (int r = headerRowIndex + 1; r <= lastRowNum; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                // Đọc các giá trị
                String studentCode = getCellValue(row, colMap.get("studentCode"));
                String fullName = getCellValue(row, colMap.get("fullName"));
                String email = getCellValue(row, colMap.get("email"));
                String username = getCellValue(row, colMap.get("username"));
                String password = getCellValue(row, colMap.get("password"));
                String dobStr = getCellValue(row, colMap.get("dateOfBirth"));
                String genderStr = getCellValue(row, colMap.get("gender"));
                String phone = getCellValue(row, colMap.get("phone"));
                String rowClassCode = getCellValue(row, colMap.get("classCode"));

                // Bỏ qua dòng trống hoàn toàn hoặc dòng hướng dẫn
                if ((studentCode == null || studentCode.isBlank()) &&
                        (fullName == null || fullName.isBlank()) &&
                        (email == null || email.isBlank())) {
                    continue;
                }

                int displayRow = r + 1;
                StudentImportItemDTO.StudentImportItemDTOBuilder itemBuilder = StudentImportItemDTO.builder()
                        .rowNumber(displayRow);

                // Kiểm tra bắt buộc: Mã sinh viên
                if (studentCode == null || studentCode.isBlank()) {
                    String msg = String.format("Dòng %d: Thiếu 'Mã sinh viên' bắt buộc", displayRow);
                    errors.add(msg);
                    items.add(itemBuilder.status("ERROR").message(msg).build());
                    totalSkipped++;
                    continue;
                }
                studentCode = studentCode.trim().toUpperCase();
                itemBuilder.studentCode(studentCode);

                // Kiểm tra bắt buộc: Họ và tên
                if (fullName == null || fullName.isBlank()) {
                    String msg = String.format("Dòng %d: Thiếu 'Họ và tên' cho sinh viên [%s]", displayRow, studentCode);
                    errors.add(msg);
                    items.add(itemBuilder.fullName("").status("ERROR").message(msg).build());
                    totalSkipped++;
                    continue;
                }
                fullName = fullName.trim();
                itemBuilder.fullName(fullName);

                // Xử lý Email
                if (email == null || email.isBlank()) {
                    // Tự động sinh email nếu để trống
                    email = studentCode.toLowerCase() + "@student.vatli1.edu.vn";
                } else {
                    email = email.trim().toLowerCase();
                }
                itemBuilder.email(email);

                // Xử lý Tên đăng nhập (username)
                if (username == null || username.isBlank()) {
                    username = studentCode.toLowerCase();
                } else {
                    username = username.trim().toLowerCase();
                }
                itemBuilder.username(username);

                // Xử lý Mật khẩu
                String effectivePassword = (password != null && !password.isBlank()) ? password.trim() : effectiveDefaultPassword;
                itemBuilder.password(effectivePassword);

                // Xử lý Ngày sinh
                LocalDate dob = parseDate(row, colMap.get("dateOfBirth"), dobStr);
                itemBuilder.dateOfBirth(dob);

                // Xử lý Giới tính
                GenderType gender = parseGender(genderStr);
                itemBuilder.gender(gender);

                // Xử lý Số điện thoại
                if (phone != null && !phone.isBlank()) {
                    phone = phone.replaceAll("[^0-9+]", "");
                    itemBuilder.phone(phone);
                }

                // Xử lý Mã lớp học
                String effectiveClassCode = null;
                if (rowClassCode != null && !rowClassCode.isBlank()) {
                    effectiveClassCode = rowClassCode.trim();
                } else if (defaultClass != null) {
                    effectiveClassCode = defaultClass.getClassCode();
                }
                itemBuilder.classCode(effectiveClassCode);

                // Kiểm tra trùng lặp trong CSDL
                if (userRepository.existsByUsername(username)) {
                    String msg = String.format("Dòng %d: Tên đăng nhập '%s' đã tồn tại trong hệ thống.", displayRow, username);
                    warnings.add(msg);
                    User existingUser = userRepository.findByUsername(username);
                    itemBuilder.userId(existingUser != null ? existingUser.getUserId() : null);
                    itemBuilder.status("SKIPPED").message(msg);
                    items.add(itemBuilder.build());
                    totalSkipped++;

                    // Vẫn hỗ trợ ghi danh vào lớp nếu chưa có trong lớp
                    if (existingUser != null && enrollStudentToClass(existingUser.getUserId(), effectiveClassCode, defaultClass)) {
                        totalEnrolled++;
                    }
                    continue;
                }

                if (userRepository.existsByEmail(email)) {
                    String msg = String.format("Dòng %d: Email '%s' đã được sử dụng bởi tài khoản khác.", displayRow, email);
                    warnings.add(msg);
                    items.add(itemBuilder.status("SKIPPED").message(msg).build());
                    totalSkipped++;
                    continue;
                }

                if (userProfileRepository.existsByStudentCode(studentCode)) {
                    String msg = String.format("Dòng %d: Mã sinh viên '%s' đã tồn tại trong hồ sơ sinh viên.", displayRow, studentCode);
                    warnings.add(msg);
                    items.add(itemBuilder.status("SKIPPED").message(msg).build());
                    totalSkipped++;
                    continue;
                }

                // Tạo tài khoản User mới
                User newUser = User.builder()
                        .username(username)
                        .email(email)
                        .passwordHash(passwordEncoder.encode(effectivePassword))
                        .role(UserRole.STUDENT)
                        .status(UserStatus.ACTIVE)
                        .build();
                User savedUser = userRepository.save(newUser);

                // Tạo hồ sơ UserProfile
                UserProfile newProfile = UserProfile.builder()
                        .userId(savedUser.getUserId())
                        .fullName(fullName)
                        .studentCode(studentCode)
                        .dateOfBirth(dob)
                        .gender(gender)
                        .phone(phone)
                        .build();
                userProfileRepository.save(newProfile);

                // Ghi danh vào lớp học phần nếu có chỉ định
                if (enrollStudentToClass(savedUser.getUserId(), effectiveClassCode, defaultClass)) {
                    totalEnrolled++;
                }

                totalCreated++;
                itemBuilder.userId(savedUser.getUserId())
                        .status("SUCCESS")
                        .message("Tạo tài khoản thành công");
                items.add(itemBuilder.build());
            }

        } catch (CustomException ce) {
            throw ce;
        } catch (Exception e) {
            log.error("Failed to parse and import students from Excel: {}", e.getMessage(), e);
            throw new CustomException("Lỗi trong quá trình xử lý tệp Excel: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }

        return StudentImportResultDTO.builder()
                .totalRows(items.size())
                .totalCreated(totalCreated)
                .totalSkipped(totalSkipped)
                .totalEnrolled(totalEnrolled)
                .students(items)
                .warnings(warnings)
                .errors(errors)
                .build();
    }

    private boolean enrollStudentToClass(UUID studentId, String classCode, Class defaultClass) {
        Class targetClass = null;
        if (classCode != null && !classCode.isBlank()) {
            targetClass = classRepository.findByClassCode(classCode).orElse(null);
        }
        if (targetClass == null && defaultClass != null) {
            targetClass = defaultClass;
        }

        if (targetClass != null) {
            if (!classEnrollmentRepository.existsByClassIdAndStudentId(targetClass.getClassId(), studentId)) {
                ClassEnrollment enrollment = ClassEnrollment.builder()
                        .classId(targetClass.getClassId())
                        .studentId(studentId)
                        .status(EnrollmentStatus.ACTIVE)
                        .build();
                classEnrollmentRepository.save(enrollment);
                return true;
            }
        }
        return false;
    }

    private Map<String, Integer> buildColumnMapping(Row headerRow) {
        Map<String, Integer> colMap = new HashMap<>();
        for (int c = 0; c < headerRow.getLastCellNum(); c++) {
            Cell cell = headerRow.getCell(c);
            if (cell == null) continue;
            String headerText = cell.getStringCellValue();
            if (headerText == null || headerText.isBlank()) continue;

            String norm = normalizeText(headerText);
            if (containsAny(norm, "ma sinh vien", "mssv", "ma sv", "student code", "student id", "studentcode")) {
                colMap.put("studentCode", c);
            } else if (containsAny(norm, "ho va ten", "ho ten", "ten sinh vien", "full name", "fullname", "name")) {
                colMap.put("fullName", c);
            } else if (containsAny(norm, "email", "mail", "thu dien tu")) {
                colMap.put("email", c);
            } else if (containsAny(norm, "ten dang nhap", "tai khoan", "username", "user name")) {
                colMap.put("username", c);
            } else if (containsAny(norm, "mat khau", "password", "pass")) {
                colMap.put("password", c);
            } else if (containsAny(norm, "ngay sinh", "dob", "birth", "ngaysinh")) {
                colMap.put("dateOfBirth", c);
            } else if (containsAny(norm, "gioi tinh", "gender", "gioitinh")) {
                colMap.put("gender", c);
            } else if (containsAny(norm, "so dien thoai", "dien thoai", "sdt", "phone", "mobile")) {
                colMap.put("phone", c);
            } else if (containsAny(norm, "ma lop", "lop hoc", "lop", "class code", "classcode", "class")) {
                colMap.put("classCode", c);
            }
        }
        return colMap;
    }

    private String getCellValue(Row row, Integer colIndex) {
        if (colIndex == null) return null;
        Cell cell = row.getCell(colIndex);
        if (cell == null) return null;

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                }
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val)) {
                    yield String.valueOf((long) val);
                }
                yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue().trim();
                } catch (Exception e) {
                    yield String.valueOf(cell.getNumericCellValue());
                }
            }
            default -> null;
        };
    }

    private LocalDate parseDate(Row row, Integer colIndex, String dobStr) {
        if (colIndex != null) {
            Cell cell = row.getCell(colIndex);
            if (cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate();
            }
        }

        if (dobStr == null || dobStr.isBlank()) return null;

        for (DateTimeFormatter dtf : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(dobStr.trim(), dtf);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private GenderType parseGender(String genderStr) {
        if (genderStr == null || genderStr.isBlank()) return null;
        String norm = normalizeText(genderStr);
        if (norm.equals("nam") || norm.equals("male") || norm.equals("m")) {
            return GenderType.MALE;
        }
        if (norm.equals("nu") || norm.equals("female") || norm.equals("f")) {
            return GenderType.FEMALE;
        }
        return GenderType.OTHER;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    private String normalizeText(String input) {
        if (input == null) return "";
        String str = input.replace("đ", "d").replace("Đ", "d");
        String normalized = Normalizer.normalize(str, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(normalized).replaceAll("").toLowerCase().trim();
    }
}
