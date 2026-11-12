package com.campusone.repo;

import com.campusone.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByFeeAccountIdOrderByPaidAtDesc(Long feeAccountId);
    List<Payment> findTop8ByOrderByPaidAtDesc();
    Optional<Payment> findByReference(String reference);
    boolean existsByReference(String reference);
    boolean existsByReceiptNo(String receiptNo);
}
