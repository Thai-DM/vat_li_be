package com.vatly1.example.repository.projection;

public interface StudentLatestAnswerProjection {
    String getStudentId();
    String getTopicId();
    String getQuestionId();
    Boolean getIsCorrect();
}
