package com.shopmanagement.crmservice.persistence.entity;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "crm_rr_cursor")
@Getter
@Setter
@IdClass(CrmRrCursorEntity.Pk.class)
public class CrmRrCursorEntity {

  @Id
  @Column(name = "tenant_id", length = 64)
  private String tenantId;

  @Id
  @Column(name = "team_id", length = 64)
  private String teamId = "DEFAULT";

  @Column(name = "last_index", nullable = false)
  private int lastIndex = -1;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @NoArgsConstructor
  @AllArgsConstructor
  @EqualsAndHashCode
  public static class Pk implements Serializable {
    private String tenantId;
    private String teamId;
  }
}
