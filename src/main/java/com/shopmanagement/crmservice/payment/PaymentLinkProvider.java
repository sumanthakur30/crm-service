package com.shopmanagement.crmservice.payment;

import com.shopmanagement.crmservice.persistence.entity.CrmQuotationEntity;

/**
 * Pluggable payment-link adapter. Default {@link StubPaymentLinkProvider} when {@code
 * crm.payment.provider=STUB}. Razorpay/UPI implementations can be added later without changing
 * quotation APIs.
 */
public interface PaymentLinkProvider {

  PaymentLinkResult createPaymentLink(CrmQuotationEntity quote, String tenantId);
}
