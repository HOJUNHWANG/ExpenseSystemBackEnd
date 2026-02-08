package com.example.demo.dto;

import lombok.Data;

@Data
public class ApprovalRequest {

    private Long approverId;   // 누가 승인/반려하는지
    private String comment;    // (선택) 메모, 이유 등
}
