package com.vatly1.example.model.dto;

import com.vatly1.example.model.request.*;
import com.vatly1.example.model.response.*;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicDTO {

    private UUID topicId;

    private UUID subjectId;

    private String topicName;

    private Integer orderIndex;

    private String description;

}