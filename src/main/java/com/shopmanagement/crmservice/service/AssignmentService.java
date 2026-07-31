package com.shopmanagement.crmservice.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
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
  private final UsageMeterService usageMeterService;

  public AssignmentService(
      CrmTeamMemberRepository memberRepository,
      CrmRrCursorRepository cursorRepository,
      CrmLeadRepository leadRepository,
      CrmLeadService leadService,
      TimelineService timelineService,
      UsageMeterService usageMeterService) {
    this.memberRepository = memberRepository;
    this.cursorRepository = cursorRepository;
    this.leadRepository = leadRepository;
    this.leadService = leadService;
    this.timelineService = timelineService;
    this.usageMeterService = usageMeterService;
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
      usageMeterService.assertCanAddSeat();
      member.setTenantId(tenantId);
      member.setTeamId(teamId);
      member.setUserId(userId);
    }
    member.setDisplayName(blankToNull(body.displayName()));
    member.setActive(body.active() == null || body.active());
    member.setSortOrder(body.sortOrder() == null ? 0 : body.sortOrder());
    member.setStateCodes(body.stateCodes() == null ? "" : body.stateCodes().trim());
    member.setPincodePrefixes(body.pincodePrefixes() == null ? "" : body.pincodePrefixes().trim());
    member.setOpenLeadCap(body.openLeadCap() == null ? 0 : body.openLeadCap());
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
      String owner = nextRoundRobinOwner(tenantId, teamId, null);
      lead.setOwnerUserId(owner);
      lead.setTeamId(teamId);
    } else if ("GEO".equals(mode)) {
      List<CrmTeamMemberEntity> members = memberRepository.findActiveForUpdate(tenantId, teamId);
      List<CrmTeamMemberEntity> filtered = filterByGeo(members, lead);
      if (filtered.isEmpty()) {
        filtered = members;
      }
      String owner = nextRoundRobinOwner(tenantId, teamId, filtered);
      lead.setOwnerUserId(owner);
      lead.setTeamId(teamId);
    } else if ("WORKLOAD".equals(mode)) {
      String owner = pickByWorkload(tenantId, teamId);
      lead.setOwnerUserId(owner);
      lead.setTeamId(teamId);
    } else if ("MANUAL".equals(mode)) {
      if (body.ownerUserId() == null || body.ownerUserId().isBlank()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ownerUserId required for MANUAL assign");
      }
      lead.setOwnerUserId(body.ownerUserId().trim());
      lead.setTeamId(teamId);
    } else {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "mode must be MANUAL, ROUND_ROBIN, GEO, or WORKLOAD");
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

  private String nextRoundRobinOwner(
      String tenantId, String teamId, List<CrmTeamMemberEntity> poolOverride) {
    List<CrmTeamMemberEntity> members =
        poolOverride != null ? poolOverride : memberRepository.findActiveForUpdate(tenantId, teamId);
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

  private String pickByWorkload(String tenantId, String teamId) {
    List<CrmTeamMemberEntity> members = memberRepository.findActiveForUpdate(tenantId, teamId);
    if (members.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "No active team members for workload team=" + teamId);
    }

    CrmTeamMemberEntity best = null;
    long bestCount = Long.MAX_VALUE;
    for (CrmTeamMemberEntity m : members) {
      long open =
          leadRepository.countByTenantIdAndOwnerUserIdAndStatusAndDeletedAtIsNull(
              tenantId, m.getUserId(), "OPEN");
      if (m.getOpenLeadCap() > 0 && open >= m.getOpenLeadCap()) {
        continue;
      }
      if (best == null
          || open < bestCount
          || (open == bestCount
              && (m.getSortOrder() < best.getSortOrder()
                  || (m.getSortOrder() == best.getSortOrder() && m.getId() < best.getId())))) {
        best = m;
        bestCount = open;
      }
    }
    if (best == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "No team members under open lead cap for team=" + teamId);
    }
    return best.getUserId();
  }

  private static List<CrmTeamMemberEntity> filterByGeo(
      List<CrmTeamMemberEntity> members, CrmLeadEntity lead) {
    List<CrmTeamMemberEntity> matched = new ArrayList<>();
    String state = blankToNull(lead.getStateCode());
    String pin = blankToNull(lead.getPincode());
    for (CrmTeamMemberEntity m : members) {
      if (matchesState(m.getStateCodes(), state) || matchesPincodePrefix(m.getPincodePrefixes(), pin)) {
        matched.add(m);
      }
    }
    return matched;
  }

  private static boolean matchesState(String stateCodes, String leadState) {
    if (leadState == null || stateCodes == null || stateCodes.isBlank()) {
      return false;
    }
    String needle = leadState.trim().toUpperCase(Locale.ROOT);
    return Arrays.stream(stateCodes.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(s -> s.toUpperCase(Locale.ROOT))
        .anyMatch(needle::equals);
  }

  private static boolean matchesPincodePrefix(String prefixes, String pincode) {
    if (pincode == null || prefixes == null || prefixes.isBlank()) {
      return false;
    }
    String pin = pincode.trim();
    return Arrays.stream(prefixes.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .anyMatch(pin::startsWith);
  }

  private static TeamMemberResponse toMember(CrmTeamMemberEntity m) {
    return new TeamMemberResponse(
        m.getId(),
        m.getTeamId(),
        m.getUserId(),
        m.getDisplayName(),
        m.isActive(),
        m.getSortOrder(),
        m.getStateCodes(),
        m.getPincodePrefixes(),
        m.getOpenLeadCap());
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
