package com.agribank.auth_service.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

/**
 * Utility to generate RSA 2048-bit KeyPair for JWT Signing (RS256).
 * Outputs PEM files to the resources directory.
 */
public class RSAKeyPairGenerator {

    public static void main(String[] args) {
        try {
            System.out.println("Generating RSA 2048-bit KeyPair...");
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair pair = keyGen.generateKeyPair();
            
            PrivateKey privateKey = pair.getPrivate();
            PublicKey publicKey = pair.getPublic();

            // Define target resource directory
            String targetDir = "src/main/resources/certs";
            File dir = new File(targetDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // Write Private Key in PEM Format (PKCS8)
            writePemFile(new File(dir, "private_key.pem"), "PRIVATE KEY", privateKey.getEncoded());
            
            // Write Public Key in PEM Format (X509)
            writePemFile(new File(dir, "public_key.pem"), "PUBLIC KEY", publicKey.getEncoded());

            System.out.println("Keys successfully generated and stored in: " + dir.getAbsolutePath());

        } catch (NoSuchAlgorithmException | IOException e) {
            System.err.println("Failed to generate RSA keys: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void writePemFile(File file, String description, byte[] encodedKey) throws IOException {
        String base64Key = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encodedKey);
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("-----BEGIN " + description + "-----\n");
            writer.write(base64Key);
            writer.write("\n-----END " + description + "-----\n");
        }
    }
}
