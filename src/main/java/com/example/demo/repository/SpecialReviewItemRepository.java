package com.example.demo.repository;

import com.example.demo.domain.SpecialReviewItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpecialReviewItemRepository extends JpaRepository<SpecialReviewItem, Long> {
}
