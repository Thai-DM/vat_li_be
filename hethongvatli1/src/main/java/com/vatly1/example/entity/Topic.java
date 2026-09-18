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
@Table(name = "topics")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Topic {

    @Column(name = "topic_id")
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID topicId;

    @Column(name = "subject_id")
    private java.util.UUID subjectId;

    @Column(name = "topic_name")
    private String topicName;

    @Column(name = "order_index")
    private Integer orderIndex;

    @Column(name = "description")
    private String description;


}
