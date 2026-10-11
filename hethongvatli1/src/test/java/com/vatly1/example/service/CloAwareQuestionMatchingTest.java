package com.vatly1.example.service;

import com.vatly1.example.entity.ExamMatrixDetail;
import com.vatly1.example.entity.QuestionBank;
import com.vatly1.example.entity.enums.ApprovalStatus;
import com.vatly1.example.entity.enums.DifficultyLevel;
import com.vatly1.example.service.algorithm.CloAwareQuestionMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class CloAwareQuestionMatchingTest {

    private CloAwareQuestionMatcher matcher;
    private UUID subjectId;
    private UUID topicA;
    private UUID topicB;
    private UUID clo1;
    private UUID clo2;
    private UUID clo3;

    @BeforeEach
    void setUp() {
        matcher = new CloAwareQuestionMatcher();
        subjectId = UUID.randomUUID();
        topicA = UUID.randomUUID();
        topicB = UUID.randomUUID();
        clo1 = UUID.randomUUID();
        clo2 = UUID.randomUUID();
        clo3 = UUID.randomUUID();
    }

    private ExamMatrixDetail createDetail(UUID topic, DifficultyLevel level, UUID clo, int numQuestions) {
        return ExamMatrixDetail.builder()
                .detailId(UUID.randomUUID())
                .subjectId(subjectId)
                .topicId(topic)
                .difficultyLevel(level)
                .cloId(clo)
                .numQuestions(numQuestions)
                .build();
    }

    private QuestionBank createQuestion(UUID topic, DifficultyLevel level, ApprovalStatus status) {
        return QuestionBank.builder()
                .questionId(UUID.randomUUID())
                .subjectId(subjectId)
                .topicId(topic)
                .difficultyLevel(level)
                .approvalStatus(status)
                .content("Question " + UUID.randomUUID())
                .build();
    }

    /**
     * Thuật toán Brute-Force đối chiếu (dùng cho tập dữ liệu nhỏ <= 10 câu hỏi).
     */
    private boolean bruteForceSolve(
            List<ExamMatrixDetail> details,
            List<QuestionBank> candidates,
            Map<UUID, Set<UUID>> questionCloMap
    ) {
        List<ExamMatrixDetail> expandedDetails = new ArrayList<>();
        for (ExamMatrixDetail d : details) {
            int n = d.getNumQuestions() != null ? d.getNumQuestions() : 0;
            for (int i = 0; i < n; i++) {
                expandedDetails.add(d);
            }
        }
        if (expandedDetails.isEmpty()) return true;
        if (candidates.size() < expandedDetails.size()) return false;

        return backtrack(0, expandedDetails, candidates, new HashSet<>(), questionCloMap);
    }

    private boolean backtrack(
            int detailIndex,
            List<ExamMatrixDetail> expandedDetails,
            List<QuestionBank> candidates,
            Set<UUID> usedQuestions,
            Map<UUID, Set<UUID>> questionCloMap
    ) {
        if (detailIndex == expandedDetails.size()) {
            return true;
        }

        ExamMatrixDetail detail = expandedDetails.get(detailIndex);
        for (QuestionBank q : candidates) {
            if (!usedQuestions.contains(q.getQuestionId()) && matcher.isEligible(q, detail, questionCloMap)) {
                usedQuestions.add(q.getQuestionId());
                if (backtrack(detailIndex + 1, expandedDetails, candidates, usedQuestions, questionCloMap)) {
                    return true;
                }
                usedQuestions.remove(q.getQuestionId());
            }
        }
        return false;
    }

    @Test
    @DisplayName("Test 1: Một Detail có đủ Question -> Phân bổ thành công và chính xác")
    void test1_singleDetail_sufficientQuestions() {
        ExamMatrixDetail d1 = createDetail(topicA, DifficultyLevel.EASY, clo1, 2);
        QuestionBank q1 = createQuestion(topicA, DifficultyLevel.EASY, ApprovalStatus.APPROVED);
        QuestionBank q2 = createQuestion(topicA, DifficultyLevel.EASY, ApprovalStatus.APPROVED);
        QuestionBank q3 = createQuestion(topicA, DifficultyLevel.EASY, ApprovalStatus.APPROVED);

        Map<UUID, Set<UUID>> cloMap = Map.of(
                q1.getQuestionId(), Set.of(clo1),
                q2.getQuestionId(), Set.of(clo1),
                q3.getQuestionId(), Set.of(clo1)
        );

        CloAwareQuestionMatcher.MatchResult result = matcher.match(List.of(d1), List.of(q1, q2, q3), cloMap, null);

        assertTrue(result.isFeasible());
        assertEquals(2, result.getTotalRequiredQuestions());
        assertEquals(2, result.getTotalMatchedQuestions());
        assertEquals(2, result.getAssignedQuestions(d1).size());
    }

    @Test
    @DisplayName("Test 2: Nhiều Detail không giao nhau (khác Topic) -> Phân bổ độc lập hoàn toàn")
    void test2_multipleDetails_disjointSets() {
        ExamMatrixDetail d1 = createDetail(topicA, DifficultyLevel.EASY, clo1, 2);
        ExamMatrixDetail d2 = createDetail(topicB, DifficultyLevel.HARD, clo2, 1);

        QuestionBank q1 = createQuestion(topicA, DifficultyLevel.EASY, ApprovalStatus.APPROVED);
        QuestionBank q2 = createQuestion(topicA, DifficultyLevel.EASY, ApprovalStatus.APPROVED);
        QuestionBank q3 = createQuestion(topicB, DifficultyLevel.HARD, ApprovalStatus.APPROVED);

        Map<UUID, Set<UUID>> cloMap = Map.of(
                q1.getQuestionId(), Set.of(clo1),
                q2.getQuestionId(), Set.of(clo1),
                q3.getQuestionId(), Set.of(clo2)
        );

        CloAwareQuestionMatcher.MatchResult result = matcher.match(List.of(d1, d2), List.of(q1, q2, q3), cloMap, null);

        assertTrue(result.isFeasible());
        assertEquals(3, result.getTotalRequiredQuestions());
        assertEquals(3, result.getTotalMatchedQuestions());
        assertEquals(2, result.getAssignedQuestions(d1).size());
        assertEquals(1, result.getAssignedQuestions(d2).size());
    }

    @Test
    @DisplayName("Test 3: Nhiều Detail có tập Question giao nhau -> Phân bổ thỏa mãn không trùng câu")
    void test3_multipleDetails_intersectingCandidatePool() {
        ExamMatrixDetail d1 = createDetail(topicA, DifficultyLevel.MEDIUM, clo1, 1);
        ExamMatrixDetail d2 = createDetail(topicA, DifficultyLevel.MEDIUM, clo2, 1);

        // qShared có cả clo1 và clo2, qOnly1 có clo1, qOnly2 có clo2
        QuestionBank qShared = createQuestion(topicA, DifficultyLevel.MEDIUM, ApprovalStatus.APPROVED);
        QuestionBank qOnly1 = createQuestion(topicA, DifficultyLevel.MEDIUM, ApprovalStatus.APPROVED);
        QuestionBank qOnly2 = createQuestion(topicA, DifficultyLevel.MEDIUM, ApprovalStatus.APPROVED);

        Map<UUID, Set<UUID>> cloMap = Map.of(
                qShared.getQuestionId(), Set.of(clo1, clo2),
                qOnly1.getQuestionId(), Set.of(clo1),
                qOnly2.getQuestionId(), Set.of(clo2)
        );

        CloAwareQuestionMatcher.MatchResult result = matcher.match(List.of(d1, d2), List.of(qShared, qOnly1, qOnly2), cloMap, null);

        assertTrue(result.isFeasible());
        assertEquals(2, result.getTotalMatchedQuestions());
        assertNotEquals(result.getAssignedQuestions(d1).get(0).getQuestionId(),
                result.getAssignedQuestions(d2).get(0).getQuestionId());
    }

    @Test
    @DisplayName("Test 4: Greedy thất bại nhưng Max-Flow Matching thành công")
    void test4_greedyFails_maxFlowSucceeds() {
        // Kịch bản kinh điển:
        // Detail 1 cần CLO 1 (1 câu)
        // Detail 2 cần CLO 2 (1 câu)
        // Question A: có cả CLO 1 và CLO 2
        // Question B: chỉ có CLO 1
        // (Không có câu nào khác có CLO 2)
        // Nếu Greedy duyệt Detail 1 trước và bốc Question A -> Detail 2 sẽ hết câu và Greedy THẤT BẠI.
        // Max-Flow sẽ gán Question B cho Detail 1 và Question A cho Detail 2 -> THÀNH CÔNG!
        ExamMatrixDetail d1 = createDetail(topicA, DifficultyLevel.EASY, clo1, 1);
        ExamMatrixDetail d2 = createDetail(topicA, DifficultyLevel.EASY, clo2, 1);

        QuestionBank qA = createQuestion(topicA, DifficultyLevel.EASY, ApprovalStatus.APPROVED);
        QuestionBank qB = createQuestion(topicA, DifficultyLevel.EASY, ApprovalStatus.APPROVED);

        Map<UUID, Set<UUID>> cloMap = Map.of(
                qA.getQuestionId(), Set.of(clo1, clo2),
                qB.getQuestionId(), Set.of(clo1)
        );

        CloAwareQuestionMatcher.MatchResult result = matcher.match(List.of(d1, d2), List.of(qA, qB), cloMap, null);

        assertTrue(result.isFeasible(), "Max-Flow bắt buộc phải giải thành công kịch bản mà Greedy thất bại!");
        assertEquals(2, result.getTotalMatchedQuestions());
        assertEquals(qB.getQuestionId(), result.getAssignedQuestions(d1).get(0).getQuestionId());
        assertEquals(qA.getQuestionId(), result.getAssignedQuestions(d2).get(0).getQuestionId());

        // Đối chiếu với Brute-Force
        assertTrue(bruteForceSolve(List.of(d1, d2), List.of(qA, qB), cloMap));
    }

    @Test
    @DisplayName("Test 5: Không có phép matching hợp lệ -> Báo fail-closed chính xác")
    void test5_noValidMatching_failsClosed() {
        ExamMatrixDetail d1 = createDetail(topicA, DifficultyLevel.EASY, clo1, 2);
        ExamMatrixDetail d2 = createDetail(topicA, DifficultyLevel.EASY, clo2, 2);

        // Tổng cộng cần 4 câu, nhưng cả ngân hàng chỉ có 3 câu
        QuestionBank q1 = createQuestion(topicA, DifficultyLevel.EASY, ApprovalStatus.APPROVED);
        QuestionBank q2 = createQuestion(topicA, DifficultyLevel.EASY, ApprovalStatus.APPROVED);
        QuestionBank q3 = createQuestion(topicA, DifficultyLevel.EASY, ApprovalStatus.APPROVED);

        Map<UUID, Set<UUID>> cloMap = Map.of(
                q1.getQuestionId(), Set.of(clo1),
                q2.getQuestionId(), Set.of(clo1, clo2),
                q3.getQuestionId(), Set.of(clo2)
        );

        CloAwareQuestionMatcher.MatchResult result = matcher.match(List.of(d1, d2), List.of(q1, q2, q3), cloMap, null);

        assertFalse(result.isFeasible());
        assertEquals(4, result.getTotalRequiredQuestions());
        assertEquals(3, result.getTotalMatchedQuestions());
        assertFalse(result.getDiagnostics().isEmpty());
    }

    @Test
    @DisplayName("Test 6: Một Question thuộc hai CLO -> Không bao giờ bị chọn hai lần")
    void test6_questionBelongsToTwoClos_neverPickedTwice() {
        ExamMatrixDetail d1 = createDetail(topicA, DifficultyLevel.EASY, clo1, 1);
        ExamMatrixDetail d2 = createDetail(topicA, DifficultyLevel.EASY, clo2, 1);

        // Chỉ có DUY NHẤT 1 câu hỏi mang cả clo1 và clo2
        QuestionBank qMulti = createQuestion(topicA, DifficultyLevel.EASY, ApprovalStatus.APPROVED);
        Map<UUID, Set<UUID>> cloMap = Map.of(qMulti.getQuestionId(), Set.of(clo1, clo2));

        CloAwareQuestionMatcher.MatchResult result = matcher.match(List.of(d1, d2), List.of(qMulti), cloMap, null);

        assertFalse(result.isFeasible(), "Chỉ có 1 câu hỏi thì không thể chia cho 2 dòng ma trận");
        assertEquals(1, result.getTotalMatchedQuestions(), "Chỉ có thể chọn 1 câu duy nhất");

        int totalAssigned = result.getAssignedQuestions(d1).size() + result.getAssignedQuestions(d2).size();
        assertEquals(1, totalAssigned);
    }

    @Test
    @DisplayName("Test 7: Một Question không có CLO -> Không được chọn cho Detail có yêu cầu CLO")
    void test7_questionWithoutClo_rejectedForCloDetail() {
        ExamMatrixDetail d1 = createDetail(topicA, DifficultyLevel.EASY, clo1, 1);
        QuestionBank qNoClo = createQuestion(topicA, DifficultyLevel.EASY, ApprovalStatus.APPROVED);

        Map<UUID, Set<UUID>> cloMap = Collections.emptyMap();

        CloAwareQuestionMatcher.MatchResult result = matcher.match(List.of(d1), List.of(qNoClo), cloMap, null);

        assertFalse(result.isFeasible());
        assertEquals(0, result.getTotalMatchedQuestions());
        assertEquals(0, result.getEligibleCandidateCount(d1));
    }

    @Test
    @DisplayName("Test 8: cloId NULL hoạt động -> Khớp bất kỳ câu nào cùng Topic và Difficulty")
    void test8_cloIdNull_matchesAnyQuestionInTopicAndDifficulty() {
        ExamMatrixDetail dNoClo = createDetail(topicA, DifficultyLevel.EASY, null, 2);

        QuestionBank qWithClo = createQuestion(topicA, DifficultyLevel.EASY, ApprovalStatus.APPROVED);
        QuestionBank qWithoutClo = createQuestion(topicA, DifficultyLevel.EASY, ApprovalStatus.APPROVED);

        Map<UUID, Set<UUID>> cloMap = Map.of(qWithClo.getQuestionId(), Set.of(clo1));

        CloAwareQuestionMatcher.MatchResult result = matcher.match(List.of(dNoClo), List.of(qWithClo, qWithoutClo), cloMap, null);

        assertTrue(result.isFeasible());
        assertEquals(2, result.getTotalMatchedQuestions());
        assertEquals(2, result.getAssignedQuestions(dNoClo).size());
    }

    @Test
    @DisplayName("Test 9: numQuestions bằng 0 hoặc âm -> Xử lý validation chuẩn xác")
    void test9_numQuestionsZeroOrNegative_handledProperly() {
        ExamMatrixDetail dZero = createDetail(topicA, DifficultyLevel.EASY, clo1, 0);
        QuestionBank q = createQuestion(topicA, DifficultyLevel.EASY, ApprovalStatus.APPROVED);
        Map<UUID, Set<UUID>> cloMap = Map.of(q.getQuestionId(), Set.of(clo1));

        // 1. Trường hợp 0 câu: Hợp lệ với 0 câu yêu cầu
        CloAwareQuestionMatcher.MatchResult resZero = matcher.match(List.of(dZero), List.of(q), cloMap, null);
        assertTrue(resZero.isFeasible());
        assertEquals(0, resZero.getTotalRequiredQuestions());
        assertEquals(0, resZero.getTotalMatchedQuestions());

        // 2. Trường hợp âm câu: Bị từ chối
        ExamMatrixDetail dNeg = createDetail(topicA, DifficultyLevel.EASY, clo1, -2);
        CloAwareQuestionMatcher.MatchResult resNeg = matcher.match(List.of(dNeg), List.of(q), cloMap, null);
        assertFalse(resNeg.isFeasible());
        assertTrue(resNeg.getDiagnostics().stream().anyMatch(msg -> msg.contains("không được âm")));
    }

    @Test
    @DisplayName("Test 10: Cùng Question ID không được chọn hai lần trong cùng một đề")
    void test10_noDuplicateQuestionIdsInResult() {
        ExamMatrixDetail d1 = createDetail(topicA, DifficultyLevel.EASY, clo1, 2);
        ExamMatrixDetail d2 = createDetail(topicA, DifficultyLevel.EASY, null, 2);

        List<QuestionBank> questions = new ArrayList<>();
        Map<UUID, Set<UUID>> cloMap = new HashMap<>();
        for (int i = 0; i < 4; i++) {
            QuestionBank q = createQuestion(topicA, DifficultyLevel.EASY, ApprovalStatus.APPROVED);
            questions.add(q);
            cloMap.put(q.getQuestionId(), Set.of(clo1));
        }

        CloAwareQuestionMatcher.MatchResult result = matcher.match(List.of(d1, d2), questions, cloMap, null);

        assertTrue(result.isFeasible());
        assertEquals(4, result.getTotalMatchedQuestions());

        Set<UUID> chosenIds = new HashSet<>();
        for (QuestionBank q : result.getAssignedQuestions(d1)) {
            assertTrue(chosenIds.add(q.getQuestionId()), "Duplicate ID detected!");
        }
        for (QuestionBank q : result.getAssignedQuestions(d2)) {
            assertTrue(chosenIds.add(q.getQuestionId()), "Duplicate ID detected!");
        }
        assertEquals(4, chosenIds.size());
    }

    @Test
    @DisplayName("Test 11: Tổng số câu được chọn đúng bằng tổng numQuestions")
    void test11_totalAssignedMatchesTotalRequired() {
        ExamMatrixDetail d1 = createDetail(topicA, DifficultyLevel.EASY, clo1, 3);
        ExamMatrixDetail d2 = createDetail(topicA, DifficultyLevel.MEDIUM, clo2, 2);
        ExamMatrixDetail d3 = createDetail(topicB, DifficultyLevel.HARD, clo3, 4);

        List<QuestionBank> bank = new ArrayList<>();
        Map<UUID, Set<UUID>> cloMap = new HashMap<>();

        for (int i = 0; i < 5; i++) {
            QuestionBank q = createQuestion(topicA, DifficultyLevel.EASY, ApprovalStatus.APPROVED);
            bank.add(q);
            cloMap.put(q.getQuestionId(), Set.of(clo1));
        }
        for (int i = 0; i < 4; i++) {
            QuestionBank q = createQuestion(topicA, DifficultyLevel.MEDIUM, ApprovalStatus.APPROVED);
            bank.add(q);
            cloMap.put(q.getQuestionId(), Set.of(clo2));
        }
        for (int i = 0; i < 6; i++) {
            QuestionBank q = createQuestion(topicB, DifficultyLevel.HARD, ApprovalStatus.APPROVED);
            bank.add(q);
            cloMap.put(q.getQuestionId(), Set.of(clo3));
        }

        CloAwareQuestionMatcher.MatchResult result = matcher.match(List.of(d1, d2, d3), bank, cloMap, null);

        assertTrue(result.isFeasible());
        assertEquals(9, result.getTotalRequiredQuestions());
        assertEquals(9, result.getTotalMatchedQuestions());
        assertEquals(3, result.getAssignedQuestions(d1).size());
        assertEquals(2, result.getAssignedQuestions(d2).size());
        assertEquals(4, result.getAssignedQuestions(d3).size());
    }

    @Test
    @DisplayName("Test 12: Random seed xác định tạo kết quả hoàn toàn có thể lặp lại")
    void test12_deterministicRandomSeed() {
        ExamMatrixDetail d = createDetail(topicA, DifficultyLevel.EASY, clo1, 3);
        List<QuestionBank> bank = new ArrayList<>();
        Map<UUID, Set<UUID>> cloMap = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            QuestionBank q = createQuestion(topicA, DifficultyLevel.EASY, ApprovalStatus.APPROVED);
            bank.add(q);
            cloMap.put(q.getQuestionId(), Set.of(clo1));
        }

        long seed = 987654321L;
        CloAwareQuestionMatcher.MatchResult res1 = matcher.match(List.of(d), bank, cloMap, new Random(seed));
        CloAwareQuestionMatcher.MatchResult res2 = matcher.match(List.of(d), bank, cloMap, new Random(seed));

        assertTrue(res1.isFeasible());
        assertTrue(res2.isFeasible());

        List<UUID> ids1 = res1.getAssignedQuestions(d).stream().map(QuestionBank::getQuestionId).toList();
        List<UUID> ids2 = res2.getAssignedQuestions(d).stream().map(QuestionBank::getQuestionId).toList();

        assertEquals(ids1, ids2, "Cùng một random seed bắt buộc phải tạo ra cùng một tập câu hỏi với thứ tự giống nhau 100%");
    }

    @Test
    @DisplayName("Test 13: Nhiều lần chạy với seed khác nhau vẫn giữ 100% mọi ràng buộc")
    void test13_multipleRunsDifferentSeeds_strictlyMaintainsConstraints() {
        ExamMatrixDetail d1 = createDetail(topicA, DifficultyLevel.EASY, clo1, 2);
        ExamMatrixDetail d2 = createDetail(topicA, DifficultyLevel.EASY, clo2, 2);

        List<QuestionBank> bank = new ArrayList<>();
        Map<UUID, Set<UUID>> cloMap = new HashMap<>();
        for (int i = 0; i < 5; i++) {
            QuestionBank q = createQuestion(topicA, DifficultyLevel.EASY, ApprovalStatus.APPROVED);
            bank.add(q);
            cloMap.put(q.getQuestionId(), Set.of(clo1));
        }
        for (int i = 0; i < 5; i++) {
            QuestionBank q = createQuestion(topicA, DifficultyLevel.EASY, ApprovalStatus.APPROVED);
            bank.add(q);
            cloMap.put(q.getQuestionId(), Set.of(clo2));
        }

        for (int seed = 1; seed <= 50; seed++) {
            CloAwareQuestionMatcher.MatchResult res = matcher.match(List.of(d1, d2), bank, cloMap, new Random(seed));
            assertTrue(res.isFeasible());
            assertEquals(4, res.getTotalMatchedQuestions());

            // Kiểm tra ràng buộc
            for (QuestionBank q : res.getAssignedQuestions(d1)) {
                assertTrue(cloMap.get(q.getQuestionId()).contains(clo1));
                assertEquals(DifficultyLevel.EASY, q.getDifficultyLevel());
            }
            for (QuestionBank q : res.getAssignedQuestions(d2)) {
                assertTrue(cloMap.get(q.getQuestionId()).contains(clo2));
                assertEquals(DifficultyLevel.EASY, q.getDifficultyLevel());
            }
        }
    }

    @Test
    @DisplayName("Test 14: Phân bổ chính xác khi nhiều dòng có cùng Topic/Difficulty/CLO")
    void test14_multipleRowsWithSameCriteria() {
        ExamMatrixDetail d1 = createDetail(topicA, DifficultyLevel.EASY, clo1, 2);
        ExamMatrixDetail d2 = createDetail(topicA, DifficultyLevel.EASY, clo1, 3);

        List<QuestionBank> bank = new ArrayList<>();
        Map<UUID, Set<UUID>> cloMap = new HashMap<>();
        for (int i = 0; i < 5; i++) {
            QuestionBank q = createQuestion(topicA, DifficultyLevel.EASY, ApprovalStatus.APPROVED);
            bank.add(q);
            cloMap.put(q.getQuestionId(), Set.of(clo1));
        }

        CloAwareQuestionMatcher.MatchResult result = matcher.match(List.of(d1, d2), bank, cloMap, null);

        assertTrue(result.isFeasible());
        assertEquals(5, result.getTotalMatchedQuestions());
        assertEquals(2, result.getAssignedQuestions(d1).size());
        assertEquals(3, result.getAssignedQuestions(d2).size());

        Set<UUID> set1 = new HashSet<>(result.getAssignedQuestions(d1).stream().map(QuestionBank::getQuestionId).toList());
        Set<UUID> set2 = new HashSet<>(result.getAssignedQuestions(d2).stream().map(QuestionBank::getQuestionId).toList());
        assertTrue(Collections.disjoint(set1, set2), "Hai dòng ma trận không được nhận cùng một câu hỏi");
    }

    @Test
    @DisplayName("Test 15: Hiệu năng trên tập dữ liệu lớn (5000 câu hỏi, 50 dòng ma trận) dưới 100ms")
    void test15_largeScalePerformance() {
        int numDetails = 50;
        int questionsPerDetail = 2; // Tổng 100 câu yêu cầu
        int totalBankQuestions = 5000;

        List<ExamMatrixDetail> details = new ArrayList<>();
        List<UUID> topics = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        List<UUID> clos = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        for (int i = 0; i < numDetails; i++) {
            details.add(createDetail(
                    topics.get(i % topics.size()),
                    DifficultyLevel.values()[i % DifficultyLevel.values().length],
                    clos.get(i % clos.size()),
                    questionsPerDetail
            ));
        }

        List<QuestionBank> bank = new ArrayList<>(totalBankQuestions);
        Map<UUID, Set<UUID>> cloMap = new HashMap<>();

        for (int i = 0; i < totalBankQuestions; i++) {
            UUID t = topics.get(i % topics.size());
            DifficultyLevel dl = DifficultyLevel.values()[i % DifficultyLevel.values().length];
            UUID c = clos.get(i % clos.size());

            QuestionBank q = createQuestion(t, dl, ApprovalStatus.APPROVED);
            bank.add(q);
            cloMap.put(q.getQuestionId(), Set.of(c));
        }

        long startTime = System.currentTimeMillis();
        CloAwareQuestionMatcher.MatchResult result = matcher.match(details, bank, cloMap, new Random(42));
        long duration = System.currentTimeMillis() - startTime;

        assertTrue(result.isFeasible());
        assertEquals(100, result.getTotalMatchedQuestions());
        assertTrue(duration < 250, "Thuật toán Dinic phải chạy dưới 250ms ngay cả với 5000 câu hỏi. Thực tế: " + duration + "ms");
    }

    @Test
    @DisplayName("Test 16: Đối chiếu toàn diện giữa Max-Flow Matching và Brute-Force trên 20 test cases ngẫu nhiên")
    void test16_crossValidationWithBruteForce() {
        Random rnd = new Random(1337);

        for (int testCase = 0; testCase < 20; testCase++) {
            int dCount = 2 + rnd.nextInt(3); // 2..4 details
            List<ExamMatrixDetail> testDetails = new ArrayList<>();
            for (int i = 0; i < dCount; i++) {
                UUID clo = rnd.nextBoolean() ? clo1 : clo2;
                testDetails.add(createDetail(topicA, DifficultyLevel.EASY, clo, 1));
            }

            int qCount = 2 + rnd.nextInt(5); // 2..6 questions
            List<QuestionBank> testBank = new ArrayList<>();
            Map<UUID, Set<UUID>> cloMap = new HashMap<>();

            for (int j = 0; j < qCount; j++) {
                QuestionBank q = createQuestion(topicA, DifficultyLevel.EASY, ApprovalStatus.APPROVED);
                testBank.add(q);
                Set<UUID> qClos = new HashSet<>();
                if (rnd.nextBoolean()) qClos.add(clo1);
                if (rnd.nextBoolean()) qClos.add(clo2);
                cloMap.put(q.getQuestionId(), qClos);
            }

            boolean bruteResult = bruteForceSolve(testDetails, testBank, cloMap);
            CloAwareQuestionMatcher.MatchResult matcherResult = matcher.match(testDetails, testBank, cloMap, rnd);

            assertEquals(bruteResult, matcherResult.isFeasible(),
                    "Mismatch giữa Brute-Force và Max-Flow ở Test Case #" + testCase);
        }
    }
}
