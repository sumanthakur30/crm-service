package com.shopmanagement.crmservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shopmanagement.crmservice.api.CrmLeadApi.LeadResponse;
import com.shopmanagement.crmservice.persistence.entity.CrmInboundEventEntity;

@ExtendWith(MockitoExtension.class)
class CapturePipeTest {

  @Mock private InboundIdempotencyService idempotency;
  @Mock private CaptureJobService jobs;
  @Mock private CrmLeadService leadService;
  @InjectMocks private CapturePipe pipe;

  @Test
  void sameIdempotencyKeyCreatesOneTouch() {
    CrmInboundEventEntity event = new CrmInboundEventEntity();
    event.setId(4L);
    LeadResponse created = mock(LeadResponse.class);
    when(created.id()).thenReturn(9L);
    when(idempotency.find("same")).thenAnswer(invocation ->
        event.getLeadId() == null ? Optional.empty() : Optional.of(event));
    when(idempotency.insertReceived(eq("T"), eq("same"), any(), nullable(Long.class), nullable(Long.class)))
        .thenReturn(event);
    when(leadService.get(9L)).thenReturn(created);
    AtomicInteger calls = new AtomicInteger();
    Function<CrmInboundEventEntity, LeadResponse> create =
        ignored -> {
          calls.incrementAndGet();
          event.setLeadId(9L);
          event.setStatus("MAPPED");
          return created;
        };

    pipe.run("T", "same", Map.of("phone", "9999999999"), null, null, create);
    pipe.run("T", "same", Map.of("phone", "9999999999"), null, null, create);

    assertEquals(1, calls.get());
  }
}
