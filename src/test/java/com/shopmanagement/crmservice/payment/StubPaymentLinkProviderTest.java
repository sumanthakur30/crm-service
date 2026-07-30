package com.shopmanagement.crmservice.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.shopmanagement.crmservice.config.CrmPaymentProperties;
import com.shopmanagement.crmservice.persistence.entity.CrmQuotationEntity;

class StubPaymentLinkProviderTest {

  @Test
  void buildsStubUrlWithRefAndAmount() {
    CrmPaymentProperties props = new CrmPaymentProperties();
    props.setLinkBaseUrl("http://localhost:4500/pay/");
    props.setProvider("STUB");
    StubPaymentLinkProvider provider = new StubPaymentLinkProvider(props);

    CrmQuotationEntity quote = new CrmQuotationEntity();
    quote.setId(12L);
    quote.setQuoteNumber("Q-100");
    quote.setTotalAmount(new BigDecimal("1500.00"));

    PaymentLinkResult link = provider.createPaymentLink(quote, "42");

    assertThat(link.provider()).isEqualTo("STUB");
    assertThat(link.ref()).isEqualTo("CRM-Q-100-12");
    assertThat(link.amount()).isEqualByComparingTo("1500.00");
    assertThat(link.url())
        .isEqualTo("http://localhost:4500/pay/42/Q-100?amount=1500.00&ref=CRM-Q-100-12");
  }
}
