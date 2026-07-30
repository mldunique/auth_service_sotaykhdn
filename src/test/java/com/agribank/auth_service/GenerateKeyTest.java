package com.agribank.auth_service;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.operator.PGPDigestCalculator;
import org.bouncycastle.openpgp.operator.jcajce.*;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.util.Date;

public class GenerateKeyTest {

    @Test
    public void generateKey() throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        // Generate RSA key pair
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        // Build PGP key
        PGPKeyPair pgpKp = new JcaPGPKeyPair(PGPPublicKey.RSA_GENERAL, kp, new Date());
        
        // Build signature generator
        PGPDigestCalculator sha1Calc = new JcaPGPDigestCalculatorProviderBuilder()
                .build()
                .get(HashAlgorithmTags.SHA1);

        PGPKeyRingGenerator keyRingGen = new PGPKeyRingGenerator(
                PGPSignature.POSITIVE_CERTIFICATION,
                pgpKp,
                "agribank-sotaykhdn",
                sha1Calc,
                null,
                null,
                new JcaPGPContentSignerBuilder(pgpKp.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA256),
                new JcePBESecretKeyEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256, sha1Calc)
                        .setProvider("BC")
                        .build("password".toCharArray())
        );

        // Ensure directories exist
        File dir = new File("src/main/resources");
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // Save public key
        PGPPublicKeyRing pubRing = keyRingGen.generatePublicKeyRing();
        try (OutputStream out = new FileOutputStream("src/main/resources/publickey.asc");
             ArmoredOutputStream armoredOut = new ArmoredOutputStream(out)) {
            pubRing.encode(armoredOut);
        }
        System.out.println("Mock PGP public key generated at src/main/resources/publickey.asc");
    }
}
