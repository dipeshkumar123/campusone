package com.campusone.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments", uniqueConstraints = {
    @UniqueConstraint(columnNames = "receipt_no"),
    @UniqueConstraint(columnNames = "reference")
})
@Getter
@Setter
@NoArgsConstructor
public class Payment {
    public enum Method { UPI, CARD, NETBANKING }
    public enum Status { SUCCESS }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fee_account_id")
    private FeeAccount feeAccount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Method method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.SUCCESS;

    @Column(name = "receipt_no", nullable = false, length = 40)
    private String receiptNo;

    @Column(nullable = false, length = 80)
    private String reference;

    @Column(nullable = false)
    private Instant paidAt = Instant.now();

    @Column(length = 120)
    private String recordedBy;

    public Payment(FeeAccount feeAccount, BigDecimal amount, Method method, String receiptNo, String reference,
                   String recordedBy) {
        this.feeAccount = feeAccount;
        this.amount = amount;
        this.method = method;
        this.receiptNo = receiptNo;
        this.reference = reference;
        this.recordedBy = recordedBy;
    }
}
