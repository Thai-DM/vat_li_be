package com.vatly1.example.model.request;


import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitExperimentDTO {
    
    private String evidenceUrl;
    
    private MultipartFile file;
    
    private JsonNode rawDataJson;
}