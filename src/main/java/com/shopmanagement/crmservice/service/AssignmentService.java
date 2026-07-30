package com.shopmanagement.crmservice.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.api.CrmLeadApi.AssignRequest;
import com.shopmanagement.crmservice.api.CrmLeadApi.LeadResponse;
import com.shopmanagement.crmservice.api.CrmLeadApi.TeamMemberRequest;
import com.shopmanagement.crmservice.api.CrmLeadApi.TeamMemberResponse;
import com.shopmanagement.crmservice.persistence.entity.CrmLeadEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmRrCursorEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmTeamMemberEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmLeadRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmRrCursorRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmTeamMemberRepository;
import com.shopmanagement.crmservice.support.TenantIds;

@Service
public class AssignmentService {

  private final CrmTeamMemberRepository memberRepository;
  private final CrmRrCursorRepository cursorRepository;
  private final CrmLeadRepository leadRepository;
  private final CrmLeadService leadService;
  private final TimelineService timelineService;

  public AssignmentService(
      CrmTeamMemberRepository memberRepository,
      CrmRrCursorRepository cursorRepository,
      CrmLeadRepository leadRepository,
      CrmLeadService leadService,
      TimelineService timelineService) {
    this.memberRepository = memberRepository;
    this.cursorRepository = cursorRepository;
    this.leadRepository = leadRepository;
    this.leadService = leadService;
    this.timelineService = timelineService;
  }

  @Transactional
  public TeamMemberResponse upsertMember(TeamMemberRequest body) {
    String tenantId = TenantIds.require();
    String teamId = blankOr(body.teamId(), "DEFAULT");
    String userId = requireUserId(body.userId());

    CrmTeamMemberEntity member =
        memberRepository
            .findByTenantIdAndTeamIdAndUserIdAndDeletedAtIsNull(tenantId, teamId, userId)
            .orElseGet(CrmTeamMemberEntity::new);

    if (member.getId() == null) {
      member.setTenantId(tenantId);
      member.setTeamId(teamId);
      member.setUserId(userId);
    }
    member.setDisplayName(blankToNull(body.displayName()));
    member.setActive(body.active() == null || body.active());
    member.setSortOrder(body.sortOrder() == null ? 0 : body.sortOrder());
    member.touch();
    return toMember(memberRepository.save(member));
  }

  @Transactional(readOnly = true)
  public List<TeamMemberResponse> listMembers(String teamId) {
    String tenantId = TenantIds.require();
    String team = blankOr(teamId, "DEFAULT");
    return memberRepository
        .findByTenantIdAndTeamIdAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAscIdAsc(tenantId, team)
        .stream()
        .map(AssignmentService::toMember)
        .toList();
  }

  @Transactional
  public void deactivateMember(Long id) {
    String tenantId = TenantIds.require();
    CrmTeamMemberEntity member =
        memberRepository
            .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
    member.setActive(false);
    member.touch();
    memberRepository.save(member);
  }

  @Transactional
  public LeadResponse assign(Long leadId, AssignRequest body) {
    String tenantId = TenantIds.require();
    CrmLeadEntity lead =
        leadRepository
            .findByTenantIdAndIdAndDeletedAtIsNull(tenantId, leadId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found"));

    String mode = body.mode() == null ? "MANUAL" : body.mode().trim().toUpperCase(Locale.ROOT);
    String teamId = blankOr(body.teamId(), blankOr(lead.getTeamId(), "DEFAULT"));

    if ("ROUND_ROBIN".equals(mode)) {
      String owner = nextRoundRobinOwner(tenantId, teamId);
      lead.setOwnerUserId(owner);
      lead.setTeamId(teamId);
    } else if ("MANUAL".equals(mode)) {
      if (body.ownerUserId() == null || body.ownerUserId().isBlank()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ownerUserId required for MANUAL assign");
      }
      lead.setOwnerUserId(body.ownerUserId().trim());
      lead.setTeamId(teamId);
    } else {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mode must be MANUAL or ROUND_ROBIN");
    }
    lead.touch();
    leadRepository.save(lead);
    timelineService.recordEvent(
        "LEAD",
        leadId,
        "ASSIGNED",
        "Assigned to " + lead.getOwnerUserId() + " (" + mode + ")",
        Map.of("mode", mode, "ownerUserId", lead.getOwnerUserId(), "teamId", teamId));
    return leadService.get(leadId);
  }

  private String nextRoundRobinOwner(String tenantId, String teamId) {
    List<CrmTeamMemberEntity> members = memberRepository.findActiveForUpdate(tenantId, teamId);
    if (members.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "No active team members for round-robin team=" + teamId);
    }

    CrmRrCursorEntity cursor =
        cursorRepository
            .findForUpdate(tenantId, teamId)
            .orElseGet(
                () -> {
                  CrmRrCursorEntity c = new CrmRrCursorEntity();
                  c.setTenantId(tenantId);
                  c.setTeamId(teamId);
                  c.setLastIndex(-1);
                  return c;
                });

    int next = (cursor.getLastIndex() + 1) % members.size();
    cursor.setLastIndex(next);
    cursor.setUpdatedAt(Instant.now());
    cursorRepository.save(cursor);
    return members.get(next).getUserId();
  }

  private static TeamMemberResponse toMember(CrmTeamMemberEntity m) {
    return new TeamMemberResponse(
        m.getId(), m.getTeamId(), m.getUserId(), m.getDisplayName(), m.isActive(), m.getSortOrder());
  }

  private static String requireUserId(String userId) {
    if (userId == null || userId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId is required");
    }
    return userId.trim();
  }

  private static String blankOr(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
