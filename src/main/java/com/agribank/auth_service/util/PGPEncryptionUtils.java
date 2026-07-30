package com.agribank.auth_service.util;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.Base64;
import java.util.Date;
import java.util.Iterator;

import org.bouncycastle.bcpg.CompressionAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPCompressedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.jcajce.JcaPGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.operator.jcajce.JcePGPDataEncryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyKeyEncryptionMethodGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * PGP Encryption / Decryption Utility
 *
 *
 */
@Component
public class PGPEncryptionUtils {

	private final PGPPublicKey publicKey;

	static {
		if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
			Security.addProvider(new BouncyCastleProvider());
		}
	}

	public PGPEncryptionUtils(@Value("classpath:publickey.asc") Resource publicKeyResource) throws Exception {

		// Validate files tồn tại
		if (!publicKeyResource.exists()) {
			throw new FileNotFoundException("Không tìm thấy publickey.asc trong resources/");
		}

		// Load keys 1 lần khi Spring khởi động
		try (InputStream pub = new BufferedInputStream(publicKeyResource.getInputStream())) {
			this.publicKey = loadPublicKey(pub);
		}

	}

	/**
	 * MÃ HÓA: plain text → PGP encrypted → Base64
	 *
	 * Dùng để mã hóa request trước khi gửi
	 *
	 * @param plainText chuỗi data cần mã hóa
	 * @return Base64 string của dữ liệu đã mã hóa PGP
	 */
	public String encrypt(String plainText) throws Exception {
		if (plainText == null || plainText.isBlank()) {
			throw new IllegalArgumentException("plainText không được rỗng");
		}
		return encryptToBase64(plainText, this.publicKey);
	}

	private static PGPPublicKey loadPublicKey(InputStream inputStream) throws Exception {
		InputStream decoderStream = PGPUtil.getDecoderStream(inputStream);
		JcaPGPPublicKeyRingCollection pgpPub = new JcaPGPPublicKeyRingCollection(decoderStream);

		Iterator<PGPPublicKeyRing> rings = pgpPub.getKeyRings();
		while (rings.hasNext()) {
			Iterator<PGPPublicKey> keys = rings.next().getPublicKeys();
			while (keys.hasNext()) {
				PGPPublicKey key = keys.next();
				if (key.isEncryptionKey())
					return key;
			}
		}
		throw new IllegalArgumentException("Không tìm thấy encryption key trong publickey.asc!");
	}

	private static String encryptToBase64(String plainText, PGPPublicKey publicKey) throws Exception {
		byte[] plainBytes = plainText.getBytes(StandardCharsets.UTF_8);
		ByteArrayOutputStream encryptedOut = new ByteArrayOutputStream();

		JcePGPDataEncryptorBuilder encryptorBuilder = new JcePGPDataEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256)
				.setWithIntegrityPacket(true).setProvider(BouncyCastleProvider.PROVIDER_NAME);

		PGPEncryptedDataGenerator encGen = new PGPEncryptedDataGenerator(encryptorBuilder);
		encGen.addMethod(new JcePublicKeyKeyEncryptionMethodGenerator(publicKey)
				.setProvider(BouncyCastleProvider.PROVIDER_NAME));

		try (OutputStream encOut = encGen.open(encryptedOut, new byte[4096])) {
			PGPCompressedDataGenerator comGen = new PGPCompressedDataGenerator(CompressionAlgorithmTags.ZIP);
			try (OutputStream comOut = comGen.open(encOut)) {
				PGPLiteralDataGenerator litGen = new PGPLiteralDataGenerator();
				try (OutputStream litOut = litGen.open(comOut, PGPLiteralData.BINARY, "data", plainBytes.length,
						new Date())) {
					litOut.write(plainBytes);
				}
			}
		}
		return Base64.getEncoder().encodeToString(encryptedOut.toByteArray());
	}
}
