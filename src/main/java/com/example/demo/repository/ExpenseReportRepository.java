package com.example.demo.repository;

import com.example.demo.domain.ExpenseReport;
import com.example.demo.domain.ExpenseReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExpenseReportRepository extends JpaRepository<ExpenseReport, Long> {

    // For searching the report that submitted by certain User
    List<ExpenseReport> findBySubmitterId(Long submitterId);
    List<ExpenseReport> findBySubmitterIdAndStatus(Long submitterId, ExpenseReportStatus status);
    List<ExpenseReport> findByStatus(ExpenseReportStatus status);

    // Paginated versions
    Page<ExpenseReport> findBySubmitterId(Long submitterId, Pageable pageable);
    Page<ExpenseReport> findBySubmitterIdAndStatus(Long submitterId, ExpenseReportStatus status, Pageable pageable);
    Page<ExpenseReport> findByStatus(ExpenseReportStatus status, Pageable pageable);

    @Query("""
        select r from ExpenseReport r
        where (:submitterId is null or r.submitter.id = :submitterId)
          and (:q is null or :q = '' or lower(r.title) like lower(concat('%', :q, '%')))
          and (:status is null or r.status = :status)
          and (:minTotal is null or r.totalAmount >= :minTotal)
          and (:maxTotal is null or r.totalAmount <= :maxTotal)
        order by r.createdAt desc
    """)
    List<ExpenseReport> search(
            @Param("submitterId") Long submitterId,
            @Param("q") String q,
            @Param("status") ExpenseReportStatus status,
            @Param("minTotal") Double minTotal,
            @Param("maxTotal") Double maxTotal
    );

    @Query("""
        select r from ExpenseReport r
        where (:submitterId is null or r.submitter.id = :submitterId)
          and (:q is null or :q = '' or lower(r.title) like lower(concat('%', :q, '%')))
          and (:status is null or r.status = :status)
          and (:minTotal is null or r.totalAmount >= :minTotal)
          and (:maxTotal is null or r.totalAmount <= :maxTotal)
    """)
    Page<ExpenseReport> searchPaged(
            @Param("submitterId") Long submitterId,
            @Param("q") String q,
            @Param("status") ExpenseReportStatus status,
            @Param("minTotal") Double minTotal,
            @Param("maxTotal") Double maxTotal,
            Pageable pageable
    );

    @Query("""
        select r from ExpenseReport r
        where (:submitterId is null or r.submitter.id = :submitterId)
        order by coalesce(r.approvedAt, r.createdAt) desc
    """)
    List<ExpenseReport> recentActivity(@Param("submitterId") Long submitterId);
}
