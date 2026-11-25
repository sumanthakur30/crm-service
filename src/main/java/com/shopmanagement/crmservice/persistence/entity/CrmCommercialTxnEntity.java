package com.shopmanagement.crmservice.persistence.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "crm_commercial_txn")
@Getter
@Setter
public class CrmCommercialTxnEntity extends TenantAuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "placement_id", nullable = false)
  private Long placementId;

  @Column(name = "consultant_id")
  private Long consultantId;

  @Column(nullable = false, length = 32)
  private String ledger;

  @Column(name = "txn_kind", nullable = false, length = 32)
  private String txnKind;

  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal amount;

  @Column(length = 32)
  private String counterparty;

  @Column(name = "reference_no", length = 128)
  private String referenceNo;

  @Column(name = "txn_date", nullable = false)
  private LocalDate txnDate;

  @Column(length = 500)
  private String note;
}
