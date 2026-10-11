package com.vatly1.example.service.impl;

import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.ClassEnrollment;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.UserProfile;
import com.vatly1.example.entity.enums.ClassStatus;
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

import com.vatly1.example.repository.IClassStaffRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
@RequiredArgsConstructor
public class StudentExcelServiceImpl implements IStudentExcelService {

    private final IUserRepository userRepository;
    private final IUserProfileRepository userProfileRepository;
    private final IClassRepository classRepository;
    private final IClassEnrollmentRepository classEnrollmentRepository;
    private final IClassStaffRepository classStaffRepository;
    private final PlatformTransactionManager transactionManager;
    private final PasswordEncoder passwordEncoder;

    private static final String DEFAULT_FALLBACK_PASSWORD = "Vatly1@123";
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("d/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd/M/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("d-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd-M-yyyy"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("d.M.yyyy"),
            DateTimeFormatter.ofPattern("d.MM.yyyy"),
            DateTimeFormatter.ofPattern("dd.M.yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("ddMMyyyy"),
            DateTimeFormatter.ofPattern("yyyyMMdd")
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

            // Dữ liệu mẫu (3 sinh viên) - Cột mật khẩu để trống để hệ thống tự động suy ra 8 số theo ngày sinh
            Object[][] sampleData = {
                    {1, "SV202601", "Nguyễn Văn An", "an.nguyen@student.edu.vn", "sv_an01", "", "20/08/2004", "Nam", "0987654321", "PHY101-01"},
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
            noteRow4.createCell(0).setCellValue("3. Mật khẩu tài khoản sinh viên được hệ thống tự động suy ra gồm 8 số theo Ngày sinh dạng ddmmyyyy (ví dụ: ngày sinh 20/08/2004 -> mật khẩu là 20082004, ngày sinh 05/03/2004 -> mật khẩu là 05032004). Cột 'Mật khẩu' nên để trống.");
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
    public StudentImportResultDTO importStudentsFromExcel(MultipartFile file, String defaultPassword, UUID classId) {
        return importStudentsFromExcel(file, defaultPassword, classId, null, "ADMIN");
    }

    @Override
    public StudentImportResultDTO importStudentsFromExcel(MultipartFile file, String defaultPassword, UUID classId, UUID currentUserId, String currentUserRole) {
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
            if (defaultClass == null) {
                throw new CustomException("Không tìm thấy lớp học phần với ID: " + classId, HttpStatus.NOT_FOUND);
            }
            if (!hasPermissionOnClass(defaultClass, currentUserId, currentUserRole)) {
                throw new CustomException("Bạn không có quyền quản lý lớp học này (" + defaultClass.getClassCode() + ")", HttpStatus.FORBIDDEN);
            }
        }

        List<StudentImportItemDTO> items = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        int totalCreated = 0;
        int totalSkipped = 0;
        int totalEnrolled = 0;

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

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

                // Xử lý Ngày sinh
                LocalDate dob = parseDate(row, colMap.get("dateOfBirth"), dobStr);
                itemBuilder.dateOfBirth(dob);

                // Xử lý Mật khẩu
                String effectivePassword;
                if (dob != null) {
                    effectivePassword = dob.format(DateTimeFormatter.ofPattern("ddMMyyyy"));
                } else if (dobStr != null && !dobStr.replaceAll("[^0-9]", "").isBlank()) {
                    String cleanDigits = dobStr.replaceAll("[^0-9]", "");
                    if (cleanDigits.length() == 8) {
                        effectivePassword = cleanDigits;
                    } else if (cleanDigits.length() == 7) {
                        effectivePassword = "0" + cleanDigits;
                    } else if (cleanDigits.length() >= 8) {
                        effectivePassword = cleanDigits.substring(0, 8);
                    } else {
                        effectivePassword = String.format("%08d", Long.parseLong(cleanDigits));
                    }
                } else if (password != null && !password.isBlank() && !password.trim().equalsIgnoreCase("Vatly1@123")) {
                    effectivePassword = password.trim();
                } else {
                    effectivePassword = effectiveDefaultPassword;
                }
                itemBuilder.password(effectivePassword);

                // Xử lý Giới tính
                GenderType gender = parseGender(genderStr);
                itemBuilder.gender(gender);

                // Xử lý Số điện thoại
                if (phone != null && !phone.isBlank()) {
                    phone = phone.replaceAll("[^0-9+]", "");
                    if (phone.length() == 9 && (phone.startsWith("3") || phone.startsWith("5") || phone.startsWith("7") || phone.startsWith("8") || phone.startsWith("9"))) {
                        phone = "0" + phone;
                    }
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

                // Chuẩn bị biến final để thực thi trong lambda transaction độc lập
                final String fStudentCode = studentCode;
                final String fFullName = fullName;
                final String fEmail = email;
                final String fUsername = username;
                final String fEffectivePassword = effectivePassword;
                final LocalDate fDob = dob;
                final GenderType fGender = gender;
                final String fPhone = phone;
                final String fEffectiveClassCode = effectiveClassCode;
                final Class fDefaultClass = defaultClass;

                final boolean[] rowCreated = new boolean[]{false};
                final boolean[] rowSkipped = new boolean[]{false};
                final boolean[] rowEnrolled = new boolean[]{false};

                // Thực thi giao dịch độc lập cho từng dòng (Atomic Row Transaction)
                transactionTemplate.execute(status -> {
                    // 1. Kiểm tra quyền và tính hợp lệ của lớp học đích
                    Class targetClass = null;
                    if (fEffectiveClassCode != null && !fEffectiveClassCode.isBlank()) {
                        targetClass = classRepository.findByClassCode(fEffectiveClassCode).orElse(null);
                        if (targetClass == null) {
                            String msg = String.format("Dòng %d: Không tìm thấy lớp học có mã '%s'.", displayRow, fEffectiveClassCode);
                            errors.add(msg);
                            items.add(itemBuilder.status("ERROR").message(msg).build());
                            rowSkipped[0] = true;
                            status.setRollbackOnly();
                            return null;
                        }
                        if (!hasPermissionOnClass(targetClass, currentUserId, currentUserRole)) {
                            String msg = String.format("Dòng %d: Bạn không có quyền ghi danh sinh viên vào lớp '%s'.", displayRow, fEffectiveClassCode);
                            errors.add(msg);
                            items.add(itemBuilder.status("ERROR").message(msg).build());
                            rowSkipped[0] = true;
                            status.setRollbackOnly();
                            return null;
                        }
                    } else if (fDefaultClass != null) {
                        targetClass = fDefaultClass;
                    }

                    // 2. Tìm kiếm người dùng hiện có (hỗ trợ cập nhật khi re-import)
                    User existingUser = userRepository.findByUsername(fUsername);
                    if (existingUser == null && fEmail != null) {
                        existingUser = userRepository.findByEmail(fEmail).orElse(null);
                    }
                    if (existingUser == null) {
                        UserProfile existingProfile = userProfileRepository.findByStudentCode(fStudentCode).orElse(null);
                        if (existingProfile != null) {
                            existingUser = userRepository.findById(existingProfile.getUserId()).orElse(null);
                        }
                    }

                    UUID studentUserId;
                    if (existingUser != null) {
                        studentUserId = existingUser.getUserId();
                        existingUser.setPasswordHash(passwordEncoder.encode(fEffectivePassword));
                        if (fEmail != null && !fEmail.equalsIgnoreCase(existingUser.getEmail())) {
                            if (!userRepository.existsByEmail(fEmail)) {
                                existingUser.setEmail(fEmail);
                            }
                        }
                        userRepository.save(existingUser);

                        UserProfile profile = userProfileRepository.findById(existingUser.getUserId()).orElse(null);
                        if (profile == null) {
                            profile = UserProfile.builder().userId(existingUser.getUserId()).build();
                        }
                        profile.setFullName(fFullName);
                        profile.setStudentCode(fStudentCode);
                        if (fDob != null) profile.setDateOfBirth(fDob);
                        if (fGender != null) profile.setGender(fGender);
                        if (fPhone != null && !fPhone.isBlank()) profile.setPhone(fPhone);
                        userProfileRepository.save(profile);

                        rowSkipped[0] = true;
                        itemBuilder.userId(existingUser.getUserId())
                                .status("SKIPPED")
                                .message(String.format("Tài khoản sinh viên '%s' đã tồn tại trong hệ thống (đã cập nhật hồ sơ & mật khẩu theo ngày sinh: %s)", fUsername, fEffectivePassword));
                    } else {
                        // Kiểm tra xung đột trước khi tạo mới
                        if (userRepository.existsByUsername(fUsername)) {
                            String msg = String.format("Dòng %d: Tên đăng nhập '%s' đã tồn tại cho một người dùng khác.", displayRow, fUsername);
                            errors.add(msg);
                            items.add(itemBuilder.status("ERROR").message(msg).build());
                            rowSkipped[0] = true;
                            status.setRollbackOnly();
                            return null;
                        }

                        if (userRepository.existsByEmail(fEmail)) {
                            String msg = String.format("Dòng %d: Email '%s' đã được sử dụng bởi tài khoản khác.", displayRow, fEmail);
                            errors.add(msg);
                            items.add(itemBuilder.status("ERROR").message(msg).build());
                            rowSkipped[0] = true;
                            status.setRollbackOnly();
                            return null;
                        }

                        User newUser = User.builder()
                                .username(fUsername)
                                .email(fEmail)
                                .passwordHash(passwordEncoder.encode(fEffectivePassword))
                                .role(UserRole.STUDENT)
                                .status(UserStatus.ACTIVE)
                                .build();
                        User savedUser = userRepository.save(newUser);
                        studentUserId = savedUser.getUserId();

                        UserProfile newProfile = UserProfile.builder()
                                .userId(savedUser.getUserId())
                                .fullName(fFullName)
                                .studentCode(fStudentCode)
                                .dateOfBirth(fDob)
                                .gender(fGender)
                                .phone(fPhone)
                                .build();
                        userProfileRepository.save(newProfile);

                        rowCreated[0] = true;
                        itemBuilder.userId(savedUser.getUserId())
                                .status("SUCCESS")
                                .message(String.format("Tạo tài khoản thành công (mật khẩu: %s)", fEffectivePassword));
                    }

                    // 3. Thực hiện ghi danh vào lớp đích nếu có
                    if (targetClass != null) {
                        if (targetClass.getStatus() != ClassStatus.ACTIVE) {
                            String msg = String.format("Dòng %d: Lớp học '%s' không ở trạng thái hoạt động (ACTIVE).", displayRow, targetClass.getClassCode());
                            errors.add(msg);
                            items.add(itemBuilder.status("ERROR").message(msg).build());
                            rowCreated[0] = false;
                            rowSkipped[0] = true;
                            status.setRollbackOnly();
                            return null;
                        }

                        // Kiểm tra sinh viên có đang ACTIVE ở lớp nào KHÁC cùng môn học và học kỳ không
                        List<ClassEnrollment> existingList = classEnrollmentRepository.findByStudentIdAndSubjectIdAndSemesterId(
                                studentUserId, targetClass.getSubjectId(), targetClass.getSemesterId());
                        final UUID targetClassId = targetClass.getClassId();
                        Optional<ClassEnrollment> activeOther = existingList.stream()
                                .filter(e -> e.getStatus() == EnrollmentStatus.ACTIVE && !e.getClassId().equals(targetClassId))
                                .findFirst();
                        if (activeOther.isPresent()) {
                            String otherCode = classRepository.findById(activeOther.get().getClassId())
                                    .map(Class::getClassCode).orElse(activeOther.get().getClassId().toString());
                            String msg = String.format("Dòng %d: Sinh viên '%s' đã có đăng ký hoạt động ở lớp khác (%s) của môn học này trong cùng học kỳ.", displayRow, fStudentCode, otherCode);
                            errors.add(msg);
                            items.add(itemBuilder.status("ERROR").message(msg).build());
                            rowCreated[0] = false;
                            rowSkipped[0] = true;
                            status.setRollbackOnly();
                            return null;
                        }

                        // Kiểm tra sĩ số tối đa nếu cần thêm mới
                        Optional<ClassEnrollment> existingEnr = classEnrollmentRepository.findByClassIdAndStudentId(targetClass.getClassId(), studentUserId);
                        if (existingEnr.isEmpty() || existingEnr.get().getStatus() != EnrollmentStatus.ACTIVE) {
                            if (targetClass.getMaxStudents() != null) {
                                int currentCount = classEnrollmentRepository.countByClassIdAndStatus(targetClass.getClassId(), EnrollmentStatus.ACTIVE);
                                if (currentCount >= targetClass.getMaxStudents()) {
                                    String msg = String.format("Dòng %d: Lớp '%s' đã đủ số lượng sinh viên tối đa (%d).", displayRow, targetClass.getClassCode(), targetClass.getMaxStudents());
                                    errors.add(msg);
                                    items.add(itemBuilder.status("ERROR").message(msg).build());
                                    rowCreated[0] = false;
                                    rowSkipped[0] = true;
                                    status.setRollbackOnly();
                                    return null;
                                }
                            }
                        }

                        // Lưu hoặc tái kích hoạt enrollment
                        try {
                            if (existingEnr.isPresent()) {
                                ClassEnrollment enr = existingEnr.get();
                                if (enr.getStatus() != EnrollmentStatus.ACTIVE) {
                                    enr.setStatus(EnrollmentStatus.ACTIVE);
                                    enr.setSubjectId(targetClass.getSubjectId());
                                    enr.setSemesterId(targetClass.getSemesterId());
                                    classEnrollmentRepository.save(enr);
                                    rowEnrolled[0] = true;
                                }
                            } else {
                                ClassEnrollment enr = ClassEnrollment.builder()
                                        .classId(targetClass.getClassId())
                                        .studentId(studentUserId)
                                        .subjectId(targetClass.getSubjectId())
                                        .semesterId(targetClass.getSemesterId())
                                        .status(EnrollmentStatus.ACTIVE)
                                        .build();
                                classEnrollmentRepository.save(enr);
                                rowEnrolled[0] = true;
                            }
                        } catch (org.springframework.dao.DataIntegrityViolationException dive) {
                            String msg = String.format("Dòng %d: Xung đột ghi danh sinh viên '%s' vào lớp '%s' (vi phạm ràng buộc dữ liệu môn/học kỳ).", displayRow, fStudentCode, targetClass.getClassCode());
                            errors.add(msg);
                            items.add(itemBuilder.status("ERROR").message(msg).build());
                            rowCreated[0] = false;
                            rowSkipped[0] = true;
                            status.setRollbackOnly();
                            return null;
                        }
                    }

                    items.add(itemBuilder.build());
                    return null;
                });

                if (rowCreated[0]) totalCreated++;
                if (rowSkipped[0]) totalSkipped++;
                if (rowEnrolled[0]) totalEnrolled++;
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

    private boolean hasPermissionOnClass(Class clazz, UUID currentUserId, String currentUserRole) {
        if (currentUserId == null || currentUserRole == null) {
            return true;
        }
        UserRole role;
        try {
            role = UserRole.valueOf(currentUserRole.toUpperCase());
        } catch (Exception e) {
            return false;
        }
        if (role == UserRole.ADMIN) {
            return true;
        }
        if (role == UserRole.INSTRUCTOR) {
            boolean isOwner = clazz.getInstructorId() != null && clazz.getInstructorId().equals(currentUserId);
            boolean isAssigned = classStaffRepository.existsByClassIdAndUserId(clazz.getClassId(), currentUserId);
            return isOwner || isAssigned;
        }
        if (role == UserRole.TA) {
            return classStaffRepository.existsByClassIdAndUserId(clazz.getClassId(), currentUserId);
        }
        return false;
    }

    private Map<String, Integer> buildColumnMapping(Row headerRow) {
        Map<String, Integer> colMap = new HashMap<>();
        for (int c = 0; c < headerRow.getLastCellNum(); c++) {
            String headerText = getCellValue(headerRow, c);
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
            if (cell != null && cell.getCellType() == CellType.NUMERIC) {
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toLocalDate();
                } else if (DateUtil.isValidExcelDate(cell.getNumericCellValue())) {
                    try {
                        return DateUtil.getLocalDateTime(cell.getNumericCellValue()).toLocalDate();
                    } catch (Exception ignored) {
                    }
                }
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
