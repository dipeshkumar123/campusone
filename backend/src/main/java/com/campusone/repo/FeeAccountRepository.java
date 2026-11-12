package com.campusone.repo;

import com.campusone.domain.FeeAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface FeeAccountRepository extends JpaRepository<FeeAccount, Long>, JpaSpecificationExecutor<FeeAccount> {
    Optional<FeeAccount> findByStudentId(Long studentId);
    List<FeeAccount> findByStudentIdIn(List<Long> studentIds);
    List<FeeAccount> findAllByOrderByDueAmountDesc();
}
