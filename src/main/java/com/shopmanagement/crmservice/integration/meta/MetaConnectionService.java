package com.shopmanagement.crmservice.integration.meta;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import com.shopmanagement.crmservice.config.CrmMetaProperties;
import com.shopmanagement.crmservice.integration.IntegrationCipher;
import com.shopmanagement.crmservice.integration.meta.MetaGraphClient.Named;
import com.shopmanagement.crmservice.integration.meta.MetaGraphClient.Page;
import com.shopmanagement.crmservice.integration.meta.MetaGraphClient.Token;
import com.shopmanagement.crmservice.persistence.entity.CrmFieldMapEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmIntegrationAssetEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmIntegrationEntity;
import com.shopmanagement.crmservice.persistence.entity.CrmIntegrationSecretEntity;
import com.shopmanagement.crmservice.persistence.repo.CrmBranchRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmFieldMapRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmIntegrationAssetRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmIntegrationRepository;
import com.shopmanagement.crmservice.persistence.repo.CrmIntegrationSecretRepository;
import com.shopmanagement.crmservice.support.TenantIds;

/** Server-side Meta login. Responses never include a page token or the app secret. */
@Service
public class MetaConnectionService {

  public record ConnectLink(String authorizeUrl) {}

  public record AssetView(Long id, String externalId, String name, boolean selected, Long branchId, String parentExternalId) {}

  public record FieldView(String externalKey, String target) {}

  public record Desk(
      String status,
      boolean healthy,
      boolean productionReady,
      String notice,
      String lastError,
      List<AssetView> businesses,
      List<AssetView> pages,
      List<AssetView> forms,
      List<FieldView> fields) {}

  public record AssetChoice(String externalId, boolean selected, Long branchId) {}

  public record FieldChoice(String externalKey, String target) {}

  public record Selection(List<AssetChoice> pages, List<AssetChoice> forms, List<FieldChoice> fields) {}

  private final CrmMetaProperties properties;
  private final MetaGraphClient graph;
  private final IntegrationCipher cipher;
  private final CrmIntegrationRepository integrationRepository;
  private final CrmIntegrationAssetRepository assetRepository;
  private final CrmIntegrationSecretRepository secretRepository;
  private final CrmFieldMapRepository fieldMapRepository;
  private final CrmBranchRepository branchRepository;

  public MetaConnectionService(
      CrmMetaProperties properties,
      MetaGraphClient graph,
      IntegrationCipher cipher,
      CrmIntegrationRepository integrationRepository,
      CrmIntegrationAssetRepository assetRepository,
      CrmIntegrationSecretRepository secretRepository,
      CrmFieldMapRepository fieldMapRepository,
      CrmBranchRepository branchRepository) {
    this.properties = properties;
    this.graph = graph;
    this.cipher = cipher;
    this.integrationRepository = integrationRepository;
    this.assetRepository = assetRepository;
    this.secretRepository = secretRepository;
    this.fieldMapRepository = fieldMapRepository;
    this.branchRepository = branchRepository;
  }

  @Transactional(readOnly = true)
  public ConnectLink connectLink() {
    requireConfigured();
    String tenantId = TenantIds.require();
    String url =
        UriComponentsBuilder.fromUriString("https://www.facebook.com/" + properties.getGraphVersion() + "/dialog/oauth")
            .queryParam("client_id", properties.getAppId())
            .queryParam("redirect_uri", properties.getRedirectUri())
            .queryParam(
                "scope",
                "pages_show_list,pages_manage_metadata,pages_read_engagement,leads_retrieval,ads_management")
            .queryParam("state", signState(tenantId))
            .build()
            .encode()
            .toUriString();
    return new ConnectLink(url);
  }

  @Transactional
  public String finishCallback(String code, String state) {
    requireConfigured();
    String tenantId = verifyState(state);
    if (code == null || code.isBlank()) {
      return returnUrl("error");
    }
    Token shortToken = graph.exchangeCode(code.trim(), properties.getRedirectUri());
    Token longToken = graph.exchangeLongLived(shortToken.accessToken());
    CrmIntegrationEntity integration = integration(tenantId);
    integration.setStatus("CONNECTED");
    integration.setDisplayName("Facebook Lead Ads");
    integration.setConnectedAt(Instant.now());
    integration.setLastError(null);
    integration.touch();
    integration = integrationRepository.save(integration);
    storeSecret(integration.getId(), null, "USER_TOKEN", longToken.accessToken(), longToken.expiresInSeconds());
    replaceCatalog(integration, longToken.accessToken());
    return returnUrl("connected");
  }

  @Transactional(readOnly = true)
  public Desk desk() {
    String tenantId = TenantIds.require();
    CrmIntegrationEntity integration =
        integrationRepository.findByTenantIdAndProviderAndDeletedAtIsNull(tenantId, "META").orElse(null);
    if (integration == null) {
      return new Desk(
          "DISCONNECTED",
          false,
          false,
          notice(),
          null,
          List.of(),
          List.of(),
          List.of(),
          List.of());
    }
    List<CrmIntegrationAssetEntity> assets =
        assetRepository.findByIntegrationIdOrderByAssetTypeAscNameAsc(integration.getId());
    List<AssetView> businesses = new ArrayList<>();
    List<AssetView> pages = new ArrayList<>();
    List<AssetView> forms = new ArrayList<>();
    List<FieldView> fields = new ArrayList<>();
    for (CrmIntegrationAssetEntity asset : assets) {
      AssetView view =
          new AssetView(
              asset.getId(),
              asset.getExternalId(),
              asset.getName(),
              asset.isSelected(),
              asset.getBranchId(),
              asset.getParentExternalId());
      switch (asset.getAssetType()) {
        case "AD_ACCOUNT" -> businesses.add(view);
        case "PAGE" -> pages.add(view);
        case "FORM" -> {
          forms.add(view);
          if (asset.isSelected() && fields.isEmpty()) {
            fieldMapRepository
                .findByAssetIdOrderByIdAsc(asset.getId())
                .forEach(row -> fields.add(new FieldView(row.getExternalKey(), row.getTarget())));
          }
        }
        default -> {
          // Instagram uses the same page subscription. No second connector.
        }
      }
    }
    return new Desk(
        integration.getStatus(),
        "CONNECTED".equals(integration.getStatus()) && integration.getLastSuccessAt() != null,
        false,
        notice(),
        integration.getLastError(),
        businesses,
        pages,
        forms,
        fields);
  }

  @Transactional
  public Desk saveSelection(Selection body) {
    String tenantId = TenantIds.require();
    CrmIntegrationEntity integration =
        integrationRepository
            .findByTenantIdAndProviderAndDeletedAtIsNull(tenantId, "META")
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Connect Facebook first"));
    if ("ERROR".equals(integration.getStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Reconnect Facebook. The page token is no longer valid.");
    }
    Map<String, AssetChoice> pages = index(body == null ? null : body.pages());
    Map<String, AssetChoice> forms = index(body == null ? null : body.forms());
    List<CrmIntegrationAssetEntity> assets =
        assetRepository.findByIntegrationIdOrderByAssetTypeAscNameAsc(integration.getId());
    boolean subscribed = false;
    String subscribeError = null;
    for (CrmIntegrationAssetEntity asset : assets) {
      if ("PAGE".equals(asset.getAssetType())) {
        AssetChoice choice = pages.get(asset.getExternalId());
        boolean select = choice != null && choice.selected();
        asset.setSelected(select);
        asset.setBranchId(choice == null ? asset.getBranchId() : validBranch(tenantId, choice.branchId()));
        asset.setUpdatedAt(Instant.now());
        assetRepository.save(asset);
        if (select) {
          try {
            graph.subscribeLeadgen(asset.getExternalId(), pageToken(asset.getId()));
            subscribed = true;
          } catch (MetaGraphException ex) {
            asset.setSelected(false);
            assetRepository.save(asset);
            subscribeError = clip(ex.getMessage());
          }
        }
      } else if ("FORM".equals(asset.getAssetType())) {
        AssetChoice choice = forms.get(asset.getExternalId());
        asset.setSelected(choice != null && choice.selected());
        if (choice != null) {
          asset.setBranchId(validBranch(tenantId, choice.branchId()));
        }
        asset.setUpdatedAt(Instant.now());
        assetRepository.save(asset);
        if (asset.isSelected() && body != null && body.fields() != null) {
          replaceFields(tenantId, asset.getId(), body.fields());
        }
      }
    }
    if (subscribeError != null) {
      integration.setStatus("DEGRADED");
      integration.setLastError(subscribeError);
    } else if (subscribed) {
      integration.setStatus("CONNECTED");
      integration.setLastError(null);
    }
    integration.touch();
    integrationRepository.save(integration);
    return desk();
  }

  private void replaceCatalog(CrmIntegrationEntity integration, String userToken) {
    List<Named> businesses;
    try {
      businesses = graph.businesses(userToken);
    } catch (MetaGraphException ex) {
      businesses = List.of();
    }
    for (Named business : businesses) {
      upsertAsset(integration, "AD_ACCOUNT", business.id(), business.name(), null, false);
    }
    for (Page page : graph.pages(userToken)) {
      CrmIntegrationAssetEntity asset =
          upsertAsset(integration, "PAGE", page.id(), page.name(), null, false);
      if (page.accessToken() != null) {
        storeSecret(integration.getId(), asset.getId(), "PAGE_TOKEN", page.accessToken(), null);
      }
      try {
        for (Named form : graph.forms(page.id(), page.accessToken())) {
          upsertAsset(integration, "FORM", form.id(), form.name(), page.id(), false);
        }
      } catch (MetaGraphException ex) {
        integration.setLastError(clip(ex.getMessage()));
      }
    }
  }

  private CrmIntegrationAssetEntity upsertAsset(
      CrmIntegrationEntity integration,
      String type,
      String externalId,
      String name,
      String parent,
      boolean selected) {
    if (externalId == null || externalId.isBlank()) {
      return null;
    }
    CrmIntegrationAssetEntity asset =
        assetRepository
            .findByIntegrationIdAndAssetTypeAndExternalId(integration.getId(), type, externalId)
            .orElseGet(CrmIntegrationAssetEntity::new);
    if (asset.getId() == null) {
      asset.setTenantId(integration.getTenantId());
      asset.setIntegrationId(integration.getId());
      asset.setAssetType(type);
      asset.setExternalId(externalId);
      asset.setSelected(selected);
    }
    asset.setName(name);
    asset.setParentExternalId(parent);
    asset.setUpdatedAt(Instant.now());
    return assetRepository.save(asset);
  }

  private void replaceFields(String tenantId, Long assetId, List<FieldChoice> fields) {
    fieldMapRepository.deleteByAssetId(assetId);
    for (FieldChoice field : fields) {
      if (field == null || field.externalKey() == null || field.externalKey().isBlank() || field.target() == null) {
        continue;
      }
      String target = field.target().trim().toUpperCase(Locale.ROOT);
      if (!WebTargets.allowed(target)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown field target " + target);
      }
      CrmFieldMapEntity row = new CrmFieldMapEntity();
      row.setTenantId(tenantId);
      row.setAssetId(assetId);
      row.setExternalKey(field.externalKey().trim());
      row.setTarget(target);
      fieldMapRepository.save(row);
    }
  }

  private void storeSecret(Long integrationId, Long assetId, String kind, String plaintext, Long expiresIn) {
    if (assetId == null) {
      secretRepository.deleteByIntegrationIdAndSecretKindAndAssetIdIsNull(integrationId, kind);
    } else {
      secretRepository.deleteByAssetIdAndSecretKind(assetId, kind);
    }
    CrmIntegrationSecretEntity secret = new CrmIntegrationSecretEntity();
    secret.setIntegrationId(integrationId);
    secret.setAssetId(assetId);
    secret.setSecretKind(kind);
    secret.setCiphertext(cipher.encrypt(plaintext));
    if (expiresIn != null && expiresIn > 0) {
      secret.setExpiresAt(Instant.now().plus(expiresIn, ChronoUnit.SECONDS));
    }
    secret.setRotatedAt(Instant.now());
    secretRepository.save(secret);
  }

  private String pageToken(Long assetId) {
    CrmIntegrationSecretEntity secret =
        secretRepository
            .findByAssetIdAndSecretKind(assetId, "PAGE_TOKEN")
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Reconnect Facebook"));
    return cipher.decrypt(secret.getCiphertext());
  }

  private CrmIntegrationEntity integration(String tenantId) {
    return integrationRepository
        .findByTenantIdAndProviderAndDeletedAtIsNull(tenantId, "META")
        .orElseGet(
            () -> {
              CrmIntegrationEntity created = new CrmIntegrationEntity();
              created.setTenantId(tenantId);
              created.setProvider("META");
              created.setStatus("DISCONNECTED");
              return created;
            });
  }

  private Long validBranch(String tenantId, Long branchId) {
    if (branchId == null) {
      return null;
    }
    if (branchRepository.findByTenantIdAndIdAndDeletedAtIsNull(tenantId, branchId).isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid branch");
    }
    return branchId;
  }

  private void requireConfigured() {
    if (!properties.isConfigured()) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "Facebook app id, app secret, verify token, and encryption key must be set on the server");
    }
  }

  private String signState(String tenantId) {
    long exp = Instant.now().plus(15, ChronoUnit.MINUTES).getEpochSecond();
    String payload = tenantId + "|" + exp;
    String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    return encoded + "." + hmac(payload);
  }

  private String verifyState(String state) {
    if (state == null || !state.contains(".")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Facebook login state is invalid");
    }
    int dot = state.lastIndexOf('.');
    String encoded = state.substring(0, dot);
    String sig = state.substring(dot + 1);
    String payload = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
    if (!constantEquals(hmac(payload), sig)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Facebook login state is invalid");
    }
    int bar = payload.lastIndexOf('|');
    long exp = Long.parseLong(payload.substring(bar + 1));
    if (Instant.now().getEpochSecond() > exp) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Facebook login expired. Connect again.");
    }
    return payload.substring(0, bar);
  }

  private String hmac(String payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(properties.getAppSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException("Could not sign the Facebook login state", ex);
    }
  }

  private static boolean constantEquals(String left, String right) {
    return left != null && right != null && java.security.MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
  }

  private String returnUrl(String result) {
    String base = properties.getUiReturnUrl();
    String join = base.contains("?") ? "&" : "?";
    return base + join + "meta=" + result;
  }

  private static Map<String, AssetChoice> index(List<AssetChoice> choices) {
    Map<String, AssetChoice> out = new LinkedHashMap<>();
    if (choices == null) {
      return out;
    }
    for (AssetChoice choice : choices) {
      if (choice != null && choice.externalId() != null && !choice.externalId().isBlank()) {
        out.put(choice.externalId().trim(), choice);
      }
    }
    return out;
  }

  private static String notice() {
    return "A test lead from Meta's tool can arrive after a page is selected. Live pages stay off until Meta approves the app. WhatsApp is not this connection.";
  }

  private static String clip(String message) {
    if (message == null || message.isBlank()) {
      return "Facebook request failed";
    }
    return message.length() <= 300 ? message : message.substring(0, 300);
  }

  /** Same targets as a website form. */
  private static final class WebTargets {
    private WebTargets() {}

    static boolean allowed(String target) {
      return switch (target) {
        case "CONTACT_PHONE", "CONTACT_EMAIL", "CONTACT_NAME", "LEAD_TITLE", "COURSE", "STORE_ONLY" -> true;
        default -> false;
      };
    }
  }
}
