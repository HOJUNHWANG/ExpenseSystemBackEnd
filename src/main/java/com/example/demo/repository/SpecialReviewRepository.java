package com.example.demo.repository;

import com.example.demo.domain.SpecialReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpecialReviewRepository extends JpaRepository<SpecialReview, Long> {
    Optional<SpecialReview> findByReportId(Long reportId);
}
