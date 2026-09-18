package com.vatly1.example.service;

public interface IEmailService {

    /**
     * Gửi email chứa mã token / link đặt lại mật khẩu cho người dùng.
     *
     * @param toEmail Địa chỉ email người nhận
     * @param token   Mã token đặt lại mật khẩu ngẫu nhiên
     */
    void sendPasswordResetEmail(String toEmail, String token);
}