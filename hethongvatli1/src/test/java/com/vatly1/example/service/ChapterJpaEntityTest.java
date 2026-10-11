package com.vatly1.example.service;

import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.Chapter;
import com.vatly1.example.entity.Subject;
import com.vatly1.example.entity.Topic;
import com.vatly1.example.model.dto.ChapterDTO;
import com.vatly1.example.model.dto.TopicDTO;
import com.vatly1.example.model.request.CreateChapterDTO;
import com.vatly1.example.model.request.CreateTopicDTO;
import com.vatly1.example.model.request.UpdateChapterDTO;
import com.vatly1.example.repository.IChapterRepository;
import com.vatly1.example.repository.ISubjectRepository;
import com.vatly1.example.repository.TopicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@ActiveProfiles("test")
@Transactional
public class ChapterJpaEntityTest {

    @Autowired
    private IChapterRepository chapterRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private ISubjectRepository subjectRepository;

    private Subject testSubject;

    @BeforeEach
    void setUp() {
        testSubject = subjectRepository.save(Subject.builder()
                .subjectCode("PHYS_JPA_" + UUID.randomUUID().toString().substring(0, 6))
                .subjectName("Vat ly JPA Test")
                .isActive(true)
                .build());
    }

    @Test
    @DisplayName("Chapter JPA entity creation and repository queries")
    void testChapterRepositoryQueries() {
        Chapter c1 = chapterRepository.save(Chapter.builder()
                .subjectId(testSubject.getSubjectId())
                .chapterCode("CH01")
                .chapterName("Chương 1")
                .orderIndex(1)
                .isActive(true)
                .build());

        Chapter c2 = chapterRepository.save(Chapter.builder()
                .subjectId(testSubject.getSubjectId())
                .chapterCode("CH02")
                .chapterName("Chương 2")
                .orderIndex(2)
                .isActive(false)
                .build());

        assertNotNull(c1.getChapterId());
        assertNotNull(c2.getChapterId());

        List<Chapter> allBySubject = chapterRepository.findBySubjectIdOrderByOrderIndexAsc(testSubject.getSubjectId());
        assertEquals(2, allBySubject.size());
        assertEquals("CH01", allBySubject.get(0).getChapterCode());
        assertEquals("CH02", allBySubject.get(1).getChapterCode());

        List<Chapter> activeBySubject = chapterRepository.findBySubjectIdAndIsActiveTrueOrderByOrderIndexAsc(testSubject.getSubjectId());
        assertEquals(1, activeBySubject.size());
        assertEquals("CH01", activeBySubject.get(0).getChapterCode());

        Optional<Chapter> foundCode = chapterRepository.findBySubjectIdAndChapterCode(testSubject.getSubjectId(), "CH01");
        assertTrue(foundCode.isPresent());

        assertTrue(chapterRepository.existsBySubjectIdAndChapterCode(testSubject.getSubjectId(), "CH01"));
        assertFalse(chapterRepository.existsBySubjectIdAndChapterCode(testSubject.getSubjectId(), "CH99"));
        assertTrue(chapterRepository.existsBySubjectId(testSubject.getSubjectId()));
    }

    @Test
    @DisplayName("Topic repository chapter-related queries")
    void testTopicRepositoryChapterQueries() {
        Chapter ch = chapterRepository.save(Chapter.builder()
                .subjectId(testSubject.getSubjectId())
                .chapterCode("CH_TOPIC")
                .chapterName("Chương chứa Topic")
                .orderIndex(1)
                .isActive(true)
                .build());

        Topic tWithChapter = topicRepository.save(Topic.builder()
                .subjectId(testSubject.getSubjectId())
                .chapterId(ch.getChapterId())
                .topicName("Bài học 1")
                .orderIndex(1)
                .build());

        Topic tWithoutChapter = topicRepository.save(Topic.builder()
                .subjectId(testSubject.getSubjectId())
                .chapterId(null)
                .topicName("Bài học 2 (Không chương)")
                .orderIndex(2)
                .build());

        List<Topic> inChapter = topicRepository.findByChapterIdOrderByOrderIndexAsc(ch.getChapterId());
        assertEquals(1, inChapter.size());
        assertEquals(tWithChapter.getTopicId(), inChapter.get(0).getTopicId());

        List<Topic> nullChapter = topicRepository.findBySubjectIdAndChapterIdIsNullOrderByOrderIndexAsc(testSubject.getSubjectId());
        assertFalse(nullChapter.isEmpty());
        assertTrue(nullChapter.stream().anyMatch(t -> t.getTopicId().equals(tWithoutChapter.getTopicId())));

        assertEquals(1, topicRepository.countByChapterId(ch.getChapterId()));
        assertTrue(topicRepository.existsByChapterId(ch.getChapterId()));
        assertFalse(topicRepository.existsByChapterId(UUID.randomUUID()));
    }

    @Test
    @DisplayName("ChapterDTO and TopicDTO builders and fields")
    void testDtoBuilders() {
        UUID chapterId = UUID.randomUUID();
        UUID subjectId = testSubject.getSubjectId();

        ChapterDTO chapterDTO = ChapterDTO.builder()
                .chapterId(chapterId)
                .subjectId(subjectId)
                .chapterCode("CH_TEST")
                .chapterName("Chương Test DTO")
                .description("Mô tả")
                .orderIndex(1)
                .isActive(true)
                .build();

        assertEquals(chapterId, chapterDTO.getChapterId());
        assertEquals("CH_TEST", chapterDTO.getChapterCode());

        CreateChapterDTO createDto = CreateChapterDTO.builder()
                .subjectId(subjectId)
                .chapterCode("CH_CREATE")
                .chapterName("Tạo mới")
                .orderIndex(1)
                .isActive(true)
                .build();
        assertEquals("CH_CREATE", createDto.getChapterCode());

        UpdateChapterDTO updateDto = UpdateChapterDTO.builder()
                .chapterCode("CH_UPDATE")
                .chapterName("Cập nhật")
                .orderIndex(2)
                .isActive(false)
                .build();
        assertEquals("CH_UPDATE", updateDto.getChapterCode());

        TopicDTO topicDTO = TopicDTO.builder()
                .topicId(UUID.randomUUID())
                .subjectId(subjectId)
                .chapterId(chapterId)
                .topicName("Topic DTO Test")
                .orderIndex(1)
                .build();
        assertEquals(chapterId, topicDTO.getChapterId());

        CreateTopicDTO createTopicDTO = CreateTopicDTO.builder()
                .subjectId(subjectId)
                .chapterId(chapterId)
                .topicName("Create Topic DTO Test")
                .build();
        assertEquals(chapterId, createTopicDTO.getChapterId());
    }
}
