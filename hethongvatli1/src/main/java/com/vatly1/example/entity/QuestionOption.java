package com.vatly1.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "question_options")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionOption {

    @Column(name = "option_id")
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID optionId;

    @Column(name = "question_id")
    private java.util.UUID questionId;

    @Column(name = "option_text")
    private String optionText;

    @Column(name = "media_url")
    private String mediaUrl;

    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column(name = "order_index")
    private Integer orderIndex;


}