package com.shopmanagement.crmservice.integration;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.config.CrmMetaProperties;

/** AES-GCM for integration secrets. The database column holds ciphertext only. */
@Component
public class IntegrationCipher {

  private static final int IV_LENGTH = 12;
  private static final int TAG_BITS = 128;

  private final CrmMetaProperties properties;
  private final SecureRandom random = new SecureRandom();

  public IntegrationCipher(CrmMetaProperties properties) {
    this.properties = properties;
  }

  public String encrypt(String plaintext) {
    if (plaintext == null) {
      return null;
    }
    byte[] iv = new byte[IV_LENGTH];
    random.nextBytes(iv);
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
      byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
      buffer.put(iv);
      buffer.put(encrypted);
      return Base64.getEncoder().encodeToString(buffer.array());
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("Could not encrypt the integration secret", ex);
    }
  }

  public String decrypt(String ciphertext) {
    if (ciphertext == null || ciphertext.isBlank()) {
      return null;
    }
    byte[] all = Base64.getDecoder().decode(ciphertext);
    if (all.length <= IV_LENGTH) {
      throw new IllegalStateException("Integration secret ciphertext is too short");
    }
    byte[] iv = new byte[IV_LENGTH];
    byte[] encrypted = new byte[all.length - IV_LENGTH];
    System.arraycopy(all, 0, iv, 0, IV_LENGTH);
    System.arraycopy(all, IV_LENGTH, encrypted, 0, encrypted.length);
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
      return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("Could not decrypt the integration secret", ex);
    }
  }

  private SecretKeySpec key() {
    String raw = properties.getEncryptionKey();
    if (raw == null || raw.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Set CRM_META_ENCRYPTION_KEY on the server before connecting Facebook");
    }
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(raw.trim());
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "CRM_META_ENCRYPTION_KEY must be base64");
    }
    if (decoded.length != 32) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "CRM_META_ENCRYPTION_KEY must decode to 32 bytes");
    }
    return new SecretKeySpec(decoded, "AES");
  }
}
