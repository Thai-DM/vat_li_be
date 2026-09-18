package com.vatly1.example.dto.request;

import com.vatly1.example.dto.dto.*;
import com.vatly1.example.dto.request.*;
import com.vatly1.example.dto.response.*;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkUpdateSettingsRequest {
    private Map<String, String> settings;
}