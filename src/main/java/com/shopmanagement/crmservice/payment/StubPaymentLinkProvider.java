package com.shopmanagement.crmservice.payment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.shopmanagement.crmservice.config.CrmPaymentProperties;
import com.shopmanagement.crmservice.persistence.entity.CrmQuotationEntity;

@Component
@ConditionalOnProperty(name = "crm.payment.provider", havingValue = "STUB", matchIfMissing = true)
public class StubPaymentLinkProvider implements PaymentLinkProvider {

  private final CrmPaymentProperties properties;

  public StubPaymentLinkProvider(CrmPaymentProperties properties) {
    this.properties = properties;
  }

  @Override
  public PaymentLinkResult createPaymentLink(CrmQuotationEntity quote, String tenantId) {
    String ref = "CRM-" + quote.getQuoteNumber() + "-" + quote.getId();
    String base = properties.getLinkBaseUrl() == null ? "" : properties.getLinkBaseUrl().trim();
    if (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    String url =
        base
            + "/"
            + tenantId
            + "/"
            + quote.getQuoteNumber()
            + "?amount="
            + quote.getTotalAmount()
            + "&ref="
            + ref;
    String provider =
        properties.getProvider() == null || properties.getProvider().isBlank()
            ? "STUB"
            : properties.getProvider().trim();
    return new PaymentLinkResult(url, ref, provider, quote.getTotalAmount());
  }
}
