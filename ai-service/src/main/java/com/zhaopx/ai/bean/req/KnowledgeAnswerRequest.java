package com.zhaopx.ai.bean.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class KnowledgeAnswerRequest {

    @NotBlank(message = "question 不能为空")
    private String question;

    @NotEmpty(message = "systemCodes 不能为空")
    private List<String> systemCodes;

    private String knowledgeBaseId;

    private Integer topK;

    private Integer numCandidates;
}
