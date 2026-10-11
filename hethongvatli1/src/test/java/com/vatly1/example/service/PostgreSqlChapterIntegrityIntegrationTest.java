package com.vatly1.example.service;

import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.Chapter;
import com.vatly1.example.entity.Subject;
import com.vatly1.example.entity.Topic;
import com.vatly1.example.repository.IChapterRepository;
import com.vatly1.example.repository.ISubjectRepository;
import com.vatly1.example.repository.QuestionBankRepository;
import com.vatly1.example.repository.TopicRepository;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.model.dto.ChapterDTO;
import com.vatly1.example.model.dto.TopicDTO;
import com.vatly1.example.model.request.AssignChapterDTO;
import com.vatly1.example.model.request.CreateChapterDTO;
import com.vatly1.example.model.request.ReorderTopicsDTO;
import com.vatly1.example.model.request.UpdateChapterDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@ActiveProfiles("pgtest")
@org.springframework.test.context.ContextConfiguration(initializers = PostgreSqlTestContextInitializer.class)
@EnabledIfEnvironmentVariable(named = "RUN_PG_INTEGRATION_TESTS", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PostgreSqlChapterIntegrityIntegrationTest {

    @Autowired
    private IChapterRepository chapterRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private ISubjectRepository subjectRepository;

    @Autowired
    private QuestionBankRepository questionBankRepository;

    @Autowired
    private IChapterService chapterService;

    @Autowired
    private ITopicService topicService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Subject subjectA;
    private Subject subjectB;

    private final List<UUID> createdTopicIds = new ArrayList<>();
    private final List<UUID> createdChapterIds = new ArrayList<>();
    private final List<UUID> createdSubjectIds = new ArrayList<>();

    @BeforeAll
    void verifySchema() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name IN ('chapters', 'topics', 'subjects')",
                Integer.class
        );
        assertEquals(3, tableCount, "Tables chapters, topics, subjects must exist in public schema");

        Integer fkChapterSubject = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = 'public' AND constraint_name = 'fk_chapters_subject'",
                Integer.class
        );
        assertEquals(1, fkChapterSubject, "Constraint fk_chapters_subject must exist");

        Integer uqSubjectCode = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = 'public' AND constraint_name = 'uq_chapters_subject_code'",
                Integer.class
        );
        assertEquals(1, uqSubjectCode, "Constraint uq_chapters_subject_code must exist");

        Integer uqIdSubject = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = 'public' AND constraint_name = 'uq_chapters_id_subject'",
                Integer.class
        );
        assertEquals(1, uqIdSubject, "Constraint uq_chapters_id_subject must exist");

        Integer fkTopicChapterSubject = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = 'public' AND constraint_name = 'fk_topics_chapter_subject'",
                Integer.class
        );
        assertEquals(1, fkTopicChapterSubject, "Constraint fk_topics_chapter_subject must exist");

        Integer topicChapterCol = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'topics' AND column_name = 'chapter_id'",
                Integer.class
        );
        assertEquals(1, topicChapterCol, "Column topics.chapter_id must exist");
    }

    @BeforeEach
    void setUp() {
        String testSuffix = UUID.randomUUID().toString().substring(0, 8);

        subjectA = subjectRepository.save(Subject.builder()
                .subjectCode("PHYS_" + testSuffix)
                .subjectName("Vat ly Dai cuong " + testSuffix)
                .isActive(true)
                .build());
        createdSubjectIds.add(subjectA.getSubjectId());

        subjectB = subjectRepository.save(Subject.builder()
                .subjectCode("MATH_" + testSuffix)
                .subjectName("Giai tich " + testSuffix)
                .isActive(true)
                .build());
        createdSubjectIds.add(subjectB.getSubjectId());
    }

    @AfterEach
    void cleanUp() {
        for (UUID topicId : createdTopicIds) {
            try {
                topicRepository.deleteById(topicId);
            } catch (Exception ignored) {
            }
        }
        createdTopicIds.clear();

        for (UUID chapterId : createdChapterIds) {
            try {
                chapterRepository.deleteById(chapterId);
            } catch (Exception ignored) {
            }
        }
        createdChapterIds.clear();

        for (UUID subjectId : createdSubjectIds) {
            try {
                subjectRepository.deleteById(subjectId);
            } catch (Exception ignored) {
            }
        }
        createdSubjectIds.clear();
    }

    @Test
    @DisplayName("1. Chapter creation and Hibernate Entity mapping")
    void testChapterCreationAndHibernateMapping() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        Chapter chapter = Chapter.builder()
                .subjectId(subjectA.getSubjectId())
                .chapterCode("CH01_" + suffix)
                .chapterName("Chương 1: Động học chất điểm " + suffix)
                .description("Mô tả chương 1")
                .orderIndex(1)
                .isActive(true)
                .build();

        Chapter saved = chapterRepository.save(chapter);
        assertNotNull(saved.getChapterId(), "chapterId should be generated by JPA UUID strategy");
        createdChapterIds.add(saved.getChapterId());

        assertNotNull(saved.getCreatedAt(), "createdAt should be populated by @CreationTimestamp");
        assertNotNull(saved.getUpdatedAt(), "updatedAt should be populated by @UpdateTimestamp");

        Optional<Chapter> retrieved = chapterRepository.findById(saved.getChapterId());
        assertTrue(retrieved.isPresent());
        assertEquals("CH01_" + suffix, retrieved.get().getChapterCode());
        assertEquals("Chương 1: Động học chất điểm " + suffix, retrieved.get().getChapterName());
        assertEquals(subjectA.getSubjectId(), retrieved.get().getSubjectId());
        assertEquals(1, retrieved.get().getOrderIndex());
        assertTrue(retrieved.get().getIsActive());
    }

    @Test
    @DisplayName("2. Unique chapterCode theo Subject (uq_chapters_subject_code)")
    void testUniqueChapterCodePerSubject() {
        String code = "CH_UNIQ_" + UUID.randomUUID().toString().substring(0, 6);

        Chapter ch1 = chapterRepository.save(Chapter.builder()
                .subjectId(subjectA.getSubjectId())
                .chapterCode(code)
                .chapterName("Chương 1 Subject A")
                .orderIndex(1)
                .isActive(true)
                .build());
        createdChapterIds.add(ch1.getChapterId());

        // Cố tình tạo trùng chapterCode trong cùng môn subjectA -> phải bắn lỗi
        assertThrows(DataIntegrityViolationException.class, () -> {
            chapterRepository.saveAndFlush(Chapter.builder()
                    .subjectId(subjectA.getSubjectId())
                    .chapterCode(code)
                    .chapterName("Chương 1 trùng lặp Subject A")
                    .orderIndex(2)
                    .isActive(true)
                    .build());
        }, "Duplicate chapterCode in same subject must violate uq_chapters_subject_code");

        // Tạo cùng chapterCode nhưng ở môn khác (subjectB) -> phải thành công
        Chapter chOtherSubject = chapterRepository.save(Chapter.builder()
                .subjectId(subjectB.getSubjectId())
                .chapterCode(code)
                .chapterName("Chương 1 Subject B (Khác môn)")
                .orderIndex(1)
                .isActive(true)
                .build());
        assertNotNull(chOtherSubject.getChapterId());
        createdChapterIds.add(chOtherSubject.getChapterId());
    }

    @Test
    @DisplayName("3. Composite FK (chapter_id, subject_id) chống Topic khác môn")
    void testCompositeFkCrossSubjectSafety() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        Chapter chA = chapterRepository.save(Chapter.builder()
                .subjectId(subjectA.getSubjectId())
                .chapterCode("CH_A_" + suffix)
                .chapterName("Chương A thuộc môn Lý")
                .orderIndex(1)
                .isActive(true)
                .build());
        createdChapterIds.add(chA.getChapterId());

        // Topic thuộc môn Toán (subjectB), nhưng lại tham chiếu chapterId của môn Lý (chA)
        Topic crossSubjectTopic = Topic.builder()
                .subjectId(subjectB.getSubjectId())
                .chapterId(chA.getChapterId()) // Mismatched subject_id!
                .topicName("Bài học chéo môn bất hợp lệ " + suffix)
                .orderIndex(1)
                .description("Test cross-subject FK restriction")
                .build();

        // Composite FK (chapter_id, subject_id) -> chapters(chapter_id, subject_id) phải từ chối
        assertThrows(DataIntegrityViolationException.class, () -> {
            topicRepository.saveAndFlush(crossSubjectTopic);
        }, "Composite foreign key fk_topics_chapter_subject must reject topic referencing chapter from different subject");
    }

    @Test
    @DisplayName("4. Chapter deletion restriction (ON DELETE RESTRICT)")
    void testChapterDeletionRestriction() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        Chapter ch = chapterRepository.save(Chapter.builder()
                .subjectId(subjectA.getSubjectId())
                .chapterCode("CH_DEL_" + suffix)
                .chapterName("Chương kiểm tra xoá " + suffix)
                .orderIndex(1)
                .isActive(true)
                .build());
        createdChapterIds.add(ch.getChapterId());

        Topic topic = topicRepository.save(Topic.builder()
                .subjectId(subjectA.getSubjectId())
                .chapterId(ch.getChapterId())
                .topicName("Bài học trong chương " + suffix)
                .orderIndex(1)
                .build());
        createdTopicIds.add(topic.getTopicId());

        // Khi có Topic đang tham chiếu, xoá Chapter phải bị RESTRICT chặn lại
        assertThrows(DataIntegrityViolationException.class, () -> {
            chapterRepository.delete(ch);
            chapterRepository.flush();
        }, "Deleting chapter with referencing topics must fail due to ON DELETE RESTRICT");

        // Sau khi huỷ liên kết (chapter_id = null)
        topic.setChapterId(null);
        topicRepository.saveAndFlush(topic);

        // Giờ xoá Chapter phải thành công
        assertDoesNotThrow(() -> {
            chapterRepository.delete(ch);
            chapterRepository.flush();
        });
        createdChapterIds.remove(ch.getChapterId());
    }

    @Test
    @DisplayName("5. Topic có chapter_id NULL hoàn toàn hợp lệ (backward compatible)")
    void testTopicWithChapterIdNull() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        Topic standaloneTopic = topicRepository.save(Topic.builder()
                .subjectId(subjectA.getSubjectId())
                .chapterId(null)
                .topicName("Bài học không gán chương " + suffix)
                .orderIndex(1)
                .build());
        createdTopicIds.add(standaloneTopic.getTopicId());

        assertNotNull(standaloneTopic.getTopicId());
        assertNull(standaloneTopic.getChapterId());

        List<Topic> nullChapterTopics = topicRepository.findBySubjectIdAndChapterIdIsNullOrderByOrderIndexAsc(subjectA.getSubjectId());
        assertFalse(nullChapterTopics.isEmpty());
        assertTrue(nullChapterTopics.stream().anyMatch(t -> t.getTopicId().equals(standaloneTopic.getTopicId())));
    }

    @Test
    @DisplayName("6. Bảo toàn dữ liệu lịch sử (1 topic 'qưer' và 8 câu hỏi question_bank)")
    void testPreserveHistoricalData() {
        UUID baselineTopicId = UUID.fromString("118332e1-36a0-4d70-b47f-fefeaae722a6");
        Optional<Topic> baselineTopic = topicRepository.findById(baselineTopicId);
        assertTrue(baselineTopic.isPresent(), "Baseline topic 118332e1-36a0-4d70-b47f-fefeaae722a6 must exist");
        assertEquals("qưer", baselineTopic.get().getTopicName());
        assertNull(baselineTopic.get().getChapterId(), "Baseline historical topic must retain chapter_id NULL");

        long qbCount = questionBankRepository.count();
        assertTrue(qbCount >= 8, "Historical question bank must retain at least 8 questions, found: " + qbCount);
    }

    @Test
    @DisplayName("7. ChapterService CRUD and status lifecycle on Neon PostgreSQL")
    void testChapterServiceLifecycleOnNeon() {
        String code = "SVC_CH_" + UUID.randomUUID().toString().substring(0, 6);
        CreateChapterDTO createDto = CreateChapterDTO.builder()
                .subjectId(subjectA.getSubjectId())
                .chapterCode(code)
                .chapterName("Chương Service Test")
                .description("Mô tả từ Service")
                .orderIndex(1)
                .isActive(true)
                .build();

        ChapterDTO created = chapterService.createChapter(subjectA.getSubjectId(), createDto);
        assertNotNull(created.getChapterId());
        createdChapterIds.add(created.getChapterId());
        assertEquals(code, created.getChapterCode());
        assertEquals(0L, created.getTopicCount());

        // Update
        UpdateChapterDTO updateDto = UpdateChapterDTO.builder()
                .chapterName("Chương Service Đã Đổi Tên")
                .orderIndex(2)
                .build();
        ChapterDTO updated = chapterService.updateChapter(created.getChapterId(), updateDto);
        assertEquals("Chương Service Đã Đổi Tên", updated.getChapterName());
        assertEquals(2, updated.getOrderIndex());

        // Toggle Status
        ChapterDTO deactivated = chapterService.updateChapterStatus(created.getChapterId(), false);
        assertFalse(deactivated.getIsActive());

        // Delete empty chapter
        assertDoesNotThrow(() -> chapterService.deleteChapter(created.getChapterId()));
        createdChapterIds.remove(created.getChapterId());
        assertTrue(chapterRepository.findById(created.getChapterId()).isEmpty());
    }

    @Test
    @DisplayName("8. TopicService assign, unassign and reorder on Neon PostgreSQL")
    void testTopicServiceAssignAndReorderOnNeon() {
        String code = "SVC_TOPIC_" + UUID.randomUUID().toString().substring(0, 6);
        Chapter ch = chapterRepository.save(Chapter.builder()
                .subjectId(subjectA.getSubjectId())
                .chapterCode(code)
                .chapterName("Chương Gán Topic Test")
                .orderIndex(1)
                .isActive(true)
                .build());
        createdChapterIds.add(ch.getChapterId());

        Topic t1 = topicRepository.save(Topic.builder()
                .subjectId(subjectA.getSubjectId())
                .topicName("Topic 1 " + code)
                .orderIndex(1)
                .build());
        createdTopicIds.add(t1.getTopicId());

        Topic t2 = topicRepository.save(Topic.builder()
                .subjectId(subjectA.getSubjectId())
                .topicName("Topic 2 " + code)
                .orderIndex(2)
                .build());
        createdTopicIds.add(t2.getTopicId());

        // Gán vào chapter qua topicService
        TopicDTO assigned1 = topicService.assignChapter(t1.getTopicId(), ch.getChapterId());
        assertEquals(ch.getChapterId(), assigned1.getChapterId());

        TopicDTO assigned2 = topicService.assignChapter(t2.getTopicId(), ch.getChapterId());
        assertEquals(ch.getChapterId(), assigned2.getChapterId());

        // Reorder [t2, t1]
        ReorderTopicsDTO reorderDto = ReorderTopicsDTO.builder()
                .topicIds(List.of(t2.getTopicId(), t1.getTopicId()))
                .build();
        List<TopicDTO> reordered = chapterService.reorderTopics(ch.getChapterId(), reorderDto);
        assertEquals(2, reordered.size());
        assertEquals(t2.getTopicId(), reordered.get(0).getTopicId());
        assertEquals(1, reordered.get(0).getOrderIndex());
        assertEquals(t1.getTopicId(), reordered.get(1).getTopicId());
        assertEquals(2, reordered.get(1).getOrderIndex());

        // Gỡ chapter
        TopicDTO unassigned = topicService.assignChapter(t1.getTopicId(), null);
        assertNull(unassigned.getChapterId());
    }

    @Test
    @DisplayName("9. Reorder transaction rollback on invalid topic on Neon PostgreSQL")
    void testReorderRollbackOnInvalidTopicOnNeon() {
        String code = "SVC_ROLL_" + UUID.randomUUID().toString().substring(0, 6);
        Chapter ch = chapterRepository.save(Chapter.builder()
                .subjectId(subjectA.getSubjectId())
                .chapterCode(code)
                .chapterName("Chương Rollback Test")
                .orderIndex(1)
                .isActive(true)
                .build());
        createdChapterIds.add(ch.getChapterId());

        Topic t1 = topicRepository.save(Topic.builder()
                .subjectId(subjectA.getSubjectId())
                .chapterId(ch.getChapterId())
                .topicName("Topic Roll 1")
                .orderIndex(1)
                .build());
        createdTopicIds.add(t1.getTopicId());

        Topic t2 = topicRepository.save(Topic.builder()
                .subjectId(subjectA.getSubjectId())
                .chapterId(ch.getChapterId())
                .topicName("Topic Roll 2")
                .orderIndex(2)
                .build());
        createdTopicIds.add(t2.getTopicId());

        // Reorder có ID không tồn tại
        UUID nonExistent = UUID.randomUUID();
        ReorderTopicsDTO invalidDto = ReorderTopicsDTO.builder()
                .topicIds(List.of(t2.getTopicId(), nonExistent))
                .build();

        assertThrows(CustomException.class, () -> chapterService.reorderTopics(ch.getChapterId(), invalidDto));

        // Kiểm tra orderIndex ban đầu không bị thay đổi (Transactional rollback)
        Topic checkT1 = topicRepository.findById(t1.getTopicId()).orElseThrow();
        Topic checkT2 = topicRepository.findById(t2.getTopicId()).orElseThrow();
        assertEquals(1, checkT1.getOrderIndex());
        assertEquals(2, checkT2.getOrderIndex());
    }
}
