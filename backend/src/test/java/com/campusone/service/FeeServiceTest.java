package com.campusone.service;

import com.campusone.api.dto.FeeDtos;
import com.campusone.common.ApiException;
import com.campusone.domain.FeeAccount;
import com.campusone.domain.Payment;
import com.campusone.repo.FeeAccountRepository;
import com.campusone.repo.UserRepository;
import com.campusone.support.TestSecurity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FeeServiceTest {
    @Autowired
    private FeeService feeService;
    @Autowired
    private FeeAccountRepository feeAccountRepository;
    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        TestSecurity.signInAs(userRepository.findByEmailIgnoreCase("admin@campusone.demo").orElseThrow());
    }

    @AfterEach
    void tearDown() {
        TestSecurity.clear();
    }

    @Test
    void paymentIsIdempotentAndReceiptIsStable() {
        FeeDtos.FeeRow row = feeService.list(null, null, null).rows().stream()
                .filter(r -> r.dueAmount().signum() > 0)
                .findFirst().orElseThrow();
        BigDecimal initialDue = row.dueAmount();
        BigDecimal amount = initialDue.min(new BigDecimal("1000.00"));
        String reference = UUID.randomUUID().toString();

        FeeDtos.PaymentView first = feeService.pay(row.studentId(), new FeeDtos.PayRequest(
                amount, Payment.Method.UPI, reference));
        FeeDtos.PaymentView second = feeService.pay(row.studentId(), new FeeDtos.PayRequest(
                amount, Payment.Method.UPI, reference));

        assertThat(first.duplicate()).isFalse();
        assertThat(second.duplicate()).isTrue();
        assertThat(second.receiptNo()).isEqualTo(first.receiptNo());
        assertThat(second.reference()).isEqualTo(reference);

        FeeAccount account = feeAccountRepository.findByStudentId(row.studentId()).orElseThrow();
        assertThat(account.getDueAmount()).isEqualByComparingTo(initialDue.subtract(amount));
    }

    @Test
    void overpaymentIsRejected() {
        FeeDtos.FeeRow row = feeService.list(null, null, null).rows().stream()
                .filter(r -> r.dueAmount().signum() > 0)
                .findFirst().orElseThrow();

        assertThatThrownBy(() -> feeService.pay(row.studentId(), new FeeDtos.PayRequest(
                row.dueAmount().add(BigDecimal.ONE), Payment.Method.CARD, UUID.randomUUID().toString())))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("more than the outstanding amount");
    }

    @Test
    void facultyCannotReadOrChangeFeeRecords() {
        TestSecurity.signInAs(userRepository.findByEmailIgnoreCase("faculty@campusone.demo").orElseThrow());

        assertThatThrownBy(() -> feeService.list(null, null, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("limited to the office");

        FeeAccount account = feeAccountRepository.findAllByOrderByDueAmountDesc().stream()
                .filter(a -> a.getDueAmount().signum() > 0)
                .findFirst().orElseThrow();

        assertThatThrownBy(() -> feeService.pay(account.getStudent().getId(), new FeeDtos.PayRequest(
                BigDecimal.TEN, Payment.Method.UPI, UUID.randomUUID().toString())))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("office administrators");
    }

    @Test
    void tallyExportOnlyCoversRecordedPayments() {
        String csv = feeService.tallyCsv();

        assertThat(csv).startsWith("\uFEFF");
        assertThat(csv).contains("VOUCHER_DATE,VOUCHER_NO");
        assertThat(csv).contains("RECEIPT_NO");
    }
}
