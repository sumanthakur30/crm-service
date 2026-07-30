package com.shopmanagement.crmservice.persistence.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "crm_quotation")
@Getter
@Setter
public class CrmQuotationEntity extends TenantAuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "opportunity_id", nullable = false)
  private Long opportunityId;

  @Column(name = "quote_number", nullable = false, length = 64)
  private String quoteNumber;

  @Column(name = "version_no", nullable = false)
  private int versionNo = 1;

  @Column(nullable = false, length = 32)
  private String status = "DRAFT";

  @Column(name = "customer_name", length = 256)
  private String customerName;

  @Column(name = "customer_gstin", length = 20)
  private String customerGstin;

  @Column(name = "place_of_supply", length = 64)
  private String placeOfSupply;

  @Column(name = "seller_state_code", length = 8)
  private String sellerStateCode;

  @Column(name = "buyer_state_code", length = 8)
  private String buyerStateCode;

  @Column(nullable = false, length = 8)
  private String currency = "INR";

  @Column(name = "taxable_amount", nullable = false, precision = 18, scale = 2)
  private BigDecimal taxableAmount = BigDecimal.ZERO;

  @Column(name = "cgst_amount", nullable = false, precision = 18, scale = 2)
  private BigDecimal cgstAmount = BigDecimal.ZERO;

  @Column(name = "sgst_amount", nullable = false, precision = 18, scale = 2)
  private BigDecimal sgstAmount = BigDecimal.ZERO;

  @Column(name = "igst_amount", nullable = false, precision = 18, scale = 2)
  private BigDecimal igstAmount = BigDecimal.ZERO;

  @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
  private BigDecimal totalAmount = BigDecimal.ZERO;

  @Column(name = "discount_amount", nullable = false, precision = 18, scale = 2)
  private BigDecimal discountAmount = BigDecimal.ZERO;

  @Column(columnDefinition = "text")
  private String terms;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "lines_json", nullable = false, columnDefinition = "jsonb")
  private List<Map<String, Object>> linesJson = new ArrayList<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "share_payload_json", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> sharePayloadJson = new LinkedHashMap<>();

  @Column(name = "valid_until")
  private LocalDate validUntil;

  @Column(name = "accepted_at")
  private Instant acceptedAt;

  @Column(name = "payment_link_url", length = 512)
  private String paymentLinkUrl;

  @Column(name = "payment_status", nullable = false, length = 32)
  private String paymentStatus = "NONE";

  @Column(name = "payment_provider", length = 32)
  private String paymentProvider;

  @Column(name = "payment_ref", length = 128)
  private String paymentRef;

  @Column(name = "payment_amount", precision = 18, scale = 2)
  private BigDecimal paymentAmount;

  @Column(name = "paid_at")
  private Instant paidAt;
}
