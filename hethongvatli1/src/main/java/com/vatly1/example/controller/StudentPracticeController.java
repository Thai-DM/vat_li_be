package com.vatly1.example.controller;

import com.vatly1.example.model.dto.PracticeSessionDTO;
import com.vatly1.example.model.dto.PracticeSessionResultDTO;
import com.vatly1.example.model.dto.WeakTopicDTO;
import com.vatly1.example.model.request.CreatePracticeRecommendationRequest;
import com.vatly1.example.model.request.SubmitSessionAnswerRequest;
import com.vatly1.example.service.IPracticeRecommendationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/student")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
public class StudentPracticeController {

    private final IPracticeRecommendationService practiceRecommendationService;

    @GetMapping("/classes/{classId}/weak-topics")
    public ResponseEntity<List<WeakTopicDTO>> getWeakTopics(
            HttpServletRequest request,
            @PathVariable UUID classId) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        List<WeakTopicDTO> weakTopics = practiceRecommendationService.getStudentWeakTopics(classId, currentUserId);
        return ResponseEntity.ok(weakTopics);
    }

    @PostMapping("/classes/{classId}/practice-recommendations")
    public ResponseEntity<PracticeSessionDTO> createAdaptivePracticeSession(
            HttpServletRequest request,
            @PathVariable UUID classId,
            @Valid @RequestBody(required = false) CreatePracticeRecommendationRequest reqBody) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        PracticeSessionDTO session = practiceRecommendationService.createAdaptivePracticeSession(classId, reqBody, currentUserId);
        return ResponseEntity.ok(session);
    }

    @GetMapping("/practice-sessions/{sessionId}")
    public ResponseEntity<PracticeSessionDTO> getPracticeSession(
            HttpServletRequest request,
            @PathVariable UUID sessionId) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        PracticeSessionDTO session = practiceRecommendationService.getPracticeSession(sessionId, currentUserId);
        return ResponseEntity.ok(session);
    }

    @PostMapping("/practice-sessions/{sessionId}/answers")
    public ResponseEntity<Void> saveAnswer(
            HttpServletRequest request,
            @PathVariable UUID sessionId,
            @Valid @RequestBody SubmitSessionAnswerRequest reqBody) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        practiceRecommendationService.saveAnswer(sessionId, reqBody, currentUserId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/practice-sessions/{sessionId}/submit")
    public ResponseEntity<PracticeSessionResultDTO> submitSession(
            HttpServletRequest request,
            @PathVariable UUID sessionId) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        PracticeSessionResultDTO result = practiceRecommendationService.submitSession(sessionId, currentUserId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/practice-sessions/{sessionId}/results")
    public ResponseEntity<PracticeSessionResultDTO> getSessionResult(
            HttpServletRequest request,
            @PathVariable UUID sessionId) {
        UUID currentUserId = UUID.fromString((String) request.getAttribute("userId"));
        PracticeSessionResultDTO result = practiceRecommendationService.getSessionResult(sessionId, currentUserId);
        return ResponseEntity.ok(result);
    }
}
