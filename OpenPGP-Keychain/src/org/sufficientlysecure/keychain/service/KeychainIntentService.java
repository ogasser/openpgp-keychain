/*
 * Copyright (C) 2012-2013 Dominik Schürmann <dominik@dominikschuermann.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.sufficientlysecure.keychain.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import org.spongycastle.openpgp.PGPPublicKeyRing;
import org.spongycastle.openpgp.PGPSecretKey;
import org.spongycastle.openpgp.PGPSecretKeyRing;
import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.Id;
import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.helper.FileHelper;
import org.sufficientlysecure.keychain.helper.OtherHelper;
import org.sufficientlysecure.keychain.helper.Preferences;
import org.sufficientlysecure.keychain.pgp.PgpConversionHelper;
import org.sufficientlysecure.keychain.pgp.PgpHelper;
import org.sufficientlysecure.keychain.pgp.PgpImportExport;
import org.sufficientlysecure.keychain.pgp.PgpKeyOperation;
import org.sufficientlysecure.keychain.pgp.PgpOperation;
import org.sufficientlysecure.keychain.pgp.exception.PgpGeneralException;
import org.sufficientlysecure.keychain.provider.KeychainContract.DataStream;
import org.sufficientlysecure.keychain.provider.ProviderHelper;
import org.sufficientlysecure.keychain.util.HkpKeyServer;
import org.sufficientlysecure.keychain.util.InputData;
import org.sufficientlysecure.keychain.util.KeyServer.KeyInfo;
import org.sufficientlysecure.keychain.util.Log;
import org.sufficientlysecure.keychain.util.ProgressDialogUpdater;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

/**
 * This Service contains all important long lasting operations for APG. It receives Intents with
 * data from the activities or other apps, queues these intents, executes them, and stops itself
 * after doing them.
 */
public class KeychainIntentService extends IntentService implements ProgressDialogUpdater {

    /* extras that can be given by intent */
    public static final String EXTRA_MESSENGER = "messenger";
    public static final String EXTRA_DATA = "data";

    /* possible actions */
    public static final String ACTION_ENCRYPT_SIGN = Constants.INTENT_PREFIX + "ENCRYPT_SIGN";

    public static final String ACTION_DECRYPT_VERIFY = Constants.INTENT_PREFIX + "DECRYPT_VERIFY";

    public static final String ACTION_SAVE_KEYRING = Constants.INTENT_PREFIX + "SAVE_KEYRING";
    public static final String ACTION_GENERATE_KEY = Constants.INTENT_PREFIX + "GENERATE_KEY";
    public static final String ACTION_GENERATE_DEFAULT_RSA_KEYS = Constants.INTENT_PREFIX
            + "GENERATE_DEFAULT_RSA_KEYS";

    public static final String ACTION_DELETE_FILE_SECURELY = Constants.INTENT_PREFIX
            + "DELETE_FILE_SECURELY";

    public static final String ACTION_IMPORT_KEYRING = Constants.INTENT_PREFIX + "IMPORT_KEYRING";
    public static final String ACTION_EXPORT_KEYRING = Constants.INTENT_PREFIX + "EXPORT_KEYRING";

    public static final String ACTION_UPLOAD_KEYRING = Constants.INTENT_PREFIX + "UPLOAD_KEYRING";
    public static final String ACTION_QUERY_KEYRING = Constants.INTENT_PREFIX + "QUERY_KEYRING";

    public static final String ACTION_SIGN_KEYRING = Constants.INTENT_PREFIX + "SIGN_KEYRING";

    /* keys for data bundle */

    // encrypt, decrypt, import export
    public static final String TARGET = "target";
    // possible targets:
    public static final int TARGET_BYTES = 1;
    public static final int TARGET_FILE = 2;
    public static final int TARGET_STREAM = 3;

    // encrypt
    public static final String ENCRYPT_SECRET_KEY_ID = "secret_key_id";
    public static final String ENCRYPT_USE_ASCII_ARMOR = "use_ascii_armor";
    public static final String ENCRYPT_ENCRYPTION_KEYS_IDS = "encryption_keys_ids";
    public static final String ENCRYPT_COMPRESSION_ID = "compression_id";
    public static final String ENCRYPT_GENERATE_SIGNATURE = "generate_signature";
    public static final String ENCRYPT_SIGN_ONLY = "sign_only";
    public static final String ENCRYPT_MESSAGE_BYTES = "message_bytes";
    public static final String ENCRYPT_INPUT_FILE = "input_file";
    public static final String ENCRYPT_OUTPUT_FILE = "output_file";
    public static final String ENCRYPT_PROVIDER_URI = "provider_uri";

    // decrypt/verify
    public static final String DECRYPT_SIGNED_ONLY = "signed_only";
    public static final String DECRYPT_RETURN_BYTES = "return_binary";
    public static final String DECRYPT_CIPHERTEXT_BYTES = "ciphertext_bytes";
    public static final String DECRYPT_ASSUME_SYMMETRIC = "assume_symmetric";
    public static final String DECRYPT_LOOKUP_UNKNOWN_KEY = "lookup_unknownKey";

    // save keyring
    public static final String SAVE_KEYRING_NEW_PASSPHRASE = "new_passphrase";
    public static final String SAVE_KEYRING_CURRENT_PASSPHRASE = "current_passphrase";
    public static final String SAVE_KEYRING_USER_IDS = "user_ids";
    public static final String SAVE_KEYRING_KEYS = "keys";
    public static final String SAVE_KEYRING_KEYS_USAGES = "keys_usages";
    public static final String SAVE_KEYRING_MASTER_KEY_ID = "master_key_id";
    public static final String SAVE_KEYRING_CAN_SIGN = "can_sign";

    // generate key
    public static final String GENERATE_KEY_ALGORITHM = "algorithm";
    public static final String GENERATE_KEY_KEY_SIZE = "key_size";
    public static final String GENERATE_KEY_SYMMETRIC_PASSPHRASE = "passphrase";
    public static final String GENERATE_KEY_MASTER_KEY = "master_key";

    // delete file securely
    public static final String DELETE_FILE = "deleteFile";

    // import key
    public static final String IMPORT_INPUT_STREAM = "import_input_stream";
    public static final String IMPORT_FILENAME = "import_filename";
    public static final String IMPORT_BYTES = "import_bytes";
    // public static final String IMPORT_KEY_TYPE = "importKeyType";

    // export key
    public static final String EXPORT_OUTPUT_STREAM = "export_output_stream";
    public static final String EXPORT_FILENAME = "export_filename";
    public static final String EXPORT_KEY_TYPE = "export_key_type";
    public static final String EXPORT_ALL = "export_all";
    public static final String EXPORT_KEY_RING_MASTER_KEY_ID = "export_key_ring_id";

    // upload key
    public static final String UPLOAD_KEY_SERVER = "upload_key_server";
    public static final String UPLOAD_KEY_KEYRING_ROW_ID = "upload_key_ring_id";

    // query key
    public static final String QUERY_KEY_SERVER = "query_key_server";
    public static final String QUERY_KEY_TYPE = "query_key_type";
    public static final String QUERY_KEY_STRING = "query_key_string";
    public static final String QUERY_KEY_ID = "query_key_id";

    // sign key
    public static final String SIGN_KEY_MASTER_KEY_ID = "sign_key_master_key_id";
    public static final String SIGN_KEY_PUB_KEY_ID = "sign_key_pub_key_id";

    /*
     * possible data keys as result send over messenger
     */
    // keys
    public static final String RESULT_NEW_KEY = "new_key";
    public static final String RESULT_NEW_KEY2 = "new_key2";

    // encrypt
    public static final String RESULT_SIGNATURE_BYTES = "signature_data";
    public static final String RESULT_SIGNATURE_STRING = "signature_text";
    public static final String RESULT_ENCRYPTED_STRING = "encrypted_message";
    public static final String RESULT_ENCRYPTED_BYTES = "encrypted_data";
    public static final String RESULT_URI = "result_uri";

    // decrypt/verify
    public static final String RESULT_DECRYPTED_STRING = "decrypted_message";
    public static final String RESULT_DECRYPTED_BYTES = "decrypted_data";
    public static final String RESULT_SIGNATURE = "signature";
    public static final String RESULT_SIGNATURE_KEY_ID = "signature_key_id";
    public static final String RESULT_SIGNATURE_USER_ID = "signature_user_id";

    public static final String RESULT_SIGNATURE_SUCCESS = "signature_success";
    public static final String RESULT_SIGNATURE_UNKNOWN = "signature_unknown";
    public static final String RESULT_SIGNATURE_LOOKUP_KEY = "lookup_key";

    // import
    public static final String RESULT_IMPORT_ADDED = "added";
    public static final String RESULT_IMPORT_UPDATED = "updated";
    public static final String RESULT_IMPORT_BAD = "bad";

    // export
    public static final String RESULT_EXPORT = "exported";

    // query
    public static final String RESULT_QUERY_KEY_DATA = "query_key_data";
    public static final String RESULT_QUERY_KEY_SEARCH_RESULT = "query_key_search_result";

    Messenger mMessenger;

    public KeychainIntentService() {
        super("ApgService");
    }

    /**
     * The IntentService calls this method from the default worker thread with the intent that
     * started the service. When this method returns, IntentService stops the service, as
     * appropriate.
     */
    @Override
    protected void onHandleIntent(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null) {
            Log.e(Constants.TAG, "Extras bundle is null!");
            return;
        }

        if (!(extras.containsKey(EXTRA_MESSENGER) || extras.containsKey(EXTRA_DATA) || (intent
                .getAction() == null))) {
            Log.e(Constants.TAG,
                    "Extra bundle must contain a messenger, a data bundle, and an action!");
            return;
        }

        mMessenger = (Messenger) extras.get(EXTRA_MESSENGER);
        Bundle data = extras.getBundle(EXTRA_DATA);

        OtherHelper.logDebugBundle(data, "EXTRA_DATA");

        String action = intent.getAction();

        // execute action from extra bundle
        if (ACTION_ENCRYPT_SIGN.equals(action)) {
            try {
                /* Input */
                int target = data.getInt(TARGET);

                long secretKeyId = data.getLong(ENCRYPT_SECRET_KEY_ID);
                String encryptionPassphrase = data.getString(GENERATE_KEY_SYMMETRIC_PASSPHRASE);

                boolean useAsciiArmor = data.getBoolean(ENCRYPT_USE_ASCII_ARMOR);
                long encryptionKeyIds[] = data.getLongArray(ENCRYPT_ENCRYPTION_KEYS_IDS);
                int compressionId = data.getInt(ENCRYPT_COMPRESSION_ID);
                boolean generateSignature = data.getBoolean(ENCRYPT_GENERATE_SIGNATURE);
                boolean signOnly = data.getBoolean(ENCRYPT_SIGN_ONLY);

                InputStream inStream = null;
                long inLength = -1;
                InputData inputData = null;
                OutputStream outStream = null;
                String streamFilename = null;
                switch (target) {
                case TARGET_BYTES: /* encrypting bytes directly */
                    byte[] bytes = data.getByteArray(ENCRYPT_MESSAGE_BYTES);

                    inStream = new ByteArrayInputStream(bytes);
                    inLength = bytes.length;

                    inputData = new InputData(inStream, inLength);
                    outStream = new ByteArrayOutputStream();

                    break;
                case TARGET_FILE: /* encrypting file */
                    String inputFile = data.getString(ENCRYPT_INPUT_FILE);
                    String outputFile = data.getString(ENCRYPT_OUTPUT_FILE);

                    // check if storage is ready
                    if (!FileHelper.isStorageMounted(inputFile)
                            || !FileHelper.isStorageMounted(outputFile)) {
                        throw new PgpGeneralException(
                                getString(R.string.error_externalStorageNotReady));
                    }

                    inStream = new FileInputStream(inputFile);
                    File file = new File(inputFile);
                    inLength = file.length();
                    inputData = new InputData(inStream, inLength);

                    outStream = new FileOutputStream(outputFile);

                    break;

                case TARGET_STREAM: /* Encrypting stream from content uri */
                    Uri providerUri = (Uri) data.getParcelable(ENCRYPT_PROVIDER_URI);

                    // InputStream
                    InputStream in = getContentResolver().openInputStream(providerUri);
                    inLength = PgpHelper.getLengthOfStream(in);
                    inputData = new InputData(in, inLength);

                    // OutputStream
                    try {
                        while (true) {
                            streamFilename = PgpHelper.generateRandomFilename(32);
                            if (streamFilename == null) {
                                throw new PgpGeneralException("couldn't generate random file name");
                            }
                            openFileInput(streamFilename).close();
                        }
                    } catch (FileNotFoundException e) {
                        // found a name that isn't used yet
                    }
                    outStream = openFileOutput(streamFilename, Context.MODE_PRIVATE);

                    break;

                default:
                    throw new PgpGeneralException("No target choosen!");

                }

                /* Operation */
                PgpOperation operation = new PgpOperation(this, this, inputData, outStream);
                if (generateSignature) {
                    Log.d(Constants.TAG, "generating signature...");
                    operation.generateSignature(useAsciiArmor, false, secretKeyId,
                            PassphraseCacheService.getCachedPassphrase(this, secretKeyId),
                            Preferences.getPreferences(this).getDefaultHashAlgorithm(), Preferences
                                    .getPreferences(this).getForceV3Signatures());
                } else if (signOnly) {
                    Log.d(Constants.TAG, "sign only...");
                    operation.signText(secretKeyId, PassphraseCacheService.getCachedPassphrase(
                            this, secretKeyId), Preferences.getPreferences(this)
                            .getDefaultHashAlgorithm(), Preferences.getPreferences(this)
                            .getForceV3Signatures());
                } else {
                    Log.d(Constants.TAG, "encrypt...");
                    operation.signAndEncrypt(useAsciiArmor, compressionId, encryptionKeyIds,
                            encryptionPassphrase, Preferences.getPreferences(this)
                                    .getDefaultEncryptionAlgorithm(), secretKeyId, Preferences
                                    .getPreferences(this).getDefaultHashAlgorithm(), Preferences
                                    .getPreferences(this).getForceV3Signatures(),
                            PassphraseCacheService.getCachedPassphrase(this, secretKeyId));
                }

                outStream.close();

                /* Output */

                Bundle resultData = new Bundle();

                switch (target) {
                case TARGET_BYTES:
                    if (useAsciiArmor) {
                        String output = new String(
                                ((ByteArrayOutputStream) outStream).toByteArray());
                        if (generateSignature) {
                            resultData.putString(RESULT_SIGNATURE_STRING, output);
                        } else {
                            resultData.putString(RESULT_ENCRYPTED_STRING, output);
                        }
                    } else {
                        byte output[] = ((ByteArrayOutputStream) outStream).toByteArray();
                        if (generateSignature) {
                            resultData.putByteArray(RESULT_SIGNATURE_BYTES, output);
                        } else {
                            resultData.putByteArray(RESULT_ENCRYPTED_BYTES, output);
                        }
                    }

                    break;
                case TARGET_FILE:
                    // nothing, file was written, just send okay

                    break;
                case TARGET_STREAM:
                    String uri = DataStream.buildDataStreamUri(streamFilename).toString();
                    resultData.putString(RESULT_URI, uri);

                    break;
                }

                OtherHelper.logDebugBundle(resultData, "resultData");

                sendMessageToHandler(KeychainIntentServiceHandler.MESSAGE_OKAY, resultData);
            } catch (Exception e) {
                sendErrorToHandler(e);
            }
        } else if (ACTION_DECRYPT_VERIFY.equals(action)) {
            try {
                /* Input */
                int target = data.getInt(TARGET);

                long secretKeyId = data.getLong(ENCRYPT_SECRET_KEY_ID);
                byte[] bytes = data.getByteArray(DECRYPT_CIPHERTEXT_BYTES);
                boolean signedOnly = data.getBoolean(DECRYPT_SIGNED_ONLY);
                boolean returnBytes = data.getBoolean(DECRYPT_RETURN_BYTES);
                boolean assumeSymmetricEncryption = data.getBoolean(DECRYPT_ASSUME_SYMMETRIC);

                boolean lookupUnknownKey = data.getBoolean(DECRYPT_LOOKUP_UNKNOWN_KEY);

                InputStream inStream = null;
                long inLength = -1;
                InputData inputData = null;
                OutputStream outStream = null;
                String streamFilename = null;
                switch (target) {
                case TARGET_BYTES: /* decrypting bytes directly */
                    inStream = new ByteArrayInputStream(bytes);
                    inLength = bytes.length;

                    inputData = new InputData(inStream, inLength);
                    outStream = new ByteArrayOutputStream();

                    break;

                case TARGET_FILE: /* decrypting file */
                    String inputFile = data.getString(ENCRYPT_INPUT_FILE);
                    String outputFile = data.getString(ENCRYPT_OUTPUT_FILE);

                    // check if storage is ready
                    if (!FileHelper.isStorageMounted(inputFile)
                            || !FileHelper.isStorageMounted(outputFile)) {
                        throw new PgpGeneralException(
                                getString(R.string.error_externalStorageNotReady));
                    }

                    // InputStream
                    inLength = -1;
                    inStream = new FileInputStream(inputFile);
                    File file = new File(inputFile);
                    inLength = file.length();
                    inputData = new InputData(inStream, inLength);

                    // OutputStream
                    outStream = new FileOutputStream(outputFile);

                    break;

                case TARGET_STREAM: /* decrypting stream from content uri */
                    Uri providerUri = (Uri) data.getParcelable(ENCRYPT_PROVIDER_URI);

                    // InputStream
                    InputStream in = getContentResolver().openInputStream(providerUri);
                    inLength = PgpHelper.getLengthOfStream(in);
                    inputData = new InputData(in, inLength);

                    // OutputStream
                    try {
                        while (true) {
                            streamFilename = PgpHelper.generateRandomFilename(32);
                            if (streamFilename == null) {
                                throw new PgpGeneralException("couldn't generate random file name");
                            }
                            openFileInput(streamFilename).close();
                        }
                    } catch (FileNotFoundException e) {
                        // found a name that isn't used yet
                    }
                    outStream = openFileOutput(streamFilename, Context.MODE_PRIVATE);

                    break;

                default:
                    throw new PgpGeneralException("No target choosen!");

                }

                /* Operation */

                Bundle resultData = new Bundle();

                // verifyText and decrypt returning additional resultData values for the
                // verification of signatures
                PgpOperation operation = new PgpOperation(this, this, inputData, outStream);
                if (signedOnly) {
                    resultData = operation.verifyText(lookupUnknownKey);
                } else {
                    resultData = operation.decryptAndVerify(
                            PassphraseCacheService.getCachedPassphrase(this, secretKeyId),
                            assumeSymmetricEncryption);
                }

                outStream.close();

                /* Output */

                switch (target) {
                case TARGET_BYTES:
                    if (returnBytes) {
                        byte output[] = ((ByteArrayOutputStream) outStream).toByteArray();
                        resultData.putByteArray(RESULT_DECRYPTED_BYTES, output);
                    } else {
                        String output = new String(
                                ((ByteArrayOutputStream) outStream).toByteArray());
                        resultData.putString(RESULT_DECRYPTED_STRING, output);
                    }

                    break;
                case TARGET_FILE:
                    // nothing, file was written, just send okay and verification bundle

                    break;
                case TARGET_STREAM:
                    String uri = DataStream.buildDataStreamUri(streamFilename).toString();
                    resultData.putString(RESULT_URI, uri);

                    break;
                }

                OtherHelper.logDebugBundle(resultData, "resultData");

                sendMessageToHandler(KeychainIntentServiceHandler.MESSAGE_OKAY, resultData);
            } catch (Exception e) {
                sendErrorToHandler(e);
            }
        } else if (ACTION_SAVE_KEYRING.equals(action)) {
            try {
                /* Input */
                String oldPassPhrase = data.getString(SAVE_KEYRING_CURRENT_PASSPHRASE);
                String newPassPhrase = data.getString(SAVE_KEYRING_NEW_PASSPHRASE);
                boolean canSign = true;

                if (data.containsKey(SAVE_KEYRING_CAN_SIGN)) {
                    canSign = data.getBoolean(SAVE_KEYRING_CAN_SIGN);
                }

                if (newPassPhrase == null) {
                    newPassPhrase = oldPassPhrase;
                }
                ArrayList<String> userIds = data.getStringArrayList(SAVE_KEYRING_USER_IDS);
                ArrayList<PGPSecretKey> keys = PgpConversionHelper.BytesToPGPSecretKeyList(data
                        .getByteArray(SAVE_KEYRING_KEYS));
                ArrayList<Integer> keysUsages = data.getIntegerArrayList(SAVE_KEYRING_KEYS_USAGES);
                long masterKeyId = data.getLong(SAVE_KEYRING_MASTER_KEY_ID);

                PgpKeyOperation keyOperations = new PgpKeyOperation(this, this);
                /* Operation */
                if (!canSign) {
                    keyOperations.changeSecretKeyPassphrase(
                            ProviderHelper.getPGPSecretKeyRingByKeyId(this, masterKeyId),
                            oldPassPhrase, newPassPhrase);
                } else {
                    keyOperations.buildSecretKey(userIds, keys, keysUsages, masterKeyId,
                            oldPassPhrase, newPassPhrase);
                }
                PassphraseCacheService.addCachedPassphrase(this, masterKeyId, newPassPhrase);

                /* Output */
                sendMessageToHandler(KeychainIntentServiceHandler.MESSAGE_OKAY);
            } catch (Exception e) {
                sendErrorToHandler(e);
            }
        } else if (ACTION_GENERATE_KEY.equals(action)) {
            try {
                /* Input */
                int algorithm = data.getInt(GENERATE_KEY_ALGORITHM);
                String passphrase = data.getString(GENERATE_KEY_SYMMETRIC_PASSPHRASE);
                int keysize = data.getInt(GENERATE_KEY_KEY_SIZE);
                PGPSecretKey masterKey = null;
                if (data.containsKey(GENERATE_KEY_MASTER_KEY)) {
                    masterKey = PgpConversionHelper.BytesToPGPSecretKey(data
                            .getByteArray(GENERATE_KEY_MASTER_KEY));
                }

                /* Operation */
                PgpKeyOperation keyOperations = new PgpKeyOperation(this, this);
                PGPSecretKeyRing newKeyRing = keyOperations.createKey(algorithm, keysize,
                        passphrase, masterKey);

                /* Output */
                Bundle resultData = new Bundle();
                resultData.putByteArray(RESULT_NEW_KEY,
                        PgpConversionHelper.PGPSecretKeyRingToBytes(newKeyRing));

                OtherHelper.logDebugBundle(resultData, "resultData");

                sendMessageToHandler(KeychainIntentServiceHandler.MESSAGE_OKAY, resultData);
            } catch (Exception e) {
                sendErrorToHandler(e);
            }
        } else if (ACTION_GENERATE_DEFAULT_RSA_KEYS.equals(action)) {
            // generate one RSA 4096 key for signing and one subkey for encrypting!
            try {
                /* Input */
                String passphrase = data.getString(GENERATE_KEY_SYMMETRIC_PASSPHRASE);

                /* Operation */
                PgpKeyOperation keyOperations = new PgpKeyOperation(this, this);

                PGPSecretKeyRing masterKeyRing = keyOperations.createKey(Id.choice.algorithm.rsa,
                        4096, passphrase, null);

                PGPSecretKeyRing subKeyRing = keyOperations.createKey(Id.choice.algorithm.rsa,
                        4096, passphrase, masterKeyRing.getSecretKey());

                /* Output */
                Bundle resultData = new Bundle();
                resultData.putByteArray(RESULT_NEW_KEY,
                        PgpConversionHelper.PGPSecretKeyRingToBytes(masterKeyRing));
                resultData.putByteArray(RESULT_NEW_KEY2,
                        PgpConversionHelper.PGPSecretKeyRingToBytes(subKeyRing));

                OtherHelper.logDebugBundle(resultData, "resultData");

                sendMessageToHandler(KeychainIntentServiceHandler.MESSAGE_OKAY, resultData);
            } catch (Exception e) {
                sendErrorToHandler(e);
            }
        } else if (ACTION_DELETE_FILE_SECURELY.equals(action)) {
            try {
                /* Input */
                String deleteFile = data.getString(DELETE_FILE);

                /* Operation */
                try {
                    PgpHelper.deleteFileSecurely(this, this, new File(deleteFile));
                } catch (FileNotFoundException e) {
                    throw new PgpGeneralException(
                            getString(R.string.error_fileNotFound, deleteFile));
                } catch (IOException e) {
                    throw new PgpGeneralException(getString(R.string.error_fileDeleteFailed,
                            deleteFile));
                }

                /* Output */
                sendMessageToHandler(KeychainIntentServiceHandler.MESSAGE_OKAY);
            } catch (Exception e) {
                sendErrorToHandler(e);
            }
        } else if (ACTION_IMPORT_KEYRING.equals(action)) {
            try {

                /* Input */
                int target = data.getInt(TARGET);

                // int keyType = Id.type.public_key;
                // if (data.containsKey(IMPORT_KEY_TYPE)) {
                // keyType = data.getInt(IMPORT_KEY_TYPE);
                // }

                /* Operation */
                InputStream inStream = null;
                long inLength = -1;
                InputData inputData = null;
                switch (target) {
                case TARGET_BYTES: /* import key from bytes directly */
                    byte[] bytes = data.getByteArray(IMPORT_BYTES);

                    inStream = new ByteArrayInputStream(bytes);
                    inLength = bytes.length;

                    inputData = new InputData(inStream, inLength);

                    break;
                case TARGET_FILE: /* import key from file */
                    String inputFile = data.getString(IMPORT_FILENAME);

                    inStream = new FileInputStream(inputFile);
                    File file = new File(inputFile);
                    inLength = file.length();
                    inputData = new InputData(inStream, inLength);

                    break;

                case TARGET_STREAM:
                    // TODO: not implemented
                    break;
                }

                Bundle resultData = new Bundle();

                PgpImportExport pgpImportExport = new PgpImportExport(this, this);
                resultData = pgpImportExport.importKeyRings(inputData);

                sendMessageToHandler(KeychainIntentServiceHandler.MESSAGE_OKAY, resultData);
            } catch (Exception e) {
                sendErrorToHandler(e);
            }
        } else if (ACTION_EXPORT_KEYRING.equals(action)) {
            try {

                /* Input */
                int keyType = Id.type.public_key;
                if (data.containsKey(EXPORT_KEY_TYPE)) {
                    keyType = data.getInt(EXPORT_KEY_TYPE);
                }

                String outputFile = data.getString(EXPORT_FILENAME);

                boolean exportAll = data.getBoolean(EXPORT_ALL);
                long keyRingMasterKeyId = -1;
                if (!exportAll) {
                    keyRingMasterKeyId = data.getLong(EXPORT_KEY_RING_MASTER_KEY_ID);
                }

                /* Operation */

                // check if storage is ready
                if (!FileHelper.isStorageMounted(outputFile)) {
                    throw new PgpGeneralException(getString(R.string.error_externalStorageNotReady));
                }

                // OutputStream
                FileOutputStream outStream = new FileOutputStream(outputFile);

                ArrayList<Long> keyRingMasterKeyIds = new ArrayList<Long>();
                if (exportAll) {
                    // get all key ring row ids based on export type

                    if (keyType == Id.type.public_key) {
                        keyRingMasterKeyIds = ProviderHelper.getPublicKeyRingsMasterKeyIds(this);
                    } else {
                        keyRingMasterKeyIds = ProviderHelper.getSecretKeyRingsMasterKeyIds(this);
                    }
                } else {
                    keyRingMasterKeyIds.add(keyRingMasterKeyId);
                }

                Bundle resultData = new Bundle();

                PgpImportExport pgpImportExport = new PgpImportExport(this, this);
                resultData = pgpImportExport
                        .exportKeyRings(keyRingMasterKeyIds, keyType, outStream);

                sendMessageToHandler(KeychainIntentServiceHandler.MESSAGE_OKAY, resultData);
            } catch (Exception e) {
                sendErrorToHandler(e);
            }
        } else if (ACTION_UPLOAD_KEYRING.equals(action)) {
            try {

                /* Input */
                int keyRingRowId = data.getInt(UPLOAD_KEY_KEYRING_ROW_ID);
                String keyServer = data.getString(UPLOAD_KEY_SERVER);

                /* Operation */
                HkpKeyServer server = new HkpKeyServer(keyServer);

                PGPPublicKeyRing keyring = ProviderHelper.getPGPPublicKeyRingByRowId(this,
                        keyRingRowId);
                if (keyring != null) {
                    PgpImportExport pgpImportExport = new PgpImportExport(this, null);

                    boolean uploaded = pgpImportExport.uploadKeyRingToServer(server,
                            (PGPPublicKeyRing) keyring);
                    if (!uploaded) {
                        throw new PgpGeneralException("Unable to export key to selected server");
                    }
                }

                sendMessageToHandler(KeychainIntentServiceHandler.MESSAGE_OKAY);
            } catch (Exception e) {
                sendErrorToHandler(e);
            }
        } else if (ACTION_QUERY_KEYRING.equals(action)) {
            try {

                /* Input */
                int queryType = data.getInt(QUERY_KEY_TYPE);
                String keyServer = data.getString(QUERY_KEY_SERVER);

                String queryString = data.getString(QUERY_KEY_STRING);
                long keyId = data.getLong(QUERY_KEY_ID);

                /* Operation */
                Bundle resultData = new Bundle();

                HkpKeyServer server = new HkpKeyServer(keyServer);
                if (queryType == Id.keyserver.search) {
                    ArrayList<KeyInfo> searchResult = server.search(queryString);

                    resultData.putParcelableArrayList(RESULT_QUERY_KEY_SEARCH_RESULT, searchResult);
                } else if (queryType == Id.keyserver.get) {
                    String keyData = server.get(keyId);

                    resultData.putString(RESULT_QUERY_KEY_DATA, keyData);
                }

                sendMessageToHandler(KeychainIntentServiceHandler.MESSAGE_OKAY, resultData);
            } catch (Exception e) {
                sendErrorToHandler(e);
            }
        } else if (ACTION_SIGN_KEYRING.equals(action)) {
            try {

                /* Input */
                long masterKeyId = data.getLong(SIGN_KEY_MASTER_KEY_ID);
                long pubKeyId = data.getLong(SIGN_KEY_PUB_KEY_ID);

                /* Operation */
                String signaturePassPhrase = PassphraseCacheService.getCachedPassphrase(this,
                        masterKeyId);

                PgpKeyOperation keyOperation = new PgpKeyOperation(this, this);
                PGPPublicKeyRing signedPubKeyRing = keyOperation.signKey(masterKeyId, pubKeyId,
                        signaturePassPhrase);

                // store the signed key in our local cache
                PgpImportExport pgpImportExport = new PgpImportExport(this, null);
                int retval = pgpImportExport.storeKeyRingInCache(signedPubKeyRing);
                if (retval != Id.return_value.ok && retval != Id.return_value.updated) {
                    throw new PgpGeneralException("Failed to store signed key in local cache");
                }

                sendMessageToHandler(KeychainIntentServiceHandler.MESSAGE_OKAY);
            } catch (Exception e) {
                sendErrorToHandler(e);
            }
        }
    }

    private void sendErrorToHandler(Exception e) {
        Log.e(Constants.TAG, "ApgService Exception: ", e);
        e.printStackTrace();

        Bundle data = new Bundle();
        data.putString(KeychainIntentServiceHandler.DATA_ERROR, e.getMessage());
        sendMessageToHandler(KeychainIntentServiceHandler.MESSAGE_EXCEPTION, null, data);
    }

    private void sendMessageToHandler(Integer arg1, Integer arg2, Bundle data) {
        Message msg = Message.obtain();
        msg.arg1 = arg1;
        if (arg2 != null) {
            msg.arg2 = arg2;
        }
        if (data != null) {
            msg.setData(data);
        }

        try {
            mMessenger.send(msg);
        } catch (RemoteException e) {
            Log.w(Constants.TAG, "Exception sending message, Is handler present?", e);
        } catch (NullPointerException e) {
            Log.w(Constants.TAG, "Messenger is null!", e);
        }
    }

    private void sendMessageToHandler(Integer arg1, Bundle data) {
        sendMessageToHandler(arg1, null, data);
    }

    private void sendMessageToHandler(Integer arg1) {
        sendMessageToHandler(arg1, null, null);
    }

    /**
     * Set progress of ProgressDialog by sending message to handler on UI thread
     */
    public void setProgress(String message, int progress, int max) {
        Log.d(Constants.TAG, "Send message by setProgress with progress=" + progress + ", max="
                + max);

        Bundle data = new Bundle();
        if (message != null) {
            data.putString(KeychainIntentServiceHandler.DATA_MESSAGE, message);
        }
        data.putInt(KeychainIntentServiceHandler.DATA_PROGRESS, progress);
        data.putInt(KeychainIntentServiceHandler.DATA_PROGRESS_MAX, max);

        sendMessageToHandler(KeychainIntentServiceHandler.MESSAGE_UPDATE_PROGRESS, null, data);
    }

    public void setProgress(int resourceId, int progress, int max) {
        setProgress(getString(resourceId), progress, max);
    }

    public void setProgress(int progress, int max) {
        setProgress(null, progress, max);
    }
}
