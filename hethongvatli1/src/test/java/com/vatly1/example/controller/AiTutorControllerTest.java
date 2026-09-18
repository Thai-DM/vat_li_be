package com.vatly1.example.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.AiConversation;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.ClassEnrollment;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.enums.EnrollmentStatus;
import com.vatly1.example.repository.IAiConversationRepository;
import com.vatly1.example.repository.IClassEnrollmentRepository;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.IUserRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AiTutorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IAiConversationRepository conversationRepository;

    @Autowired
    private IClassRepository classRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private IClassEnrollmentRepository classEnrollmentRepository;

    private String studentToken;
    private String studentBToken;
    private String instructorToken;
    private String classId;

    @BeforeEach
    void setUp() throws Exception {
        studentToken = signin("sv_an", "sv_an123456");
        studentBToken = signin("sv_binh", "sv_binh123456");
        instructorToken = signin("gv_nguyen", "gv_nguyen123456");

        User svAn = userRepository.findByUsername("sv_an");
        Class targetClass = classRepository.findAll().stream()
                .filter(c -> "PHY101-01".equals(c.getClassCode()))
                .findFirst()
                .orElseThrow();
        classId = targetClass.getClassId().toString();

        if (!classEnrollmentRepository.existsByClassIdAndStudentId(targetClass.getClassId(), svAn.getUserId())) {
            classEnrollmentRepository.save(ClassEnrollment.builder()
                    .classId(targetClass.getClassId())
                    .studentId(svAn.getUserId())
                    .status(EnrollmentStatus.ACTIVE)
                    .build());
        }
    }

    private String signin(String username, String password) throws Exception {
        String bodyContent = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        String body = mockMvc.perform(post("/api/v1/users/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyContent))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("data").get("accessToken").asText();
    }

    private String startConversationHelper(String token, String cId) throws Exception {
        String payload = """
            {
                "classId": "%s",
                "mode": "TEXT"
            }
            """.formatted(cId);

        String res = mockMvc.perform(post("/api/v1/ai-tutor/conversations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(res).get("data").get("conversationId").asText();
    }

    @Test
    @DisplayName("AI-01: Sinh viên khởi tạo phiên hỏi đáp với Trợ giảng AI Socratic thành công")
    void testAi01_StartConversationSuccess() throws Exception {
        String convId = startConversationHelper(studentToken, classId);
        Assertions.assertNotNull(convId);

        mockMvc.perform(get("/api/v1/ai-tutor/conversations/my")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].conversationId").value(convId));
    }

    @Test
    @DisplayName("AI-02: Sinh viên gửi câu hỏi cho AI và nhận phản hồi phân tích Socratic")
    void testAi02_SendMessageAndReceiveReply() throws Exception {
        String convId = startConversationHelper(studentToken, classId);

        String messagePayload = """
            {
                "content": "Thầy ơi cho em hỏi tại sao trong dao động điều hòa vận tốc lại sớm pha hơn li độ pi/2?"
            }
            """;

        mockMvc.perform(post("/api/v1/ai-tutor/conversations/" + convId + "/messages")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(messagePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sender").value("AI"))
                .andExpect(jsonPath("$.data.contentText").isNotEmpty());
    }

    @Test
    @DisplayName("AI-03: Sinh viên tra cứu toàn bộ lịch sử tin nhắn trong phiên hội thoại")
    void testAi03_GetConversationHistory() throws Exception {
        String convId = startConversationHelper(studentToken, classId);

        // Send 1 message
        mockMvc.perform(post("/api/v1/ai-tutor/conversations/" + convId + "/messages")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"Hỏi về bảo toàn cơ năng\"}"))
                .andExpect(status().isOk());

        // Get history (should contain user message and AI reply -> 2 messages)
        mockMvc.perform(get("/api/v1/ai-tutor/conversations/" + convId + "/messages")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("AI-04: (IDOR) Sinh viên B cố xem lịch sử hội thoại của Sinh viên A bị chặn 403")
    void testAi04_IdorPreventStudentViewingAnotherConversation() throws Exception {
        String convId = startConversationHelper(studentToken, classId);

        mockMvc.perform(get("/api/v1/ai-tutor/conversations/" + convId + "/messages")
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AI-05: Giảng viên cố dùng endpoint AI Tutor của sinh viên bị chặn 403 Forbidden")
    void testAi05_RbacPreventNonStudentStartingAiChat() throws Exception {
        String payload = """
            {
                "classId": "%s",
                "mode": "TEXT"
            }
            """.formatted(classId);

        mockMvc.perform(post("/api/v1/ai-tutor/conversations")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AI-06: Chặn gửi tin nhắn vào phiên hội thoại đã kết thúc (400 Bad Request)")
    void testAi06_PreventMessageInEndedConversation() throws Exception {
        String convId = startConversationHelper(studentToken, classId);

        // End conversation
        mockMvc.perform(put("/api/v1/ai-tutor/conversations/" + convId + "/end")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());

        // Attempt to send message to closed conversation
        mockMvc.perform(post("/api/v1/ai-tutor/conversations/" + convId + "/messages")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"Câu hỏi sau khi kết thúc\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("AI-07: Sinh viên kết thúc phiên hội thoại - Kiểm tra ended_at trong CSDL")
    void testAi07_EndConversationSuccess() throws Exception {
        String convId = startConversationHelper(studentToken, classId);

        mockMvc.perform(put("/api/v1/ai-tutor/conversations/" + convId + "/end")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.endedAt").isNotEmpty());

        AiConversation dbConv = conversationRepository.findById(UUID.fromString(convId)).orElseThrow();
        Assertions.assertNotNull(dbConv.getEndedAt());
    }

    @Test
    @DisplayName("AI-08: (IDOR) Sinh viên B cố đóng phiên hội thoại của Sinh viên A bị chặn 403")
    void testAi08_IdorPreventStudentEndingAnotherConversation() throws Exception {
        String convId = startConversationHelper(studentToken, classId);

        mockMvc.perform(put("/api/v1/ai-tutor/conversations/" + convId + "/end")
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AI-09: [IDOR Ghi] Sinh viên B cố gửi tin nhắn vào phiên hội thoại của Sinh viên A bị chặn 403")
    void testAi09_IdorPreventStudentSendingMessageInAnotherConversation() throws Exception {
        // Student A starts conversation
        String convId = startConversationHelper(studentToken, classId);

        String messagePayload = """
            {
                "content": "Tin nhắn giả mạo từ Sinh viên B nhằm phá hoại phiên chat của Sinh viên A"
            }
            """;

        // Student B tries to inject message into Student A's conversation
        mockMvc.perform(post("/api/v1/ai-tutor/conversations/" + convId + "/messages")
                        .header("Authorization", "Bearer " + studentBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(messagePayload))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AI-10: [IDOR Ghi] Sinh viên B cố gửi đánh giá/feedback cho tin nhắn AI của Sinh viên A bị chặn 403")
    void testAi10_IdorPreventStudentSubmittingFeedbackForAnotherStudentMessage() throws Exception {
        // Student A starts conversation and sends a message
        String convId = startConversationHelper(studentToken, classId);
        String msgRes = mockMvc.perform(post("/api/v1/ai-tutor/conversations/" + convId + "/messages")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"Câu hỏi của A\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String messageId = objectMapper.readTree(msgRes).get("data").get("messageId").asText();

        // Student B tries to submit feedback for Student A's AI message
        String feedbackPayload = """
            {
                "rating": 5,
                "comment": "Đánh giá giả mạo từ Sinh viên B"
            }
            """;

        mockMvc.perform(post("/api/v1/ai-tutor/messages/" + messageId + "/feedback")
                        .header("Authorization", "Bearer " + studentBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(feedbackPayload))
                .andExpect(status().isForbidden());
    }
}
