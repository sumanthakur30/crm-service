package com.shopmanagement.crmservice.security;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.config.CrmSecurityProperties;
import com.shopmanagement.crmservice.filter.TenantContextFilter;
import com.shopmanagement.crmservice.persistence.entity.CrmTeamMemberEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmTeamMemberRepository;
import com.shopmanagement.crmservice.support.TenantIds;

/**
 * Own / Team / Org record scoping for CRM objects that carry {@code ownerUserId} / {@code teamId}.
 *
 * <p>Resolution order when {@code crm.security.record-scope-enabled=true}:
 *
 * <ol>
 *   <li>{@code X-Crm-Access-Scope} header (ORG|TEAM|OWN)
 *   <li>Derived from {@code X-Auth-Role} (owner/admin → ORG, manager → TEAM, else OWN)
 *   <li>{@code crm.security.default-scope}
 * </ol>
 */
@Component
public class CrmRecordScopeService {

  private final CrmSecurityProperties properties;
  private final CrmTeamMemberRepository teamMemberRepository;

  public CrmRecordScopeService(
      CrmSecurityProperties properties, CrmTeamMemberRepository teamMemberRepository) {
    this.properties = properties;
    this.teamMemberRepository = teamMemberRepository;
  }

  public boolean isEnabled() {
    return properties.isRecordScopeEnabled();
  }

  public CrmAccessScope resolveScope() {
    if (!properties.isRecordScopeEnabled()) {
      return CrmAccessScope.ORG;
    }
    String header = TenantContextFilter.getCurrentAccessScope();
    if (header != null && !header.isBlank()) {
      return CrmAccessScope.parse(header);
    }
    String role = TenantContextFilter.getCurrentAuthRole();
    CrmAccessScope fromRole = scopeFromRole(role);
    if (fromRole != null) {
      return fromRole;
    }
    return CrmAccessScope.parse(properties.getDefaultScope());
  }

  /** Deny if the caller cannot see this owned record under current scope. */
  public void assertCanAccess(String ownerUserId, String teamId) {
    if (!properties.isRecordScopeEnabled()) {
      return;
    }
    CrmAccessScope scope = resolveScope();
    if (scope == CrmAccessScope.ORG) {
      return;
    }
    String user = TenantIds.currentUserOrNull();
    if (user == null || user.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "CRM record scope requires X-User-Id when scope is " + scope);
    }
    if (Objects.equals(user, ownerUserId)) {
      return;
    }
    if (scope == CrmAccessScope.OWN) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to access this record (OWN scope)");
    }
    // TEAM
    if (teamId != null && !teamId.isBlank() && isMemberOfTeam(TenantIds.require(), teamId, user)) {
      return;
    }
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to access this record (TEAM scope)");
  }

  /**
   * List filter helpers. When scope is ORG, returns nulls (no extra filter). When OWN, forces owner.
   * When TEAM, returns current user + team ids they belong to (empty team list → own-only).
   */
  public ScopeListFilter listFilter(String requestedOwnerUserId) {
    if (!properties.isRecordScopeEnabled()) {
      return new ScopeListFilter(CrmAccessScope.ORG, requestedOwnerUserId, null, List.of());
    }
    CrmAccessScope scope = resolveScope();
    if (scope == CrmAccessScope.ORG) {
      return new ScopeListFilter(scope, requestedOwnerUserId, null, List.of());
    }
    String user = TenantIds.currentUserOrNull();
    if (user == null || user.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "CRM list under " + scope + " scope requires X-User-Id");
    }
    if (scope == CrmAccessScope.OWN) {
      return new ScopeListFilter(scope, user, user, List.of());
    }
    List<String> teamIds = teamIdsForUser(TenantIds.require(), user);
    return new ScopeListFilter(scope, user, user, teamIds);
  }

  public record ScopeListFilter(
      CrmAccessScope scope, String ownerUserId, String scopeUserId, List<String> scopeTeamIds) {

    public boolean appliesTeamOrOwn() {
      return scope == CrmAccessScope.TEAM || scope == CrmAccessScope.OWN;
    }
  }

  private boolean isMemberOfTeam(String tenantId, String teamId, String userId) {
    return teamMemberRepository
        .findByTenantIdAndTeamIdAndUserIdAndDeletedAtIsNull(tenantId, teamId, userId)
        .filter(CrmTeamMemberEntity::isActive)
        .isPresent();
  }

  private List<String> teamIdsForUser(String tenantId, String userId) {
    return teamMemberRepository.findByTenantIdAndUserIdAndActiveTrueAndDeletedAtIsNull(tenantId, userId).stream()
        .map(CrmTeamMemberEntity::getTeamId)
        .filter(Objects::nonNull)
        .distinct()
        .collect(Collectors.toList());
  }

  private static CrmAccessScope scopeFromRole(String role) {
    if (role == null || role.isBlank()) {
      return null;
    }
    String r = role.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    if (r.contains("OWNER")
        || r.contains("ADMIN")
        || r.contains("SUPER")
        || r.equals("ORG_ADMIN")
        || r.equals("SHOP_OWNER")) {
      return CrmAccessScope.ORG;
    }
    if (r.contains("MANAGER") || r.contains("TEAM_LEAD") || r.contains("LEADER")) {
      return CrmAccessScope.TEAM;
    }
    if (r.contains("EXEC") || r.contains("SALES") || r.contains("REP") || r.contains("AGENT")) {
      return CrmAccessScope.OWN;
    }
    return null;
  }
}
