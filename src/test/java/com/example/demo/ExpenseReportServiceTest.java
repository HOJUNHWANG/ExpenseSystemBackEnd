package com.example.demo;

import com.example.demo.domain.*;
import com.example.demo.dto.ApprovalRequest;
import com.example.demo.dto.ExpenseItemCreateRequest;
import com.example.demo.dto.ExpenseReportCreateRequest;
import com.example.demo.dto.SubmitRequest;
import com.example.demo.repository.AuditLogRepository;
import com.example.demo.repository.ExpenseReportRepository;
import com.example.demo.repository.SpecialReviewRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.ExpenseReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Mockito-based unit tests for ExpenseReportService — no Spring context.
 */
@ExtendWith(MockitoExtension.class)
class ExpenseReportServiceTest {

    @Mock ExpenseReportRepository expenseReportRepository;
    @Mock SpecialReviewRepository specialReviewRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock UserRepository userRepository;

    @InjectMocks ExpenseReportService service;

    // ── helpers ─────────────────────────────────────────────────────────────

    private User employee(long id) {
        return User.builder().id(id).name("Employee " + id).email("emp" + id + "@test.com").role("EMPLOYEE").build();
    }

    private User manager(long id) {
        return User.builder().id(id).name("Manager").email("manager@test.com").role("MANAGER").build();
    }

    private User cfo(long id) {
        return User.builder().id(id).name("CFO").email("cfo@test.com").role("CFO").build();
    }

    private ExpenseReport draftReport(long reportId, User submitter) {
        ExpenseReport r = ExpenseReport.builder()
                .id(reportId)
                .title("Test Report")
                .submitter(submitter)
                .status(ExpenseReportStatus.DRAFT)
                .build();
        // Add one item so the report is non-empty
        ExpenseItem item = ExpenseItem.builder()
                .id(null)
                .date(LocalDate.now())
                .description("Travel")
                .amount(new BigDecimal("50.00"))
                .category("Travel")
                .build();
        r.getItems().add(item);
        return r;
    }

    // ── tests ────────────────────────────────────────────────────────────────

    @Test
    void createReport_savesAndReturnsId() {
        User submitter = employee(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(submitter));

        ExpenseReport saved = ExpenseReport.builder().id(42L).submitter(submitter).title("Test").build();
        when(expenseReportRepository.save(any())).thenReturn(saved);

        ExpenseItemCreateRequest itemReq = new ExpenseItemCreateRequest();
        itemReq.setDate(LocalDate.now());
        itemReq.setDescription("Flight");
        itemReq.setAmount(new BigDecimal("200.00"));
        itemReq.setCategory("Airfare");

        ExpenseReportCreateRequest request = new ExpenseReportCreateRequest();
        request.setSubmitterId(1L);
        request.setTitle("Test Report");
        request.setItems(List.of(itemReq));

        Long id = service.createReport(request);

        assertThat(id).isEqualTo(42L);
        verify(expenseReportRepository).save(any());
    }

    @Test
    void submitReport_transitionsStatus() {
        User submitter = employee(1L);
        ExpenseReport report = draftReport(10L, submitter);

        when(expenseReportRepository.findById(10L)).thenReturn(Optional.of(report));
        when(specialReviewRepository.findByReportId(10L)).thenReturn(Optional.empty());
        when(expenseReportRepository.save(any())).thenReturn(report);

        SubmitRequest req = new SubmitRequest();
        req.setSubmitterId(1L);

        ExpenseReportStatus result = service.submitReport(10L, req);

        // EMPLOYEE → MANAGER_REVIEW (no policy warnings for a $50 Travel item)
        assertThat(result).isEqualTo(ExpenseReportStatus.MANAGER_REVIEW);
    }

    @Test
    void approveReport_selfApprovalThrows() {
        User submitter = employee(1L);
        ExpenseReport report = ExpenseReport.builder()
                .id(10L)
                .submitter(submitter)
                .status(ExpenseReportStatus.MANAGER_REVIEW)
                .build();

        when(expenseReportRepository.findById(10L)).thenReturn(Optional.of(report));
        // Return the exact same object reference — identity equals → self-approval
        when(userRepository.findById(1L)).thenReturn(Optional.of(submitter));

        ApprovalRequest req = new ApprovalRequest();
        req.setApproverId(1L);

        assertThrows(IllegalStateException.class, () -> service.approveReport(10L, req));
    }

    @Test
    void approveReport_wrongRoleThrows() {
        User submitter = employee(1L);
        User cfo = cfo(2L);
        ExpenseReport report = ExpenseReport.builder()
                .id(10L)
                .submitter(submitter)
                .status(ExpenseReportStatus.MANAGER_REVIEW) // requires MANAGER, not CFO
                .build();

        when(expenseReportRepository.findById(10L)).thenReturn(Optional.of(report));
        when(userRepository.findById(2L)).thenReturn(Optional.of(cfo));

        ApprovalRequest req = new ApprovalRequest();
        req.setApproverId(2L);

        assertThrows(IllegalStateException.class, () -> service.approveReport(10L, req));
    }

    @Test
    void deleteDraft_onlySubmitterSucceeds() {
        User submitter = employee(1L);
        ExpenseReport report = draftReport(10L, submitter);

        when(expenseReportRepository.findById(10L)).thenReturn(Optional.of(report));
        when(specialReviewRepository.findByReportId(10L)).thenReturn(Optional.empty());

        service.deleteDraft(10L, 1L);

        verify(expenseReportRepository).delete(report);
    }

    @Test
    void deleteDraft_nonDraftThrows() {
        User submitter = employee(1L);
        ExpenseReport report = ExpenseReport.builder()
                .id(10L)
                .submitter(submitter)
                .status(ExpenseReportStatus.MANAGER_REVIEW) // not DRAFT
                .build();

        when(expenseReportRepository.findById(10L)).thenReturn(Optional.of(report));

        assertThrows(IllegalStateException.class, () -> service.deleteDraft(10L, 1L));
        verify(expenseReportRepository, never()).delete(any());
    }
}
