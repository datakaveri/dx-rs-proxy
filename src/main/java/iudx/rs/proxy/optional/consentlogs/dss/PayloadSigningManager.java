package iudx.rs.proxy.optional.consentlogs.dss;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore.PasswordProtection;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import eu.europa.esig.dss.cades.CAdESSignatureParameters;
import eu.europa.esig.dss.cades.signature.CAdESService;
import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.enumerations.SignaturePackaging;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.Pkcs12SignatureToken;
import eu.europa.esig.dss.validation.CommonCertificateVerifier;
import io.vertx.core.json.JsonObject;

public class PayloadSigningManager {
  private final JsonObject config;
  private final String password;
  private final String certFileName;

  private static final Logger LOGGER = LogManager.getLogger(PayloadSigningManager.class);
  
  private static PayloadSigningManager payloadSigningManager;
  
  private PayloadSigningManager(JsonObject configJson) {
    this.config=configJson;
    this.password = config.getString("password");
    this.certFileName=config.getString("certFileName");
  }
  
  public static PayloadSigningManager getInstance() {
    if(payloadSigningManager==null) {
      throw new AssertionError("You have to call init first");
    }
    return payloadSigningManager;
  }
  
  /**
   * init method will be used to pass the configs [cert file path and password on object creation]
   * @param config
   * @return
   */
  public synchronized static PayloadSigningManager init(JsonObject config) {
    if(payloadSigningManager!=null) {
      throw new AssertionError("Already initialized");
    }
    payloadSigningManager=new PayloadSigningManager(config);
    return payloadSigningManager;
  }

  public String signDocWithPKCS12(JsonObject documentToSign) {
    
    try (
        InputStream is =
            getClass().getClassLoader().getResourceAsStream(certFileName);
        Pkcs12SignatureToken token =
            new Pkcs12SignatureToken(is, new PasswordProtection(password.toCharArray()))) {

      List<DSSPrivateKeyEntry> keys = token.getKeys();
      DSSPrivateKeyEntry privateKey = keys.get(0);

      CAdESSignatureParameters signParameters = new CAdESSignatureParameters();
      signParameters.setSignatureLevel(SignatureLevel.CAdES_BASELINE_B);
      signParameters.setSignaturePackaging(SignaturePackaging.ENVELOPING);
      signParameters.setDigestAlgorithm(DigestAlgorithm.SHA256);
      signParameters.setSigningCertificate(privateKey.getCertificate());
      signParameters.setCertificateChain(privateKey.getCertificateChain());

      CommonCertificateVerifier commonCertificateVerifier = new CommonCertificateVerifier();
      CAdESService service = new CAdESService(commonCertificateVerifier);

      DSSDocument doc = new InMemoryDocument(documentToSign.encode().getBytes());
      ToBeSigned dataToSign = service.getDataToSign(doc, signParameters);
      DigestAlgorithm digestAlgorithm = signParameters.getDigestAlgorithm();
      SignatureValue signatureValue = token.sign(dataToSign, digestAlgorithm, privateKey);
      DSSDocument signedDocument = service.signDocument(doc, signParameters, signatureValue);
      
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      signedDocument.writeTo(out);

      return Base64.getEncoder().encodeToString(out.toByteArray());
    } catch (IOException e) {
      LOGGER.error("error : {}", e);
      e.printStackTrace();
      throw new RuntimeException("Issue occured while signing log");
    }
  }

}
