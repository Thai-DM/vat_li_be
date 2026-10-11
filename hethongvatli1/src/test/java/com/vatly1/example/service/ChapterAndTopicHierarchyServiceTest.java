package com.vatly1.example.service;

import com.vatly1.example.app.JwtAuthServiceApp;
import com.vatly1.example.entity.Class;
import com.vatly1.example.entity.Chapter;
import com.vatly1.example.entity.Subject;
import com.vatly1.example.entity.Topic;
import com.vatly1.example.entity.User;
import com.vatly1.example.entity.enums.ClassStatus;
import com.vatly1.example.entity.enums.UserRole;
import com.vatly1.example.entity.enums.UserStatus;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.model.dto.ChapterDTO;
import com.vatly1.example.model.dto.TopicDTO;
import com.vatly1.example.model.request.CreateChapterDTO;
import com.vatly1.example.model.request.CreateTopicDTO;
import com.vatly1.example.model.request.ReorderTopicsDTO;
import com.vatly1.example.model.request.UpdateChapterDTO;
import com.vatly1.example.repository.IChapterRepository;
import com.vatly1.example.repository.IClassRepository;
import com.vatly1.example.repository.ISubjectRepository;
import com.vatly1.example.repository.IUserRepository;
import com.vatly1.example.repository.TopicRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = JwtAuthServiceApp.class)
@ActiveProfiles("test")
public class ChapterAndTopicHierarchyServiceTest {

    @Autowired
    private IChapterService chapterService;

    @Autowired
    private ITopicService topicService;

    @Autowired
    private IChapterRepository chapterRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private ISubjectRepository subjectRepository;

    @Autowired
    private IClassRepository classRepository;

    @Autowired
    private IUserRepository userRepository;

    private Subject subjectPhys;
    private Subject subjectMath;
    private User adminUser;
    private User instructorAssigned;
    private User instructorUnassigned;
    private User studentUser;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        subjectPhys = subjectRepository.save(Subject.builder()
                .subjectCode("PHYS_" + suffix)
                .subjectName("Vat ly Dai cuong " + suffix)
                .isActive(true)
                .build());

        subjectMath = subjectRepository.save(Subject.builder()
                .subjectCode("MATH_" + suffix)
                .subjectName("Giai tich " + suffix)
                .isActive(true)
                .build());

        adminUser = userRepository.save(User.builder()
                .username("admin_" + suffix)
                .email("admin_" + suffix + "@test.edu.vn")
                .passwordHash("password")
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .build());

        instructorAssigned = userRepository.save(User.builder()
                .username("inst_assigned_" + suffix)
                .email("inst_assigned_" + suffix + "@test.edu.vn")
                .passwordHash("password")
                .role(UserRole.INSTRUCTOR)
                .status(UserStatus.ACTIVE)
                .build());

        instructorUnassigned = userRepository.save(User.builder()
                .username("inst_unassigned_" + suffix)
                .email("inst_unassigned_" + suffix + "@test.edu.vn")
                .passwordHash("password")
                .role(UserRole.INSTRUCTOR)
                .status(UserStatus.ACTIVE)
                .build());

        studentUser = userRepository.save(User.builder()
                .username("sv_" + suffix)
                .email("sv_" + suffix + "@test.edu.vn")
                .passwordHash("password")
                .role(UserRole.STUDENT)
                .status(UserStatus.ACTIVE)
                .build());

        // Phân công instructorAssigned vào subjectPhys thông qua một Class
        classRepository.save(Class.builder()
                .classCode("CLASS_" + suffix)
                .subjectId(subjectPhys.getSubjectId())
                .instructorId(instructorAssigned.getUserId())
                .status(ClassStatus.ACTIVE)
                .build());

        // Default login as admin
        authenticateAs(adminUser, "ROLE_ADMIN");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(User user, String role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                user.getUsername(),
                null,
                Collections.singletonList(new SimpleGrantedAuthority(role))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("1. Admin tạo Chapter thành công")
    void testAdminCreateChapterSuccess() {
        CreateChapterDTO dto = CreateChapterDTO.builder()
                .subjectId(subjectPhys.getSubjectId())
                .chapterCode("CH01")
                .chapterName("Chương 1: Động học chất điểm")
                .description("Mô tả chương 1")
                .orderIndex(1)
                .isActive(true)
                .build();

        ChapterDTO created = chapterService.createChapter(subjectPhys.getSubjectId(), dto);
        assertNotNull(created.getChapterId());
        assertEquals("CH01", created.getChapterCode());
        assertEquals(subjectPhys.getSubjectId(), created.getSubjectId());
        assertEquals(1, created.getOrderIndex());
        assertTrue(created.getIsActive());
    }

    @Test
    @DisplayName("2. Không tạo được Chapter khi Subject không tồn tại")
    void testCreateChapterSubjectNotFound() {
        CreateChapterDTO dto = CreateChapterDTO.builder()
                .subjectId(UUID.randomUUID())
                .chapterCode("CH_FAIL")
                .chapterName("Tên chương")
                .build();

        CustomException ex = assertThrows(CustomException.class, () ->
                chapterService.createChapter(dto.getSubjectId(), dto));
        assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
    }

    @Test
    @DisplayName("3. Không tạo Chapter trùng mã trong cùng Subject")
    void testCreateChapterDuplicateCode() {
        CreateChapterDTO dto1 = CreateChapterDTO.builder()
                .subjectId(subjectPhys.getSubjectId())
                .chapterCode("CH_DUP")
                .chapterName("Chương A")
                .build();
        chapterService.createChapter(subjectPhys.getSubjectId(), dto1);

        CreateChapterDTO dto2 = CreateChapterDTO.builder()
                .subjectId(subjectPhys.getSubjectId())
                .chapterCode("CH_DUP")
                .chapterName("Chương B")
                .build();
        CustomException ex = assertThrows(CustomException.class, () ->
                chapterService.createChapter(subjectPhys.getSubjectId(), dto2));
        assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
    }

    @Test
    @DisplayName("4. Instructor quản lý Chapter thuộc Subject được phân công")
    void testInstructorManagesAssignedSubject() {
        authenticateAs(instructorAssigned, "ROLE_INSTRUCTOR");

        CreateChapterDTO dto = CreateChapterDTO.builder()
                .subjectId(subjectPhys.getSubjectId())
                .chapterCode("CH_INST")
                .chapterName("Chương do Giảng viên tạo")
                .build();

        ChapterDTO created = chapterService.createChapter(subjectPhys.getSubjectId(), dto);
        assertNotNull(created.getChapterId());

        UpdateChapterDTO updateDTO = UpdateChapterDTO.builder()
                .chapterName("Chương đã cập nhật bởi Giảng viên")
                .build();
        ChapterDTO updated = chapterService.updateChapter(created.getChapterId(), updateDTO);
        assertEquals("Chương đã cập nhật bởi Giảng viên", updated.getChapterName());
    }

    @Test
    @DisplayName("5. Instructor bị chặn khi sửa Chapter của Subject khác")
    void testInstructorBlockedOnUnassignedSubject() {
        // Giảng viên unassigned cố gắng tạo hoặc sửa chương trên môn Toán (subjectMath)
        authenticateAs(instructorUnassigned, "ROLE_INSTRUCTOR");

        CreateChapterDTO dto = CreateChapterDTO.builder()
                .subjectId(subjectMath.getSubjectId())
                .chapterCode("CH_BLOCKED")
                .chapterName("Chương bị chặn")
                .build();

        CustomException ex = assertThrows(CustomException.class, () ->
                chapterService.createChapter(subjectMath.getSubjectId(), dto));
        assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
    }

    @Test
    @DisplayName("6. Student bị chặn khi gọi API ghi")
    void testStudentBlockedFromWriteOperations() {
        authenticateAs(studentUser, "ROLE_STUDENT");

        CreateChapterDTO dto = CreateChapterDTO.builder()
                .subjectId(subjectPhys.getSubjectId())
                .chapterCode("CH_SV")
                .chapterName("Chương của SV")
                .build();

        CustomException ex = assertThrows(CustomException.class, () ->
                chapterService.createChapter(subjectPhys.getSubjectId(), dto));
        assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
    }

    @Test
    @DisplayName("7. Lấy danh sách Chapter đúng thứ tự orderIndex")
    void testGetChaptersInOrderIndexAsc() {
        chapterService.createChapter(subjectPhys.getSubjectId(), CreateChapterDTO.builder()
                .subjectId(subjectPhys.getSubjectId())
                .chapterCode("CH02")
                .chapterName("Chương 2")
                .orderIndex(2)
                .build());

        chapterService.createChapter(subjectPhys.getSubjectId(), CreateChapterDTO.builder()
                .subjectId(subjectPhys.getSubjectId())
                .chapterCode("CH01")
                .chapterName("Chương 1")
                .orderIndex(1)
                .build());

        List<ChapterDTO> list = chapterService.getChaptersBySubject(subjectPhys.getSubjectId(), false);
        assertEquals(2, list.size());
        assertEquals("CH01", list.get(0).getChapterCode());
        assertEquals("CH02", list.get(1).getChapterCode());
    }

    @Test
    @DisplayName("8. Tạo Topic có chapterId hợp lệ")
    void testCreateTopicWithValidChapter() {
        ChapterDTO ch = chapterService.createChapter(subjectPhys.getSubjectId(), CreateChapterDTO.builder()
                .subjectId(subjectPhys.getSubjectId())
                .chapterCode("CH_TOPIC")
                .chapterName("Chương cho Topic")
                .build());

        CreateTopicDTO topicDto = CreateTopicDTO.builder()
                .subjectId(subjectPhys.getSubjectId())
                .chapterId(ch.getChapterId())
                .topicName("Bài 1: Chuyển động thẳng biến đổi đều")
                .orderIndex(1)
                .build();

        TopicDTO createdTopic = topicService.createTopic(topicDto);
        assertNotNull(createdTopic.getTopicId());
        assertEquals(ch.getChapterId(), createdTopic.getChapterId());
    }

    @Test
    @DisplayName("9. Tạo Topic không có chapterId (chapterId = NULL)")
    void testCreateTopicWithoutChapterId() {
        CreateTopicDTO topicDto = CreateTopicDTO.builder()
                .subjectId(subjectPhys.getSubjectId())
                .chapterId(null)
                .topicName("Bài học độc lập chưa phân chương")
                .orderIndex(1)
                .build();

        TopicDTO createdTopic = topicService.createTopic(topicDto);
        assertNotNull(createdTopic.getTopicId());
        assertNull(createdTopic.getChapterId());

        List<TopicDTO> unassigned = topicService.getUnassignedTopicsBySubject(subjectPhys.getSubjectId());
        assertTrue(unassigned.stream().anyMatch(t -> t.getTopicId().equals(createdTopic.getTopicId())));
    }

    @Test
    @DisplayName("10. Chặn gán Topic khác Subject")
    void testBlockTopicAssignmentAcrossDifferentSubject() {
        // Tạo Chapter ở môn Toán
        ChapterDTO chMath = chapterService.createChapter(subjectMath.getSubjectId(), CreateChapterDTO.builder()
                .subjectId(subjectMath.getSubjectId())
                .chapterCode("CH_MATH")
                .chapterName("Chương Toán")
                .build());

        // Cố tình tạo Topic thuộc môn Lý nhưng gán chapter của môn Toán
        CreateTopicDTO topicDto = CreateTopicDTO.builder()
                .subjectId(subjectPhys.getSubjectId())
                .chapterId(chMath.getChapterId())
                .topicName("Bài học Lý nhưng trỏ nhầm Chương Toán")
                .build();

        CustomException ex = assertThrows(CustomException.class, () ->
                topicService.createTopic(topicDto));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test
    @DisplayName("11. Chuyển Topic giữa hai Chapter cùng Subject")
    void testTransferTopicBetweenChaptersSameSubject() {
        ChapterDTO ch1 = chapterService.createChapter(subjectPhys.getSubjectId(), CreateChapterDTO.builder()
                .subjectId(subjectPhys.getSubjectId())
                .chapterCode("CH_T1")
                .chapterName("Chương 1")
                .build());

        ChapterDTO ch2 = chapterService.createChapter(subjectPhys.getSubjectId(), CreateChapterDTO.builder()
                .subjectId(subjectPhys.getSubjectId())
                .chapterCode("CH_T2")
                .chapterName("Chương 2")
                .build());

        TopicDTO topic = topicService.createTopic(CreateTopicDTO.builder()
                .subjectId(subjectPhys.getSubjectId())
                .chapterId(ch1.getChapterId())
                .topicName("Bài học chuyển chương")
                .build());

        TopicDTO reassigned = topicService.assignChapter(topic.getTopicId(), ch2.getChapterId());
        assertEquals(ch2.getChapterId(), reassigned.getChapterId());
    }

    @Test
    @DisplayName("12. Gỡ Topic khỏi Chapter thành công (chapterId = NULL)")
    void testUnassignTopicFromChapter() {
        ChapterDTO ch = chapterService.createChapter(subjectPhys.getSubjectId(), CreateChapterDTO.builder()
                .subjectId(subjectPhys.getSubjectId())
                .chapterCode("CH_UNASSIGN")
                .chapterName("Chương cần gỡ topic")
                .build());

        TopicDTO topic = topicService.createTopic(CreateTopicDTO.builder()
                .subjectId(subjectPhys.getSubjectId())
                .chapterId(ch.getChapterId())
                .topicName("Bài học cần gỡ")
                .build());

        TopicDTO unassigned = topicService.assignChapter(topic.getTopicId(), null);
        assertNull(unassigned.getChapterId());
    }

    @Test
    @DisplayName("13. Request cập nhật Topic cũ không truyền chapterId vẫn giữ nguyên Chapter")
    void testLegacyUpdateTopicPreservesExistingChapter() {
        ChapterDTO ch = chapterService.createChapter(subjectPhys.getSubjectId(), CreateChapterDTO.builder()
                .subjectId(subjectPhys.getSubjectId())
                .chapterCode("CH_LEGACY")
                .chapterName("Chương bảo tồn")
                .build());

        TopicDTO topic = topicService.createTopic(CreateTopicDTO.builder()
                .subjectId(subjectPhys.getSubjectId())
                .chapterId(ch.getChapterId())
                .topicName("Tên ban đầu")
                .build());

        // Giả lập request PUT từ Frontend cũ không chứa chapterId (chapterId = null)
        CreateTopicDTO legacyUpdateDTO = CreateTopicDTO.builder()
                .subjectId(subjectPhys.getSubjectId())
                .chapterId(null)
                .topicName("Tên đã cập nhật mới")
                .orderIndex(5)
                .build();

        TopicDTO updated = topicService.updateTopic(topic.getTopicId(), legacyUpdateDTO);
        assertEquals("Tên đã cập nhật mới", updated.getTopicName());
        assertEquals(ch.getChapterId(), updated.getChapterId(), "Legacy PUT request must NOT wipe out existing chapterId");
    }

    @Test
    @DisplayName("14. Không xóa được Chapter đang chứa Topic")
    void testCannotDeleteChapterContainingTopics() {
        ChapterDTO ch = chapterService.createChapter(subjectPhys.getSubjectId(), CreateChapterDTO.builder()
                .subjectId(subjectPhys.getSubjectId())
                .chapterCode("CH_HAS_TOPIC")
                .chapterName("Chương có bài học")
                .build());

        topicService.createTopic(CreateTopicDTO.builder()
                .subjectId(subjectPhys.getSubjectId())
                .chapterId(ch.getChapterId())
                .topicName("Bài học con")
                .build());

        CustomException ex = assertThrows(CustomException.class, () ->
                chapterService.deleteChapter(ch.getChapterId()));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test
    @DisplayName("15. Toggle isActive hoạt động đúng và chặn gán bài học vào chương inactive")
    void testToggleChapterActiveStatus() {
        ChapterDTO ch = chapterService.createChapter(subjectPhys.getSubjectId(), CreateChapterDTO.builder()
                .subjectId(subjectPhys.getSubjectId())
                .chapterCode("CH_INACTIVE")
                .chapterName("Chương ngưng hoạt động")
                .isActive(true)
                .build());

        // Đổi sang inactive
        ChapterDTO disabled = chapterService.updateChapterStatus(ch.getChapterId(), false);
        assertFalse(disabled.getIsActive());

        // Cố tình gán topic vào chương inactive
        CreateTopicDTO topicDto = CreateTopicDTO.builder()
                .subjectId(subjectPhys.getSubjectId())
                .chapterId(ch.getChapterId())
                .topicName("Bài học vào chương inactive")
                .build();
        CustomException ex = assertThrows(CustomException.class, () ->
                topicService.createTopic(topicDto));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test
    @DisplayName("16. Reorder Topic trong Chapter thành công")
    void testReorderTopicsWithinChapter() {
        ChapterDTO ch = chapterService.createChapter(subjectPhys.getSubjectId(), CreateChapterDTO.builder()
                .subjectId(subjectPhys.getSubjectId())
                .chapterCode("CH_REORDER")
                .chapterName("Chương reorder")
                .build());

        TopicDTO t1 = topicService.createTopic(CreateTopicDTO.builder()
                .subjectId(subjectPhys.getSubjectId())
                .chapterId(ch.getChapterId())
                .topicName("Bài A")
                .orderIndex(1)
                .build());

        TopicDTO t2 = topicService.createTopic(CreateTopicDTO.builder()
                .subjectId(subjectPhys.getSubjectId())
                .chapterId(ch.getChapterId())
                .topicName("Bài B")
                .orderIndex(2)
                .build());

        TopicDTO t3 = topicService.createTopic(CreateTopicDTO.builder()
                .subjectId(subjectPhys.getSubjectId())
                .chapterId(ch.getChapterId())
                .topicName("Bài C")
                .orderIndex(3)
                .build());

        // Đổi thứ tự thành: t3, t1, t2
        ReorderTopicsDTO reorderDto = ReorderTopicsDTO.builder()
                .topicIds(List.of(t3.getTopicId(), t1.getTopicId(), t2.getTopicId()))
                .build();

        List<TopicDTO> reordered = chapterService.reorderTopics(ch.getChapterId(), reorderDto);
        assertEquals(3, reordered.size());
        assertEquals(t3.getTopicId(), reordered.get(0).getTopicId());
        assertEquals(1, reordered.get(0).getOrderIndex());
        assertEquals(t1.getTopicId(), reordered.get(1).getTopicId());
        assertEquals(2, reordered.get(1).getOrderIndex());
        assertEquals(t2.getTopicId(), reordered.get(2).getTopicId());
        assertEquals(3, reordered.get(2).getOrderIndex());
    }

    @Test
    @DisplayName("17. Reorder có phần tử không hợp lệ phải rollback")
    void testReorderRollbackOnInvalidTopicId() {
        ChapterDTO ch = chapterService.createChapter(subjectPhys.getSubjectId(), CreateChapterDTO.builder()
                .subjectId(subjectPhys.getSubjectId())
                .chapterCode("CH_REORDER_FAIL")
                .chapterName("Chương reorder rollback")
                .build());

        TopicDTO t1 = topicService.createTopic(CreateTopicDTO.builder()
                .subjectId(subjectPhys.getSubjectId())
                .chapterId(ch.getChapterId())
                .topicName("Bài 1")
                .orderIndex(1)
                .build());

        // Gửi ID ngoài chương
        ReorderTopicsDTO invalidDto = ReorderTopicsDTO.builder()
                .topicIds(List.of(t1.getTopicId(), UUID.randomUUID()))
                .build();

        CustomException ex = assertThrows(CustomException.class, () ->
                chapterService.reorderTopics(ch.getChapterId(), invalidDto));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());

        // Kiểm tra orderIndex ban đầu không bị biến dạng
        Topic original = topicRepository.findById(t1.getTopicId()).orElseThrow();
        assertEquals(1, original.getOrderIndex());
    }

    @Test
    @DisplayName("18. Hai request reorder đồng thời không gây deadlock hay lost update")
    void testConcurrentReorderSafety() throws Exception {
        ChapterDTO ch = chapterService.createChapter(subjectPhys.getSubjectId(), CreateChapterDTO.builder()
                .subjectId(subjectPhys.getSubjectId())
                .chapterCode("CH_CONCUR")
                .chapterName("Chương reorder đồng thời")
                .build());

        TopicDTO t1 = topicService.createTopic(CreateTopicDTO.builder()
                .subjectId(subjectPhys.getSubjectId())
                .chapterId(ch.getChapterId())
                .topicName("Bài X")
                .orderIndex(1)
                .build());

        TopicDTO t2 = topicService.createTopic(CreateTopicDTO.builder()
                .subjectId(subjectPhys.getSubjectId())
                .chapterId(ch.getChapterId())
                .topicName("Bài Y")
                .orderIndex(2)
                .build());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<List<TopicDTO>> task1 = () -> {
            authenticateAs(adminUser, "ROLE_ADMIN");
            return chapterService.reorderTopics(ch.getChapterId(), ReorderTopicsDTO.builder()
                    .topicIds(List.of(t1.getTopicId(), t2.getTopicId()))
                    .build());
        };
        Callable<List<TopicDTO>> task2 = () -> {
            authenticateAs(adminUser, "ROLE_ADMIN");
            return chapterService.reorderTopics(ch.getChapterId(), ReorderTopicsDTO.builder()
                    .topicIds(List.of(t2.getTopicId(), t1.getTopicId()))
                    .build());
        };

        Future<List<TopicDTO>> f1 = executor.submit(task1);
        Future<List<TopicDTO>> f2 = executor.submit(task2);

        assertDoesNotThrow(() -> f1.get(5, TimeUnit.SECONDS));
        assertDoesNotThrow(() -> f2.get(5, TimeUnit.SECONDS));
        executor.shutdown();

        List<TopicDTO> finalTopics = chapterService.getTopicsByChapter(ch.getChapterId());
        assertEquals(2, finalTopics.size());
        assertTrue(finalTopics.get(0).getOrderIndex() < finalTopics.get(1).getOrderIndex());
    }
}
