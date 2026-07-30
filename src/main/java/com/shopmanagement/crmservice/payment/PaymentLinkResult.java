package com.shopmanagement.crmservice.payment;

import java.math.BigDecimal;

/** Result of creating a payment link for a CRM quotation. */
public record PaymentLinkResult(String url, String ref, String provider, BigDecimal amount) {}
