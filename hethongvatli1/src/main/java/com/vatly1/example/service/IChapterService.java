package com.vatly1.example.service;

import com.vatly1.example.model.dto.ChapterDTO;
import com.vatly1.example.model.dto.TopicDTO;
import com.vatly1.example.model.request.CreateChapterDTO;
import com.vatly1.example.model.request.ReorderTopicsDTO;
import com.vatly1.example.model.request.UpdateChapterDTO;

import java.util.List;
import java.util.UUID;

public interface IChapterService {

    ChapterDTO createChapter(UUID subjectId, CreateChapterDTO dto);

    List<ChapterDTO> getChaptersBySubject(UUID subjectId, Boolean activeOnly);

    ChapterDTO getChapterById(UUID chapterId);

    ChapterDTO updateChapter(UUID chapterId, UpdateChapterDTO dto);

    ChapterDTO updateChapterStatus(UUID chapterId, Boolean isActive);

    void deleteChapter(UUID chapterId);

    long getTopicCount(UUID chapterId);

    List<TopicDTO> getTopicsByChapter(UUID chapterId);

    List<TopicDTO> reorderTopics(UUID chapterId, ReorderTopicsDTO dto);
}
