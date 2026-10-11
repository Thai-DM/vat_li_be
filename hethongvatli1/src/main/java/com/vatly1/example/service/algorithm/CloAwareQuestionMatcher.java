package com.vatly1.example.service.algorithm;

import com.vatly1.example.entity.ExamMatrixDetail;
import com.vatly1.example.entity.QuestionBank;
import com.vatly1.example.entity.enums.ApprovalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Thuật toán phân bổ câu hỏi đề thi cuối kỳ có xét chuẩn đầu ra (CLO)
 * dựa trên mô hình Maximum Bipartite Matching / Network Flow (Dinic's Algorithm).
 *
 * Đảm bảo:
 * 1. Mỗi câu hỏi chỉ xuất hiện tối đa một lần trong toàn bộ đề thi (không duplicate).
 * 2. Thỏa mãn đồng thời mọi ràng buộc của từng dòng ExamMatrixDetail (Topic, Difficulty, APPROVED, CLO).
 * 3. Tối ưu toàn cục: Giải quyết triệt để tranh chấp câu hỏi giữa các dòng ma trận, vượt trội hoàn toàn so với Greedy.
 * 4. Hỗ trợ Randomization ngẫu nhiên hóa hoặc tái lặp xác định theo seed.
 * 5. Tuân thủ tuyệt đối cơ chế FAIL-CLOSED đối với đề thi cuối kỳ (FINAL).
 */
@Component
@Slf4j
public class CloAwareQuestionMatcher {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MatchResult {
        private boolean isFeasible;
        private int totalRequiredQuestions;
        private int totalMatchedQuestions;
        private int totalUniqueCandidates;
        private Map<ExamMatrixDetail, List<QuestionBank>> assignedQuestions;
        private Map<ExamMatrixDetail, Integer> detailEligibleCountMap;
        private Map<ExamMatrixDetail, Integer> detailMatchedCountMap;
        private List<String> diagnostics;

        public List<QuestionBank> getAssignedQuestions(ExamMatrixDetail detail) {
            return assignedQuestions != null ? assignedQuestions.getOrDefault(detail, Collections.emptyList()) : Collections.emptyList();
        }

        public int getEligibleCandidateCount(ExamMatrixDetail detail) {
            return detailEligibleCountMap != null ? detailEligibleCountMap.getOrDefault(detail, 0) : 0;
        }

        public int getMatchedCount(ExamMatrixDetail detail) {
            return detailMatchedCountMap != null ? detailMatchedCountMap.getOrDefault(detail, 0) : 0;
        }
    }

    public static class Edge {
        int to;
        int rev;
        int capacity;
        int flow;

        Edge(int to, int rev, int capacity) {
            this.to = to;
            this.rev = rev;
            this.capacity = capacity;
            this.flow = 0;
        }

        int remaining() {
            return capacity - flow;
        }
    }

    private static class Dinic {
        int n;
        List<List<Edge>> adj;
        int[] level;
        int[] ptr;

        Dinic(int n) {
            this.n = n;
            this.adj = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                this.adj.add(new ArrayList<>());
            }
            this.level = new int[n];
            this.ptr = new int[n];
        }

        void addEdge(int from, int to, int cap) {
            Edge a = new Edge(to, adj.get(to).size(), cap);
            Edge b = new Edge(from, adj.get(from).size(), 0);
            adj.get(from).add(a);
            adj.get(to).add(b);
        }

        boolean bfs(int s, int t) {
            Arrays.fill(level, -1);
            level[s] = 0;
            Queue<Integer> q = new ArrayDeque<>();
            q.add(s);
            while (!q.isEmpty()) {
                int v = q.poll();
                for (Edge e : adj.get(v)) {
                    if (e.remaining() > 0 && level[e.to] < 0) {
                        level[e.to] = level[v] + 1;
                        q.add(e.to);
                    }
                }
            }
            return level[t] >= 0;
        }

        int dfs(int v, int t, int pushed) {
            if (pushed == 0 || v == t) return pushed;
            for (int cid = ptr[v]; cid < adj.get(v).size(); cid++, ptr[v]++) {
                Edge e = adj.get(v).get(cid);
                if (level[e.to] == level[v] + 1 && e.remaining() > 0) {
                    int trFlow = dfs(e.to, t, Math.min(pushed, e.remaining()));
                    if (trFlow > 0) {
                        e.flow += trFlow;
                        adj.get(e.to).get(e.rev).flow -= trFlow;
                        return trFlow;
                    }
                }
            }
            return 0;
        }

        int maxFlow(int s, int t) {
            int flow = 0;
            while (bfs(s, t)) {
                Arrays.fill(ptr, 0);
                while (true) {
                    int pushed = dfs(s, t, Integer.MAX_VALUE);
                    if (pushed == 0) break;
                    flow += pushed;
                }
            }
            return flow;
        }
    }

    /**
     * Thực thi thuật toán Maximum Network Flow để tìm phân bổ câu hỏi tối ưu.
     *
     * @param details Danh sách chi tiết ma trận
     * @param candidateQuestions Danh sách câu hỏi ứng viên
     * @param questionCloMap Ánh xạ questionId -> tập CLO IDs
     * @param random Instance Random phục vụ ngẫu nhiên hóa (nullable)
     * @return MatchResult chứa kết quả phân bổ và chẩn đoán
     */
    public MatchResult match(
            List<ExamMatrixDetail> details,
            List<QuestionBank> candidateQuestions,
            Map<UUID, Set<UUID>> questionCloMap,
            Random random
    ) {
        if (details == null || details.isEmpty()) {
            return MatchResult.builder()
                    .isFeasible(false)
                    .totalRequiredQuestions(0)
                    .totalMatchedQuestions(0)
                    .totalUniqueCandidates(0)
                    .assignedQuestions(Collections.emptyMap())
                    .detailEligibleCountMap(Collections.emptyMap())
                    .detailMatchedCountMap(Collections.emptyMap())
                    .diagnostics(List.of("Ma trận đề thi không có cấu hình chi tiết (details)"))
                    .build();
        }

        int totalRequired = 0;
        for (ExamMatrixDetail d : details) {
            if (d.getNumQuestions() != null && d.getNumQuestions() < 0) {
                return MatchResult.builder()
                        .isFeasible(false)
                        .totalRequiredQuestions(0)
                        .totalMatchedQuestions(0)
                        .totalUniqueCandidates(0)
                        .assignedQuestions(Collections.emptyMap())
                        .detailEligibleCountMap(Collections.emptyMap())
                        .detailMatchedCountMap(Collections.emptyMap())
                        .diagnostics(List.of("Số lượng câu hỏi yêu cầu trong ma trận không được âm (detailId: " + d.getDetailId() + ")"))
                        .build();
            }
            totalRequired += (d.getNumQuestions() != null ? d.getNumQuestions() : 0);
        }

        // Loại bỏ câu hỏi trùng lặp theo questionId và chỉ giữ câu APPROVED
        Map<UUID, QuestionBank> uniqueCandidatesMap = new LinkedHashMap<>();
        if (candidateQuestions != null) {
            for (QuestionBank q : candidateQuestions) {
                if (q != null && q.getQuestionId() != null && q.getApprovalStatus() == ApprovalStatus.APPROVED) {
                    uniqueCandidatesMap.putIfAbsent(q.getQuestionId(), q);
                }
            }
        }
        List<QuestionBank> uniqueCandidates = new ArrayList<>(uniqueCandidatesMap.values());
        int totalUniqueCandidates = uniqueCandidates.size();

        if (totalRequired == 0) {
            Map<ExamMatrixDetail, List<QuestionBank>> emptyAssignments = new LinkedHashMap<>();
            Map<ExamMatrixDetail, Integer> zeroCounts = new LinkedHashMap<>();
            for (ExamMatrixDetail d : details) {
                emptyAssignments.put(d, Collections.emptyList());
                zeroCounts.put(d, 0);
            }
            return MatchResult.builder()
                    .isFeasible(true)
                    .totalRequiredQuestions(0)
                    .totalMatchedQuestions(0)
                    .totalUniqueCandidates(totalUniqueCandidates)
                    .assignedQuestions(emptyAssignments)
                    .detailEligibleCountMap(zeroCounts)
                    .detailMatchedCountMap(zeroCounts)
                    .diagnostics(Collections.emptyList())
                    .build();
        }

        int m = details.size();
        int n = uniqueCandidates.size();

        Map<UUID, Integer> questionIndexMap = new HashMap<>();
        for (int j = 0; j < n; j++) {
            questionIndexMap.put(uniqueCandidates.get(j).getQuestionId(), j);
        }

        Map<ExamMatrixDetail, Integer> detailEligibleCountMap = new LinkedHashMap<>();
        List<List<QuestionBank>> eligiblePerDetail = new ArrayList<>(m);

        for (ExamMatrixDetail detail : details) {
            List<QuestionBank> eligible = new ArrayList<>();
            for (QuestionBank q : uniqueCandidates) {
                if (isEligible(q, detail, questionCloMap)) {
                    eligible.add(q);
                }
            }
            detailEligibleCountMap.put(detail, eligible.size());
            eligiblePerDetail.add(eligible);
        }

        // Xây dựng mạng luồng Network Flow:
        // Node 0: Source S
        // Node 1 .. m: Matrix Details (D_i = i + 1)
        // Node m + 1 .. m + n: Candidate Questions (Q_j = m + 1 + j)
        // Node m + n + 1: Sink T
        int source = 0;
        int sink = m + n + 1;
        int totalNodes = sink + 1;

        Dinic dinic = new Dinic(totalNodes);

        // 1. Cạnh Source -> Detail: capacity = numQuestions
        for (int i = 0; i < m; i++) {
            int req = details.get(i).getNumQuestions() != null ? details.get(i).getNumQuestions() : 0;
            dinic.addEdge(source, i + 1, req);
        }

        // 2. Cạnh Detail -> Question: capacity = 1
        for (int i = 0; i < m; i++) {
            List<QuestionBank> eligible = eligiblePerDetail.get(i);
            List<QuestionBank> shuffledEligible = new ArrayList<>(eligible);
            if (random != null) {
                Collections.shuffle(shuffledEligible, random);
            } else {
                Collections.shuffle(shuffledEligible);
            }

            for (QuestionBank q : shuffledEligible) {
                Integer qIdx = questionIndexMap.get(q.getQuestionId());
                if (qIdx != null) {
                    dinic.addEdge(i + 1, m + 1 + qIdx, 1);
                }
            }
        }

        // 3. Cạnh Question -> Sink: capacity = 1 (Đảm bảo mỗi câu hỏi chỉ được chọn tối đa 1 lần)
        for (int j = 0; j < n; j++) {
            dinic.addEdge(m + 1 + j, sink, 1);
        }

        int maxFlow = dinic.maxFlow(source, sink);
        boolean isFeasible = (maxFlow == totalRequired);

        // Trích xuất kết quả phân bổ
        Map<ExamMatrixDetail, List<QuestionBank>> assignedQuestions = new LinkedHashMap<>();
        Map<ExamMatrixDetail, Integer> detailMatchedCountMap = new LinkedHashMap<>();

        for (int i = 0; i < m; i++) {
            ExamMatrixDetail detail = details.get(i);
            List<QuestionBank> matched = new ArrayList<>();
            for (Edge e : dinic.adj.get(i + 1)) {
                if (e.capacity > 0 && e.flow > 0 && e.to >= m + 1 && e.to <= m + n) {
                    int qIdx = e.to - (m + 1);
                    matched.add(uniqueCandidates.get(qIdx));
                }
            }
            assignedQuestions.put(detail, matched);
            detailMatchedCountMap.put(detail, matched.size());
        }

        // Xây dựng thông điệp chẩn đoán chi tiết nếu không khả thi
        List<String> diagnostics = new ArrayList<>();
        if (!isFeasible) {
            for (int i = 0; i < m; i++) {
                ExamMatrixDetail d = details.get(i);
                int req = d.getNumQuestions() != null ? d.getNumQuestions() : 0;
                int elig = detailEligibleCountMap.getOrDefault(d, 0);
                int matched = detailMatchedCountMap.getOrDefault(d, 0);
                if (elig < req) {
                    diagnostics.add(String.format("Chi tiết [Topic ID: %s, Mức độ: %s, CLO ID: %s]: Cần %d câu nhưng chỉ có %d câu ứng viên APPROVED phù hợp.",
                            d.getTopicId(), d.getDifficultyLevel(), d.getCloId() != null ? d.getCloId() : "NONE", req, elig));
                } else if (matched < req) {
                    diagnostics.add(String.format("Chi tiết [Topic ID: %s, Mức độ: %s, CLO ID: %s]: Có %d câu ứng viên nhưng chỉ phân bổ được %d/%d câu do xung đột/tranh chấp tài nguyên với các dòng khác.",
                            d.getTopicId(), d.getDifficultyLevel(), d.getCloId() != null ? d.getCloId() : "NONE", elig, matched, req));
                }
            }
            if (diagnostics.isEmpty()) {
                diagnostics.add(String.format("Tổng số câu hỏi khả dụng theo Maximum Matching là %d/%d câu (thiếu %d câu).",
                        maxFlow, totalRequired, totalRequired - maxFlow));
            }
        }

        return MatchResult.builder()
                .isFeasible(isFeasible)
                .totalRequiredQuestions(totalRequired)
                .totalMatchedQuestions(maxFlow)
                .totalUniqueCandidates(totalUniqueCandidates)
                .assignedQuestions(assignedQuestions)
                .detailEligibleCountMap(detailEligibleCountMap)
                .detailMatchedCountMap(detailMatchedCountMap)
                .diagnostics(diagnostics)
                .build();
    }

    /**
     * Kiểm tra tính hợp lệ của câu hỏi đối với một dòng chi tiết ma trận.
     */
    public boolean isEligible(
            QuestionBank question,
            ExamMatrixDetail detail,
            Map<UUID, Set<UUID>> questionCloMap
    ) {
        if (question == null || detail == null) {
            return false;
        }

        if (question.getApprovalStatus() != ApprovalStatus.APPROVED) {
            return false;
        }

        if (!Objects.equals(question.getSubjectId(), detail.getSubjectId())) {
            return false;
        }

        if (!Objects.equals(question.getTopicId(), detail.getTopicId())) {
            return false;
        }

        if (question.getDifficultyLevel() != detail.getDifficultyLevel()) {
            return false;
        }

        if (detail.getCloId() != null) {
            if (questionCloMap == null) {
                return false;
            }
            Set<UUID> assignedClos = questionCloMap.get(question.getQuestionId());
            if (assignedClos == null || !assignedClos.contains(detail.getCloId())) {
                return false;
            }
        }

        return true;
    }
}
