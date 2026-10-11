package com.vatly1.example.service;

import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.*;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.enums.*;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.model.request.CreateQuestionDTO;
import com.vatly1.example.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@ActiveProfiles("test")
@Transactional
class FinalExamHistoricalIntegrityTest {

    @Autowired
    private IQuestionBankService questionBankService;

    @Autowired
    private QuestionBankRepository questionBankRepository;

    @Autowired
    private QuestionOptionRepository questionOptionRepository;

    @Autowired
    private IExamRepository examRepository;

    @Autowired
    private IExamQuestionRepository examQuestionRepository;

    @Autowired
    private IExamAttemptRepository examAttemptRepository;

    @Autowired
    private ISubjectRepository subjectRepository;

    @Autowired
    private IClassRepository classRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private ICloRepository cloRepository;

    @Autowired
    private IUserRepository userRepository;

    private User admin;
    private Subject subject;
    private Topic topic;
    private Clo clo;
    private QuestionBank questionUsedInExam;
    private Exam exam;

    @BeforeEach
    void setUp() {
        admin = userRepository.save(User.builder()
                .username("hist_admin_" + UUID.randomUUID())
                .email("hist_admin_" + UUID.randomUUID() + "@test.com")
                .role(UserRole.ADMIN)
                .build());

        subject = subjectRepository.save(Subject.builder()
                .subjectCode("HIST101_" + UUID.randomUUID().toString().substring(0, 6))
                .subjectName("Historical Physics")
                .build());

        topic = topicRepository.save(Topic.builder()
                .subjectId(subject.getSubjectId())
                .topicName("Historical Topic")
                .orderIndex(1)
                .build());

        clo = cloRepository.save(Clo.builder()
                .subjectId(subject.getSubjectId())
                .cloCode("CLO_HIST")
                .description("Historical CLO")
                .orderIndex(1)
                .isActive(true)
                .build());

        questionUsedInExam = questionBankRepository.save(QuestionBank.builder()
                .subjectId(subject.getSubjectId())
                .topicId(topic.getTopicId())
                .content("Used Question Content?")
                .questionType(QuestionType.MCQ_SINGLE)
                .difficultyLevel(DifficultyLevel.MEDIUM)
                .approvalStatus(ApprovalStatus.APPROVED)
                .createdBy(admin.getUserId())
                .createdAt(Instant.now())
                .build());

        questionOptionRepository.save(QuestionOption.builder()
                .questionId(questionUsedInExam.getQuestionId())
                .optionText("Correct Option")
                .isCorrect(true)
                .orderIndex(1)
                .build());

        Class clazz = classRepository.save(Class.builder()
                .subjectId(subject.getSubjectId())
                .classCode("HIST_CLASS_" + UUID.randomUUID().toString().substring(0, 6))
                .status(ClassStatus.ACTIVE)
                .build());

        exam = examRepository.save(Exam.builder()
                .classId(clazz.getClassId())
                .title("Historical Final Exam")
                .examType(ExamType.FINAL)
                .durationMinutes(90)
                .createdBy(admin.getUserId())
                .createdAt(Instant.now())
                .build());

        examQuestionRepository.save(ExamQuestion.builder()
                .examId(exam.getExamId())
                .questionId(questionUsedInExam.getQuestionId())
                .orderIndex(1)
                .scoreWeight(BigDecimal.TEN)
                .build());
    }

    @Test
    @DisplayName("Historical Question Integrity: Updating a question used in an exam is blocked")
    void testCannotUpdateQuestionUsedInExam() {
        CreateQuestionDTO updateDto = new CreateQuestionDTO();
        updateDto.setSubjectId(subject.getSubjectId());
        updateDto.setTopicId(topic.getTopicId());
        updateDto.setContent("Modified Content");
        updateDto.setQuestionType(QuestionType.MCQ_SINGLE);
        updateDto.setDifficultyLevel(DifficultyLevel.HARD);

        CustomException ex = assertThrows(CustomException.class, () ->
                questionBankService.updateQuestion(questionUsedInExam.getQuestionId(), updateDto, admin.getUserId(), "ADMIN"));

        assertTrue(ex.getMessage().contains("used in an exam"));
    }

    @Test
    @DisplayName("Historical Question Integrity: Deleting a question used in an exam is blocked")
    void testCannotDeleteQuestionUsedInExam() {
        CustomException ex = assertThrows(CustomException.class, () ->
                questionBankService.deleteQuestion(questionUsedInExam.getQuestionId(), admin.getUserId(), "ADMIN"));

        assertTrue(ex.getMessage().contains("used in an exam"));
    }

    @Test
    @DisplayName("Historical Question Integrity: Modifying CLOs of a question used in an exam is blocked")
    void testCannotModifyClosOfQuestionUsedInExam() {
        CustomException ex = assertThrows(CustomException.class, () ->
                questionBankService.assignClosToQuestion(questionUsedInExam.getQuestionId(), List.of(clo.getCloId()), admin.getUserId(), "ADMIN"));

        assertTrue(ex.getMessage().contains("used in an exam"));
    }

    @Test
    @DisplayName("Question Governance: Approved question cannot be rejected directly")
    void testCannotRejectApprovedQuestion() {
        CustomException ex = assertThrows(CustomException.class, () ->
                questionBankService.rejectQuestion(questionUsedInExam.getQuestionId(), "Reject Reason", admin.getUserId(), "ADMIN", "comment"));

        assertTrue(ex.getMessage().contains("đã được phê duyệt"));
    }
}
