package com.shopmanagement.crmservice.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.config.CrmSecurityProperties;
import com.shopmanagement.crmservice.filter.TenantContextFilter;
import com.shopmanagement.crmservice.persistence.entity.CrmTeamMemberEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmTeamMemberRepository;

@ExtendWith(MockitoExtension.class)
class CrmRecordScopeServiceTest {

  @Mock private CrmTeamMemberRepository teamMemberRepository;

  private CrmSecurityProperties properties;
  private CrmRecordScopeService service;

  @BeforeEach
  void setUp() {
    properties = new CrmSecurityProperties();
    properties.setRecordScopeEnabled(true);
    properties.setDefaultScope("ORG");
    service = new CrmRecordScopeService(properties, teamMemberRepository);
    TenantContextFilter.bindTenantForTests("t1");
    TenantContextFilter.bindUserForTests("alice");
  }

  @AfterEach
  void tearDown() {
    TenantContextFilter.clearTenantForTests();
  }

  @Test
  void ownScopeDeniesOtherOwner() {
    // Force OWN via default when no role
    properties.setDefaultScope("OWN");
    assertThrows(ResponseStatusException.class, () -> service.assertCanAccess("bob", "DEFAULT"));
  }

  @Test
  void ownScopeAllowsOwner() {
    properties.setDefaultScope("OWN");
    service.assertCanAccess("alice", "DEFAULT");
  }

  @Test
  void teamScopeAllowsTeammate() {
    properties.setDefaultScope("TEAM");
    CrmTeamMemberEntity member = new CrmTeamMemberEntity();
    member.setActive(true);
    member.setTeamId("DEFAULT");
    member.setUserId("alice");
    when(teamMemberRepository.findByTenantIdAndTeamIdAndUserIdAndDeletedAtIsNull(
            eq("t1"), eq("DEFAULT"), eq("alice")))
        .thenReturn(Optional.of(member));
    service.assertCanAccess("bob", "DEFAULT");
  }

  @Test
  void disabledMeansOrg() {
    properties.setRecordScopeEnabled(false);
    assertEquals(CrmAccessScope.ORG, service.resolveScope());
    service.assertCanAccess("anyone", null);
  }
}
