package com.shopmanagement.crmservice.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.crmservice.entitlement.CrmEntitlementGuard;
import com.shopmanagement.crmservice.service.ConsultancyDeskService;
import com.shopmanagement.crmservice.service.ConsultancyDeskService.AgreementBody;
import com.shopmanagement.crmservice.service.ConsultancyDeskService.AgreementView;
import com.shopmanagement.crmservice.service.ConsultancyDeskService.CancelBody;
import com.shopmanagement.crmservice.service.ConsultancyDeskService.CodeName;
import com.shopmanagement.crmservice.service.ConsultancyDeskService.ConfirmBody;
import com.shopmanagement.crmservice.service.ConsultancyDeskService.ConsultantBody;
import com.shopmanagement.crmservice.service.ConsultancyDeskService.ConsultantView;
import com.shopmanagement.crmservice.service.ConsultancyDeskService.Desk;
import com.shopmanagement.crmservice.service.ConsultancyDeskService.Named;
import com.shopmanagement.crmservice.service.ConsultancyDeskService.OfferBody;
import com.shopmanagement.crmservice.service.ConsultancyDeskService.OfferView;
import com.shopmanagement.crmservice.service.ConsultancyDeskService.PlacementBody;
import com.shopmanagement.crmservice.service.ConsultancyDeskService.PlacementView;
import com.shopmanagement.crmservice.service.ConsultancyDeskService.RuleBody;
import com.shopmanagement.crmservice.service.ConsultancyDeskService.TxnBody;
import com.shopmanagement.crmservice.service.ConsultancyMoney.Summary;

@RestController
@RequestMapping("/api/v1/crm/consultancy")
public class ConsultancyController {

  private final ConsultancyDeskService deskService;
  private final CrmEntitlementGuard entitlementGuard;

  public ConsultancyController(ConsultancyDeskService deskService, CrmEntitlementGuard entitlementGuard) {
    this.deskService = deskService;
    this.entitlementGuard = entitlementGuard;
  }

  @GetMapping
  public Desk desk() {
    entitlementGuard.requireCrmAccess();
    return deskService.desk();
  }

  @PostMapping("/institutions")
  public Named institution(@RequestBody CodeName body) {
    entitlementGuard.requireCrmAccess();
    return deskService.saveInstitution(body);
  }

  @PostMapping("/courses")
  public Named course(@RequestBody CodeName body) {
    entitlementGuard.requireCrmAccess();
    return deskService.saveCourse(body);
  }

  @PostMapping("/sessions")
  public Named session(@RequestBody CodeName body) {
    entitlementGuard.requireCrmAccess();
    return deskService.saveSession(body);
  }

  @PostMapping("/consultants")
  public ConsultantView consultant(@RequestBody ConsultantBody body) {
    entitlementGuard.requireCrmAccess();
    return deskService.saveConsultant(body);
  }

  @PostMapping("/offers")
  public OfferView offer(@RequestBody OfferBody body) {
    entitlementGuard.requireCrmAccess();
    return deskService.saveOffer(body);
  }

  @PostMapping("/placements")
  public PlacementView placement(@RequestBody PlacementBody body) {
    entitlementGuard.requireCrmAccess();
    return deskService.openPlacement(body);
  }

  @PostMapping("/placements/{id}/confirm")
  public PlacementView confirm(@PathVariable Long id, @RequestBody ConfirmBody body) {
    entitlementGuard.requireCrmAccess();
    return deskService.confirm(id, body);
  }

  @PostMapping("/placements/{id}/cancel")
  public PlacementView cancel(@PathVariable Long id, @RequestBody CancelBody body) {
    entitlementGuard.requireCrmAccess();
    return deskService.cancel(id, body);
  }

  @PostMapping("/placements/{id}/transactions")
  public Summary transaction(@PathVariable Long id, @RequestBody TxnBody body) {
    entitlementGuard.requireCrmAccess();
    return deskService.addTxn(id, body);
  }

  @GetMapping("/placements/{id}/contribution")
  public Summary one(@PathVariable Long id) {
    entitlementGuard.requireCrmAccess();
    return deskService.contribution(id);
  }

  @PostMapping("/agreements")
  public AgreementView agreement(@RequestBody AgreementBody body) {
    entitlementGuard.requireCrmAccess();
    return deskService.saveAgreement(body);
  }

  @PostMapping("/assignment-rules")
  public void rule(@RequestBody RuleBody body) {
    entitlementGuard.requireCrmAccess();
    deskService.saveRule(body);
  }
}
