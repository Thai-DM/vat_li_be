package com.vatly1.example.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionCloId implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID questionId;
    private UUID cloId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QuestionCloId that = (QuestionCloId) o;
        return Objects.equals(questionId, that.questionId) && Objects.equals(cloId, that.cloId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(questionId, cloId);
    }
}
