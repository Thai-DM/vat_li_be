package com.vatly1.example.service;

import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.Chapter;
import com.vatly1.example.entity.QuestionAcceptedAnswer;
import com.vatly1.example.entity.QuestionBank;
import com.vatly1.example.entity.Subject;
import com.vatly1.example.entity.Topic;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.enums.ApprovalStatus;
import com.vatly1.example.entity.enums.DifficultyLevel;
import com.vatly1.example.entity.enums.QuestionType;
import com.vatly1.example.repository.IChapterRepository;
import com.vatly1.example.repository.ISubjectRepository;
import com.vatly1.example.repository.IUserRepository;
import com.vatly1.example.repository.QuestionAcceptedAnswerRepository;
import com.vatly1.example.repository.QuestionBankRepository;
import com.vatly1.example.repository.TopicRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@ActiveProfiles("pgtest")
@ContextConfiguration(initializers = PostgreSqlTestContextInitializer.class)
@EnabledIfEnvironmentVariable(named = "RUN_PG_INTEGRATION_TESTS", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PostgreSqlFillBlankIntegrityIntegrationTest {

    @Autowired
    private QuestionAcceptedAnswerRepository questionAcceptedAnswerRepository;

    @Autowired
    private QuestionBankRepository questionBankRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private IChapterRepository chapterRepository;

    @Autowired
    private ISubjectRepository subjectRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<UUID> createdAnswerIds = new ArrayList<>();
    private final List<UUID> createdQuestionIds = new ArrayList<>();
    private final List<UUID> createdTopicIds = new ArrayList<>();
    private final List<UUID> createdChapterIds = new ArrayList<>();
    private final List<UUID> createdSubjectIds = new ArrayList<>();

    private Subject testSubject;
    private Chapter testChapter;
    private Topic testTopic;
    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = userRepository.findByUsername("admin");
        assertNotNull(testUser, "admin user must exist in PostgreSQL database");

        testSubject = subjectRepository.save(Subject.builder()
                .subjectCode("PG_FB_" + UUID.randomUUID().toString().substring(0, 8))
                .subjectName("PG Fill Blank Test Subject")
                .isActive(true)
                .build());
        createdSubjectIds.add(testSubject.getSubjectId());

        testChapter = chapterRepository.save(Chapter.builder()
                .subjectId(testSubject.getSubjectId())
                .chapterCode("PG_CH_" + UUID.randomUUID().toString().substring(0, 8))
                .chapterName("PG Fill Blank Chapter")
                .orderIndex(1)
                .isActive(true)
                .build());
        createdChapterIds.add(testChapter.getChapterId());

        testTopic = topicRepository.save(Topic.builder()
                .subjectId(testSubject.getSubjectId())
                .chapterId(testChapter.getChapterId())
                .topicName("PG Fill Blank Topic")
                .orderIndex(1)
                .build());
        createdTopicIds.add(testTopic.getTopicId());
    }

    @AfterEach
    void tearDown() {
        for (UUID id : createdAnswerIds) {
            try { questionAcceptedAnswerRepository.deleteById(id); } catch (Exception ignored) {}
        }
        for (UUID id : createdQuestionIds) {
            try { questionBankRepository.deleteById(id); } catch (Exception ignored) {}
        }
        for (UUID id : createdTopicIds) {
            try { topicRepository.deleteById(id); } catch (Exception ignored) {}
        }
        for (UUID id : createdChapterIds) {
            try { chapterRepository.deleteById(id); } catch (Exception ignored) {}
        }
        for (UUID id : createdSubjectIds) {
            try { subjectRepository.deleteById(id); } catch (Exception ignored) {}
        }
    }

    @Test
    @DisplayName("PG-1: Table question_accepted_answers schema và CRUD trên Neon PostgreSQL")
    void testQuestionAcceptedAnswersCrudOnPostgres() {
        QuestionBank q = questionBankRepository.save(QuestionBank.builder()
                .subjectId(testSubject.getSubjectId())
                .topicId(testTopic.getTopicId())
                .questionType(QuestionType.FILL_BLANK)
                .content("Neon DB Fill Blank Test Question: ____")
                .difficultyLevel(DifficultyLevel.EASY)
                .approvalStatus(ApprovalStatus.APPROVED)
                .createdBy(testUser.getUserId())
                .build());
        createdQuestionIds.add(q.getQuestionId());

        QuestionAcceptedAnswer a1 = questionAcceptedAnswerRepository.save(QuestionAcceptedAnswer.builder()
                .questionId(q.getQuestionId())
                .acceptedText("Đáp án 1")
                .orderIndex(0)
                .build());
        createdAnswerIds.add(a1.getAcceptedAnswerId());

        QuestionAcceptedAnswer a2 = questionAcceptedAnswerRepository.save(QuestionAcceptedAnswer.builder()
                .questionId(q.getQuestionId())
                .acceptedText("Đáp án 2")
                .orderIndex(1)
                .build());
        createdAnswerIds.add(a2.getAcceptedAnswerId());

        List<QuestionAcceptedAnswer> list = questionAcceptedAnswerRepository.findByQuestionIdOrderByOrderIndexAsc(q.getQuestionId());
        assertEquals(2, list.size());
        assertEquals("Đáp án 1", list.get(0).getAcceptedText());
        assertEquals("Đáp án 2", list.get(1).getAcceptedText());
    }

    @Test
    @DisplayName("PG-2: Cascade Delete khi xóa câu hỏi trong question_bank trên Neon PostgreSQL")
    void testCascadeDeleteOnPostgres() {
        QuestionBank q = questionBankRepository.save(QuestionBank.builder()
                .subjectId(testSubject.getSubjectId())
                .topicId(testTopic.getTopicId())
                .questionType(QuestionType.FILL_BLANK)
                .content("Question to be deleted: ____")
                .difficultyLevel(DifficultyLevel.MEDIUM)
                .approvalStatus(ApprovalStatus.DRAFT)
                .createdBy(testUser.getUserId())
                .build());

        QuestionAcceptedAnswer a = questionAcceptedAnswerRepository.save(QuestionAcceptedAnswer.builder()
                .questionId(q.getQuestionId())
                .acceptedText("Cascade target")
                .orderIndex(0)
                .build());
        UUID answerId = a.getAcceptedAnswerId();

        assertTrue(questionAcceptedAnswerRepository.existsById(answerId));

        // Xóa question_bank
        questionBankRepository.deleteById(q.getQuestionId());

        assertFalse(questionAcceptedAnswerRepository.existsById(answerId),
                "Accepted answer must be cascade deleted when question is deleted in PostgreSQL");
    }
}
