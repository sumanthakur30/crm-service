package com.shopmanagement.crmservice.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.shopmanagement.crmservice.payment.PaymentLinkProvider;
import com.shopmanagement.crmservice.payment.StubPaymentLinkProvider;

@Configuration
public class PaymentProviderConfig {

  /**
   * Ensures a provider exists even if a non-STUB {@code crm.payment.provider} is set before a real
   * adapter is registered.
   */
  @Bean
  @ConditionalOnMissingBean(PaymentLinkProvider.class)
  PaymentLinkProvider fallbackStubPaymentLinkProvider(CrmPaymentProperties properties) {
    return new StubPaymentLinkProvider(properties);
  }
}
