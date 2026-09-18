package com.vatly1.example.service.impl;

import com.vatly1.example.service.IEmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailServiceImpl implements IEmailService {

    @Override
    public void sendPasswordResetEmail(String toEmail, String token) {
        log.info("================================================================================");
        log.info("📧 [EMAIL NOTIFICATION SERVICE] GỬI EMAIL ĐẶT LẠI MẬT KHẨU");
        log.info("   Đến người nhận : {}", toEmail);
        log.info("   Mã Token       : {}", token);
        log.info("   Thời hạn       : 15 phút kể từ thời điểm gửi");
        log.info("   Link khôi phục : http://localhost:8080/api/v1/users/reset-password?token={}", token);
        log.info("   Lưu ý an toàn  : Không chia sẻ mã token này với bất kỳ ai để bảo vệ tài khoản.");
        log.info("================================================================================");
    }
}