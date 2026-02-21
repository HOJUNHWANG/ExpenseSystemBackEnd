package com.example.demo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmitRequest {
    @NotNull
    private Long submitterId;
    private List<WarningReason> reasons;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WarningReason {
        public String code;
        public String reason;
    }
}
