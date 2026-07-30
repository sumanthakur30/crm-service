package com.shopmanagement.crmservice.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.persistence.entity.CrmLeadEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmOpportunityEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmSlaPolicyEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmTaskEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmLeadRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmOpportunityRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmSlaPolicyRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmTaskRepository;
import com.shopmanagement.crmservice.support.TenantIds;

@Service
public class SlaTaskService {

  private final CrmSlaPolicyRepository slaPolicyRepository;
  private final CrmTaskRepository taskRepository;
  private final CrmLeadRepository leadRepository;
  private final CrmOpportunityRepository opportunityRepository;
  private final TimelineService timelineService;

  public SlaTaskService(
      CrmSlaPolicyRepository slaPolicyRepository,
      CrmTaskRepository taskRepository,
      CrmLeadRepository leadRepository,
      CrmOpportunityRepository opportunityRepository,
      TimelineService timelineService) {
    this.slaPolicyRepository = slaPolicyRepository;
    this.taskRepository = taskRepository;
    this.leadRepository = leadRepository;
    this.opportunityRepository = opportunityRepository;
    this.timelineService = timelineService;
  }

  @Transactional
  public Map<String, Object> ensureDefaultLeadSla() {
    String tenantId = TenantIds.require();
    CrmSlaPolicyEntity policy =
        slaPolicyRepository
            .findByTenantIdAndCodeAndDeletedAtIsNull(tenantId, "LEAD_IDLE_24H")
            .orElseGet(
                () -> {
                  CrmSlaPolicyEntity p = new CrmSlaPolicyEntity();
                  p.setTenantId(tenantId);
                  p.setCode("LEAD_IDLE_24H");
                  p.setName("Lead idle 24h follow-up");
                  p.setObjectType("LEAD");
                  p.setIdleHours(24);
                  p.setPriority("HIGH");
                  p.setTaskTitle("Follow up idle lead");
                  p.setActive(true);
                  return slaPolicyRepository.save(p);
                });
    return Map.of(
        "id", policy.getId(),
        "code", policy.getCode(),
        "idleHours", policy.getIdleHours(),
        "taskTitle", policy.getTaskTitle());
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listOpenTasks() {
    return taskRepository
        .findByTenantIdAndStatusAndDeletedAtIsNullOrderByDueAtAsc(TenantIds.require(), "OPEN")
        .stream()
        .map(SlaTaskService::toTask)
        .toList();
  }

  @Transactional
  public Map<String, Object> completeTask(Long id) {
    CrmTaskEntity task =
        taskRepository
            .findByTenantIdAndIdAndDeletedAtIsNull(TenantIds.require(), id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
    task.setStatus("DONE");
    task.setCompletedAt(Instant.now());
    task.touch();
    return toTask(taskRepository.save(task));
  }

  @Transactional
  public Map<String, Object> processAging() {
    String tenantId = TenantIds.require();
    ensureDefaultLeadSla();
    List<CrmSlaPolicyEntity> policies =
        slaPolicyRepository.findByTenantIdAndActiveTrueAndDeletedAtIsNull(tenantId);
    int created = 0;
    Instant now = Instant.now();
    for (CrmSlaPolicyEntity policy : policies) {
      Instant cutoff = now.minus(policy.getIdleHours(), ChronoUnit.HOURS);
      if ("LEAD".equalsIgnoreCase(policy.getObjectType())) {
        List<CrmLeadEntity> idle =
            leadRepository.findByTenantIdAndStatusAndUpdatedAtBeforeAndDeletedAtIsNull(
                tenantId, "OPEN", cutoff);
        for (CrmLeadEntity lead : idle) {
          if (taskRepository.existsByTenantIdAndRelatedTypeAndRelatedIdAndSlaPolicyIdAndStatusAndDeletedAtIsNull(
              tenantId, "LEAD", lead.getId(), policy.getId(), "OPEN")) {
            continue;
          }
          CrmTaskEntity task = newTask(tenantId, policy, "LEAD", lead.getId(), lead.getOwnerUserId());
          task.setTitle(policy.getTaskTitle() + ": " + lead.getTitle());
          taskRepository.save(task);
          timelineService.recordEvent(
              "LEAD",
              lead.getId(),
              "SLA_TASK_CREATED",
              "SLA task created (" + policy.getCode() + ")",
              Map.of("taskId", task.getId(), "idleHours", policy.getIdleHours()));
          created++;
        }
      } else if ("OPPORTUNITY".equalsIgnoreCase(policy.getObjectType())) {
        List<CrmOpportunityEntity> idle =
            opportunityRepository.findByTenantIdAndStatusAndUpdatedAtBeforeAndDeletedAtIsNull(
                tenantId, "OPEN", cutoff);
        for (CrmOpportunityEntity opp : idle) {
          if (taskRepository.existsByTenantIdAndRelatedTypeAndRelatedIdAndSlaPolicyIdAndStatusAndDeletedAtIsNull(
              tenantId, "OPPORTUNITY", opp.getId(), policy.getId(), "OPEN")) {
            continue;
          }
          CrmTaskEntity task =
              newTask(tenantId, policy, "OPPORTUNITY", opp.getId(), opp.getOwnerUserId());
          task.setTitle(policy.getTaskTitle() + ": " + opp.getName());
          taskRepository.save(task);
          timelineService.recordEvent(
              "OPPORTUNITY",
              opp.getId(),
              "SLA_TASK_CREATED",
              "SLA task created (" + policy.getCode() + ")",
              Map.of("taskId", task.getId()));
          created++;
        }
      }
    }
    long overdueOpen =
        taskRepository.countByTenantIdAndStatusAndDueAtBeforeAndDeletedAtIsNull(tenantId, "OPEN", now);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("tasksCreated", created);
    result.put("openOverdueTasks", overdueOpen);
    return result;
  }

  private static CrmTaskEntity newTask(
      String tenantId, CrmSlaPolicyEntity policy, String relatedType, Long relatedId, String owner) {
    CrmTaskEntity task = new CrmTaskEntity();
    task.setTenantId(tenantId);
    task.setRelatedType(relatedType);
    task.setRelatedId(relatedId);
    task.setPriority(policy.getPriority());
    task.setStatus("OPEN");
    task.setDueAt(Instant.now());
    task.setOwnerUserId(owner);
    task.setSourceCode("SLA");
    task.setSlaPolicyId(policy.getId());
    task.setTitle(policy.getTaskTitle());
    return task;
  }

  private static Map<String, Object> toTask(CrmTaskEntity t) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", t.getId());
    m.put("relatedType", t.getRelatedType());
    m.put("relatedId", t.getRelatedId());
    m.put("title", t.getTitle());
    m.put("status", t.getStatus());
    m.put("priority", t.getPriority());
    m.put("dueAt", t.getDueAt());
    m.put("ownerUserId", t.getOwnerUserId());
    m.put("sourceCode", t.getSourceCode());
    m.put("slaPolicyId", t.getSlaPolicyId());
    return m;
  }
}
