package com.example.dharani.publicdata.encryption;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * BhubharatiSurveyNosEncryptor
 *
 * Purpose ------- Utility class used to generate the encrypted "searchData"
 * parameter required by the Bhubharati getPublicDataInfo API.
 *
 * Example ------- Survey Number: 1147
 *
 * Generated searchData: GToOfYzvSJq3GxwzolIGXw==
 *
 * How it works ------------ Bhubharati does not send the survey number
 * directly. The survey number is encrypted using:
 *
 * Algorithm : AES-128-CBC Padding : PKCS7 (Java uses PKCS5Padding) Output :
 * Base64 String
 *
 * AES Key: u/Gu5posvwDsXUnV5Zaq4g==
 *
 * AES IV: 5D9r9ZVzEYYgha93/aUK2w==
 *
 * Typical Flow ------------ Survey Number 1147
 *
 * ↓ Encrypt
 *
 * searchData GToOfYzvSJq3GxwzolIGXw==
 *
 * ↓
 *
 * Used in: /getPublicDataInfo?searchData=GToOfYzvSJq3GxwzolIGXw==
 *
 * This class reproduces the same encryption logic used by the Bhubharati web
 * application.
 */
public final class BhubharatiSurveyNosEncryptor {

	/**
	 * AES Encryption Key Base64 encoded in source code.
	 */
	private static final String KEY_BASE64 = "u/Gu5posvwDsXUnV5Zaq4g==";

	/**
	 * AES Initialization Vector (IV) Base64 encoded in source code.
	 */
	private static final String IV_BASE64 = "5D9r9ZVzEYYgha93/aUK2w==";

	/**
	 * Prevent object creation. Utility class only.
	 */
	private BhubharatiSurveyNosEncryptor() {
	}

	/**
	 * Converts the survey number into the encrypted searchData value expected by
	 * Bhubharati.
	 *
	 * Input Example: 1147
	 *
	 * Output Example: GToOfYzvSJq3GxwzolIGXw==
	 *
	 * @param surveyNumber Survey number to encrypt
	 * @return Encrypted searchData value
	 */
	public static String encrypt(String surveyNumber) throws Exception {

		// Convert Base64 key into raw AES bytes
		byte[] keyBytes = Base64.getDecoder().decode(KEY_BASE64);

		// Convert Base64 IV into raw bytes
		byte[] ivBytes = Base64.getDecoder().decode(IV_BASE64);

		// Create AES key specification
		SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");

		// Create IV specification
		IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);

		// Same encryption used by CryptoJS
		Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");

		// Initialize cipher for encryption
		cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);

		// Encrypt survey number
		byte[] encryptedBytes = cipher.doFinal(surveyNumber.getBytes(StandardCharsets.UTF_8));

		// Return Base64 encoded result
		return Base64.getEncoder().encodeToString(encryptedBytes);
	}

	/**
	 * Sample execution.
	 */
	public static void main(String[] args) throws Exception {

		String surveyNo = "1147";

		String searchData = BhubharatiSurveyNosEncryptor.encrypt(surveyNo);

		System.out.println("Survey Number : " + surveyNo);

		System.out.println("searchData    : " + searchData);
	}
}
