package com.shopmanagement.crmservice.persistence.entity;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "crm_usage_counter")
@Getter
@Setter
public class CrmUsageCounterEntity {

  @EmbeddedId private Pk id = new Pk();

  @Column(name = "used_count", nullable = false)
  private long usedCount;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @Embeddable
  @Getter
  @Setter
  public static class Pk implements Serializable {
    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "meter_code", nullable = false, length = 32)
    private String meterCode;

    @Column(name = "period_key", nullable = false, length = 16)
    private String periodKey;

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Pk pk)) {
        return false;
      }
      return Objects.equals(tenantId, pk.tenantId)
          && Objects.equals(meterCode, pk.meterCode)
          && Objects.equals(periodKey, pk.periodKey);
    }

    @Override
    public int hashCode() {
      return Objects.hash(tenantId, meterCode, periodKey);
    }
  }
}
