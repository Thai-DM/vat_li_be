package com.vatly1.example.service.impl;

import com.vatly1.example.model.request.CreateLearningMaterialDTO;
import com.vatly1.example.model.dto.LearningMaterialDTO;
import com.vatly1.example.entity.FileUpload;
import com.vatly1.example.entity.LearningMaterial;
import com.vatly1.example.entity.enums.ApprovalStatus;
import com.vatly1.example.entity.enums.FileProcessingStatus;
import com.vatly1.example.entity.enums.MaterialType;
import com.vatly1.example.exception.CustomException;
import com.vatly1.example.repository.FileUploadRepository;
import com.vatly1.example.repository.LearningMaterialRepository;
import com.vatly1.example.repository.TopicRepository;
import com.vatly1.example.service.IFileStorageService;
import com.vatly1.example.service.ILearningMaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LearningMaterialServiceImpl implements ILearningMaterialService {

    private final LearningMaterialRepository materialRepository;
    private final TopicRepository topicRepository;
    private final FileUploadRepository fileUploadRepository;
    private final IFileStorageService fileStorageService;

    @Override
    public List<LearningMaterialDTO> getMaterialsByTopic(UUID topicId, String role) {
        if (!topicRepository.existsById(topicId)) {
            throw new CustomException("Topic not found", HttpStatus.NOT_FOUND);
        }
        
        List<LearningMaterial> materials = materialRepository.findByTopicIdOrderByCreatedAtDesc(topicId);
        
        // Students can only see APPROVED materials
        if ("STUDENT".equals(role)) {
            materials = materials.stream()
                    .filter(m -> ApprovalStatus.APPROVED.equals(m.getApprovalStatus()))
                    .collect(Collectors.toList());
        }
        
        return materials.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    public LearningMaterialDTO getMaterialById(UUID materialId, String role) {
        LearningMaterial material = materialRepository.findById(materialId)
                .orElseThrow(() -> new CustomException("Material not found", HttpStatus.NOT_FOUND));
                
        if ("STUDENT".equals(role) && !ApprovalStatus.APPROVED.equals(material.getApprovalStatus())) {
            throw new CustomException("Material is pending approval", HttpStatus.FORBIDDEN);
        }
        
        return mapToDTO(material);
    }

    @Override
    @Transactional
    public LearningMaterialDTO createMaterial(CreateLearningMaterialDTO dto, UUID currentUserId) {
        if (!topicRepository.existsById(dto.getTopicId())) {
            throw new CustomException("Topic not found", HttpStatus.NOT_FOUND);
        }

        LearningMaterial material = LearningMaterial.builder()
                .topicId(dto.getTopicId())
                .title(dto.getTitle())
                .type(dto.getType())
                .contentText(dto.getContentText())
                .sourceCitation(dto.getSourceCitation())
                .version(1)
                .approvalStatus(ApprovalStatus.PENDING) // Needs approval from Instructor/Admin
                .createdBy(currentUserId)
                .createdAt(Instant.now())
                .build();

        // Handle file upload
        if (dto.getFile() != null && !dto.getFile().isEmpty()) {
            org.springframework.web.multipart.MultipartFile file = dto.getFile();
            if (file.getSize() > 50L * 1024 * 1024) {
                throw new CustomException("Kích thước file vượt quá giới hạn 50MB", HttpStatus.BAD_REQUEST);
            }

            String filename = file.getOriginalFilename();
            if (filename != null) {
                String lower = filename.toLowerCase();
                if (lower.endsWith(".exe") || lower.endsWith(".bat") || lower.endsWith(".sh") || lower.endsWith(".cmd") || lower.endsWith(".vbs") || lower.endsWith(".msi")) {
                    throw new CustomException("Định dạng file không được phép", HttpStatus.BAD_REQUEST);
                }
                if (dto.getType() == MaterialType.PDF && !lower.endsWith(".pdf")) {
                    throw new CustomException("File không đúng định dạng PDF", HttpStatus.BAD_REQUEST);
                }
            }

            // Kiểm tra Magic Bytes chống giả mạo đuôi file (MIME Sniffing)
            try {
                byte[] bytes = file.getBytes();
                if (bytes.length >= 2 && bytes[0] == 0x4D && bytes[1] == 0x5A) {
                    throw new CustomException("Nội dung tệp chứa chữ ký thực thi nguy hại (Windows MZ/PE binary)", HttpStatus.BAD_REQUEST);
                }
                if (bytes.length >= 4 && bytes[0] == 0x7F && bytes[1] == 0x45 && bytes[2] == 0x4C && bytes[3] == 0x46) {
                    throw new CustomException("Nội dung tệp chứa chữ ký thực thi nguy hại (Linux ELF binary)", HttpStatus.BAD_REQUEST);
                }
            } catch (java.io.IOException e) {
                throw new CustomException("Không thể đọc nội dung tệp tin tải lên", HttpStatus.BAD_REQUEST);
            }

            String fileUrl = fileStorageService.storeFile(dto.getFile());
            
            FileUpload fileUpload = FileUpload.builder()
                    .uploaderId(currentUserId)
                    .originalFilename(dto.getFile().getOriginalFilename())
                    .storedUrl(fileUrl)
                    .fileSizeBytes(dto.getFile().getSize())
                    .mimeType(dto.getFile().getContentType())
                    .purpose("LEARNING_MATERIAL")
                    .status(FileProcessingStatus.COMPLETED)
                    .createdAt(Instant.now())
                    .build();
            
            fileUpload = fileUploadRepository.save(fileUpload);
            
            material.setFileId(fileUpload.getFileId());
            material.setFileUrl(fileUrl);
        }

        material = materialRepository.save(material);
        return mapToDTO(material);
    }

    @Override
    @Transactional
    public LearningMaterialDTO updateMaterial(UUID materialId, CreateLearningMaterialDTO dto, UUID currentUserId, String role) {
        LearningMaterial material = materialRepository.findById(materialId)
                .orElseThrow(() -> new CustomException("Material not found", HttpStatus.NOT_FOUND));

        if (!"ADMIN".equals(role) && !material.getCreatedBy().equals(currentUserId)) {
            throw new CustomException("You do not have permission to update this material", HttpStatus.FORBIDDEN);
        }

        material.setTitle(dto.getTitle());
        material.setType(dto.getType());
        material.setContentText(dto.getContentText());
        material.setSourceCitation(dto.getSourceCitation());
        material.setVersion(material.getVersion() + 1);
        material.setUpdatedAt(Instant.now());
        material.setApprovalStatus(ApprovalStatus.PENDING); // Needs re-approval after update

        // Handle file update
        if (dto.getFile() != null && !dto.getFile().isEmpty()) {
            String fileUrl = fileStorageService.storeFile(dto.getFile());
            
            FileUpload fileUpload = FileUpload.builder()
                    .uploaderId(currentUserId)
                    .originalFilename(dto.getFile().getOriginalFilename())
                    .storedUrl(fileUrl)
                    .fileSizeBytes(dto.getFile().getSize())
                    .mimeType(dto.getFile().getContentType())
                    .purpose("LEARNING_MATERIAL")
                    .status(FileProcessingStatus.COMPLETED)
                    .createdAt(Instant.now())
                    .build();
            
            fileUpload = fileUploadRepository.save(fileUpload);
            
            material.setFileId(fileUpload.getFileId());
            material.setFileUrl(fileUrl);
        }

        material = materialRepository.save(material);
        return mapToDTO(material);
    }

    @Override
    @Transactional
    public LearningMaterialDTO approveMaterial(UUID materialId) {
        LearningMaterial material = materialRepository.findById(materialId)
                .orElseThrow(() -> new CustomException("Material not found", HttpStatus.NOT_FOUND));
                
        material.setApprovalStatus(ApprovalStatus.APPROVED);
        material.setUpdatedAt(Instant.now());
        
        material = materialRepository.save(material);
        return mapToDTO(material);
    }

    @Override
    @Transactional
    public void deleteMaterial(UUID materialId, UUID currentUserId, String role) {
        LearningMaterial material = materialRepository.findById(materialId)
                .orElseThrow(() -> new CustomException("Material not found", HttpStatus.NOT_FOUND));
                
        if (!"ADMIN".equals(role) && !material.getCreatedBy().equals(currentUserId)) {
            throw new CustomException("You do not have permission to delete this material", HttpStatus.FORBIDDEN);
        }
        
        materialRepository.delete(material);
    }

    private LearningMaterialDTO mapToDTO(LearningMaterial material) {
        return LearningMaterialDTO.builder()
                .materialId(material.getMaterialId())
                .topicId(material.getTopicId())
                .fileId(material.getFileId())
                .title(material.getTitle())
                .type(material.getType())
                .fileUrl(material.getFileUrl())
                .contentText(material.getContentText())
                .version(material.getVersion())
                .approvalStatus(material.getApprovalStatus())
                .sourceCitation(material.getSourceCitation())
                .createdBy(material.getCreatedBy())
                .createdAt(material.getCreatedAt())
                .updatedAt(material.getUpdatedAt())
                .build();
    }
}