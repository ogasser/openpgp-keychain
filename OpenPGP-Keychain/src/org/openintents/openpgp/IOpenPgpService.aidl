/*
 * Copyright (C) 2013 Dominik Schürmann <dominik@dominikschuermann.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
package org.openintents.openpgp;

import org.openintents.openpgp.IOpenPgpCallback;

/**
 * All methods are oneway, which means they are asynchronous and non-blocking.
 * Results are returned to the callback, which has to be implemented on client side.
 */
interface IOpenPgpService {
    
    /**
     * Encrypt
     * 
     * After successful encryption, callback's onSuccess will contain the resulting output bytes.
     * 
     * @param inputBytes
     *            Byte array you want to encrypt
     * @param encryptionUserIds
     *            User Ids (emails) of recipients
     * @param asciiArmor
     *            Encode for ASCII (Radix-64, 33 percent overhead compared to binary)
     * @param allowUserInteraction
     *            Allows the OpenPGP Provider to handle missing keys by showing activities
     * @param callback
     *            Callback where to return results
     */
    oneway void encrypt(in byte[] inputBytes, in String[] encryptionUserIds,
            in boolean asciiArmor, in boolean allowUserInteraction, in IOpenPgpCallback callback);
    
    /**
     * Sign
     * 
     * After successful signing, callback's onSuccess will contain the resulting output bytes.
     *
     * @param inputBytes
     *            Byte array you want to sign
     * @param asciiArmor
     *            Encode for ASCII (Radix-64, 33 percent overhead compared to binary)
     * @param allowUserInteraction
     *            Allows the OpenPGP Provider to handle missing keys by showing activities
     * @param callback
     *            Callback where to return results
     */
    oneway void sign(in byte[] inputBytes, in boolean asciiArmor, in boolean allowUserInteraction,
            in IOpenPgpCallback callback);
    
    /**
     * Sign then encrypt
     * 
     * After successful signing and encryption, callback's onSuccess will contain the resulting output bytes.
     *
     * @param inputBytes
     *            Byte array you want to sign and encrypt
     * @param encryptionUserIds
     *            User Ids (emails) of recipients
     * @param asciiArmor
     *            Encode for ASCII (Radix-64, 33 percent overhead compared to binary)
     * @param allowUserInteraction
     *            Allows the OpenPGP Provider to handle missing keys by showing activities
     * @param callback
     *            Callback where to return results
     */
    oneway void signAndEncrypt(in byte[] inputBytes, in String[] encryptionUserIds,
            in boolean asciiArmor, in boolean allowUserInteraction, in IOpenPgpCallback callback);
    
    /**
     * Decrypts and verifies given input bytes. This methods handles encrypted-only, signed-and-encrypted,
     * and also signed-only inputBytes.
     * 
     * After successful decryption/verification, callback's onSuccess will contain the resulting output bytes.
     * The signatureResult in onSuccess is only non-null if signed-and-encrypted or signed-only inputBytes were given.
     * 
     * @param inputBytes
     *            Byte array you want to decrypt and verify
     * @param allowUserInteraction
     *            Allows the OpenPGP Provider to handle missing keys by showing activities
     * @param callback
     *            Callback where to return results
     */
    oneway void decryptAndVerify(in byte[] inputBytes, in boolean allowUserInteraction,
            in IOpenPgpCallback callback);
    
}