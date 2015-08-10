/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.providers.contacts;

import android.content.ContentProviderOperation;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.AggregationExceptions;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.DataUsageFeedback;
import android.provider.ContactsContract.MetadataSync;
import android.provider.ContactsContract.RawContacts;
import android.test.MoreAsserts;
import android.test.suitebuilder.annotation.MediumTest;
import com.android.providers.contacts.ContactsDatabaseHelper.MetadataSyncColumns;
import com.android.providers.contacts.testutil.RawContactUtil;
import com.google.android.collect.Lists;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Unit tests for {@link com.android.providers.contacts.ContactMetadataProvider}.
 * <p/>
 * Run the test like this:
 * <code>
 * adb shell am instrument -e class com.android.providers.contacts.ContactMetadataProviderTest -w \
 * com.android.providers.contacts.tests/android.test.InstrumentationTestRunner
 * </code>
 */
@MediumTest
public class ContactMetadataProviderTest extends BaseContactsProvider2Test {
    private static String TEST_ACCOUNT_TYPE1 = "test_account_type1";
    private static String TEST_ACCOUNT_NAME1 = "test_account_name1";
    private static String TEST_DATA_SET1 = "plus";
    private static String TEST_BACKUP_ID1 = "1001";
    private static String TEST_DATA1 = "{\n" +
            "  \"unique_contact_id\": {\n" +
            "    \"account_type\": \"CUSTOM_ACCOUNT\",\n" +
            "    \"custom_account_type\": " + TEST_ACCOUNT_TYPE1 + ",\n" +
            "    \"account_name\": " + TEST_ACCOUNT_NAME1 + ",\n" +
            "    \"contact_id\": " + TEST_BACKUP_ID1 + ",\n" +
            "    \"data_set\": \"GOOGLE_PLUS\"\n" +
            "  },\n" +
            "  \"contact_prefs\": {\n" +
            "    \"send_to_voicemail\": true,\n" +
            "    \"starred\": true,\n" +
            "    \"pinned\": 2\n" +
            "  }\n" +
            "  }";

    private static String TEST_ACCOUNT_TYPE2 = "test_account_type2";
    private static String TEST_ACCOUNT_NAME2 = "test_account_name2";
    private static String TEST_DATA_SET2 = null;
    private static String TEST_BACKUP_ID2 = "1002";
    private static String TEST_DATA2 =  "{\n" +
            "  \"unique_contact_id\": {\n" +
            "    \"account_type\": \"CUSTOM_ACCOUNT\",\n" +
            "    \"custom_account_type\": " + TEST_ACCOUNT_TYPE2 + ",\n" +
            "    \"account_name\": " + TEST_ACCOUNT_NAME2 + ",\n" +
            "    \"contact_id\": " + TEST_BACKUP_ID2 + ",\n" +
            "    \"data_set\": \"GOOGLE_PLUS\"\n" +
            "  },\n" +
            "  \"contact_prefs\": {\n" +
            "    \"send_to_voicemail\": true,\n" +
            "    \"starred\": true,\n" +
            "    \"pinned\": 2\n" +
            "  }\n" +
            "  }";

    private static String SELECTION_BY_TEST_ACCOUNT1 = MetadataSync.ACCOUNT_NAME + "='" +
            TEST_ACCOUNT_NAME1 + "' AND " + MetadataSync.ACCOUNT_TYPE + "='" + TEST_ACCOUNT_TYPE1 +
            "' AND " + MetadataSync.DATA_SET + "='" + TEST_DATA_SET1 + "'";

    private static String SELECTION_BY_TEST_ACCOUNT2 = MetadataSync.ACCOUNT_NAME + "='" +
            TEST_ACCOUNT_NAME2 + "' AND " + MetadataSync.ACCOUNT_TYPE + "='" + TEST_ACCOUNT_TYPE2 +
            "' AND " + MetadataSync.DATA_SET + "='" + TEST_DATA_SET2 + "'";

    private ContactMetadataProvider mContactMetadataProvider;
    private AccountWithDataSet mTestAccount;
    private ContentValues defaultValues;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContactMetadataProvider = (ContactMetadataProvider) addProvider(
                ContactMetadataProvider.class, MetadataSync.METADATA_AUTHORITY);
        // Reset the dbHelper to be the one ContactsProvider2 is using. Before this, two providers
        // are using different dbHelpers.
        mContactMetadataProvider.setDatabaseHelper(((SynchronousContactsProvider2)
                mActor.provider).getDatabaseHelper(getContext()));
        setupData();
    }

    public void testInsertWithInvalidUri() {
        try {
            mResolver.insert(Uri.withAppendedPath(MetadataSync.METADATA_AUTHORITY_URI,
                    "metadata"), getDefaultValues());
            fail("the insert was expected to fail, but it succeeded");
        } catch (IllegalArgumentException e) {
            // this was expected
        }
    }

    public void testUpdateWithInvalidUri() {
        try {
            mResolver.update(Uri.withAppendedPath(MetadataSync.METADATA_AUTHORITY_URI,
                    "metadata"), getDefaultValues(), null, null);
            fail("the update was expected to fail, but it succeeded");
        } catch (IllegalArgumentException e) {
            // this was expected
        }
    }

    public void testGetMetadataByAccount() {
        Cursor c = mResolver.query(MetadataSync.CONTENT_URI, null, SELECTION_BY_TEST_ACCOUNT1,
                null, null);
        assertEquals(1, c.getCount());

        ContentValues expectedValues = defaultValues;
        expectedValues.remove(MetadataSyncColumns.ACCOUNT_ID);
        c.moveToFirst();
        assertCursorValues(c, expectedValues);
        c.close();
    }

    public void testFailOnInsertMetadataForSameAccountIdAndBackupId() {
        // Insert a new metadata with same account and backupId as defaultValues should fail.
        String newData = "{\n" +
                "  \"unique_contact_id\": {\n" +
                "    \"account_type\": \"CUSTOM_ACCOUNT\",\n" +
                "    \"custom_account_type\": " + TEST_ACCOUNT_TYPE1 + ",\n" +
                "    \"account_name\": " + TEST_ACCOUNT_NAME1 + ",\n" +
                "    \"contact_id\": " + TEST_BACKUP_ID1 + ",\n" +
                "    \"data_set\": \"GOOGLE_PLUS\"\n" +
                "  },\n" +
                "  \"contact_prefs\": {\n" +
                "    \"send_to_voicemail\": false,\n" +
                "    \"starred\": false,\n" +
                "    \"pinned\": 1\n" +
                "  }\n" +
                "  }";

        ContentValues  newValues =  new ContentValues();
        newValues.put(MetadataSync.ACCOUNT_NAME, TEST_ACCOUNT_NAME1);
        newValues.put(MetadataSync.ACCOUNT_TYPE, TEST_ACCOUNT_TYPE1);
        newValues.put(MetadataSync.DATA_SET, TEST_DATA_SET1);
        newValues.put(MetadataSync.RAW_CONTACT_BACKUP_ID, TEST_BACKUP_ID1);
        newValues.put(MetadataSync.DATA, newData);
        newValues.put(MetadataSync.DELETED, 0);
        try {
            mResolver.insert(MetadataSync.CONTENT_URI, newValues);
        } catch (Exception e) {
            // Expected.
        }
    }

    public void testQueryRawContact() {
        // Create a raw contact.
        long rawContactId = RawContactUtil.createRawContactWithAccountDataSet(
                mResolver, mTestAccount);
        Cursor c = mContactMetadataProvider.query(RawContacts.CONTENT_URI,
                new String[]{RawContacts._ID}, RawContacts._ID + "=?",
                new String[]{String.valueOf(rawContactId)}, null);
        assertEquals(1, c.getCount());
    }

    public void testQueryData() {
        // Create a raw contact.
        long rawContactId = RawContactUtil.createRawContactWithAccountDataSet(
                mResolver, mTestAccount);
        // Create a data for the raw contact.
        ContentValues values = new ContentValues();
        values.put(Data.RAW_CONTACT_ID, rawContactId);
        values.put(Data.MIMETYPE, "testmimetype");
        values.put(Data.RES_PACKAGE, "oldpackage");
        values.put(Data.IS_PRIMARY, 1);
        values.put(Data.IS_SUPER_PRIMARY, 1);
        values.put(Data.DATA1, "one");
        values.put(Data.DATA2, "two");
        values.put(Data.DATA3, "three");
        values.put(Data.DATA4, "four");
        values.put(Data.DATA5, "five");
        values.put(Data.DATA6, "six");
        values.put(Data.DATA7, "seven");
        values.put(Data.DATA8, "eight");
        values.put(Data.DATA9, "nine");
        values.put(Data.DATA10, "ten");
        values.put(Data.DATA11, "eleven");
        values.put(Data.DATA12, "twelve");
        values.put(Data.DATA13, "thirteen");
        values.put(Data.DATA14, "fourteen");
        values.put(Data.DATA15, "fifteen".getBytes());
        values.put(Data.CARRIER_PRESENCE, ContactsContract.Data.CARRIER_PRESENCE_VT_CAPABLE);
        values.put(Data.SYNC1, "sync1");
        values.put(Data.SYNC2, "sync2");
        values.put(Data.SYNC3, "sync3");
        values.put(Data.SYNC4, "sync4");
        Uri dataUri = mResolver.insert(Data.CONTENT_URI, values);
        long dataId = ContentUris.parseId(dataUri);
        Uri dataUsageUri = ContactsContract.DataUsageFeedback.FEEDBACK_URI.buildUpon()
                .appendPath(String.valueOf(dataId))
                .appendQueryParameter(DataUsageFeedback.USAGE_TYPE,
                        DataUsageFeedback.USAGE_TYPE_CALL)
                .build();
        assertEquals(1, mResolver.update(dataUsageUri, new ContentValues(), null, null));

        // Update data usage for dataId, times used changes from 0 to 1 for "CALL" type.
        Uri uri1 = Data.CONTENT_URI.buildUpon()
                .appendQueryParameter(DataUsageFeedback.USAGE_TYPE,
                        DataUsageFeedback.USAGE_TYPE_CALL)
                .build();
        Cursor c1 = mContactMetadataProvider.query(uri1,
                new String[] {Data.HASH_ID, Data.IS_PRIMARY, Data.IS_SUPER_PRIMARY,
                        Data.LAST_TIME_USED, Data.TIMES_USED}, Data._ID + "=?",
                new String[] {String.valueOf(dataId)}, null);
        assertEquals(1, c1.getCount());
        c1.moveToNext();
        assertEquals(1, c1.getInt(4)); // times_used

        Uri uri2 = Data.CONTENT_URI.buildUpon()
                .appendQueryParameter(DataUsageFeedback.USAGE_TYPE,
                        DataUsageFeedback.USAGE_TYPE_LONG_TEXT)
                .build();
        Cursor c2 = mContactMetadataProvider.query(uri2,
                new String[] {Data.HASH_ID, Data.IS_PRIMARY, Data.IS_SUPER_PRIMARY,
                        Data.LAST_TIME_USED, Data.TIMES_USED}, Data._ID + "=?",
                new String[] {String.valueOf(dataId)}, null);
        assertEquals(1, c2.getCount());
        // Check if times used for "long text" type is still 0.
        c2.moveToNext();
        assertEquals(0, c2.getInt(4)); // times_used
    }

    public void testQueryAggregationExceptions() {
        long rawContactId1 = RawContactUtil.createRawContactWithAccountDataSet(
                mResolver, mTestAccount);
        long rawContactId2 = RawContactUtil.createRawContactWithAccountDataSet(
                mResolver, mTestAccount);
        ContentValues values = new ContentValues();
        values.put(AggregationExceptions.RAW_CONTACT_ID1, rawContactId1);
        values.put(AggregationExceptions.RAW_CONTACT_ID2, rawContactId2);
        values.put(AggregationExceptions.TYPE, 1/*TOGETHER*/);
        mResolver.update(AggregationExceptions.CONTENT_URI, values, null, null);

        Cursor c = mContactMetadataProvider.query(AggregationExceptions.CONTENT_URI,
                new String[] {ContactsContract.AggregationExceptions.TYPE}, null, null, null);
        assertEquals(1, c.getCount());
    }

    public void testInsertAndUpdateMetadataSync() {
        // Create a raw contact with backupId.
        String backupId = "backupId10001";
        long rawContactId = RawContactUtil.createRawContactWithAccountDataSet(
                mResolver, mTestAccount);
        Uri rawContactUri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId);
        ContentValues values = new ContentValues();
        values.put(RawContacts.BACKUP_ID, backupId);
        assertEquals(1, mResolver.update(rawContactUri, values, null, null));

        assertStoredValue(rawContactUri, RawContacts._ID, rawContactId);
        assertStoredValue(rawContactUri, RawContacts.ACCOUNT_TYPE, TEST_ACCOUNT_TYPE1);
        assertStoredValue(rawContactUri, RawContacts.ACCOUNT_NAME, TEST_ACCOUNT_NAME1);
        assertStoredValue(rawContactUri, RawContacts.BACKUP_ID, backupId);
        assertStoredValue(rawContactUri, RawContacts.DATA_SET, TEST_DATA_SET1);

        String deleted = "0";
        String insertJson = "{\n" +
                "  \"unique_contact_id\": {\n" +
                "    \"account_type\": \"CUSTOM_ACCOUNT\",\n" +
                "    \"custom_account_type\": " + TEST_ACCOUNT_TYPE1 + ",\n" +
                "    \"account_name\": " + TEST_ACCOUNT_NAME1 + ",\n" +
                "    \"contact_id\": " + backupId + ",\n" +
                "    \"data_set\": \"GOOGLE_PLUS\"\n" +
                "  },\n" +
                "  \"contact_prefs\": {\n" +
                "    \"send_to_voicemail\": true,\n" +
                "    \"starred\": true,\n" +
                "    \"pinned\": 2\n" +
                "  }\n" +
                "  }";

        // Insert to MetadataSync table.
        ContentValues insertedValues = new ContentValues();
        insertedValues.put(MetadataSync.RAW_CONTACT_BACKUP_ID, backupId);
        insertedValues.put(MetadataSync.ACCOUNT_TYPE, TEST_ACCOUNT_TYPE1);
        insertedValues.put(MetadataSync.ACCOUNT_NAME, TEST_ACCOUNT_NAME1);
        insertedValues.put(MetadataSync.DATA_SET, TEST_DATA_SET1);
        insertedValues.put(MetadataSync.DATA, insertJson);
        insertedValues.put(MetadataSync.DELETED, deleted);
        Uri metadataUri = mResolver.insert(MetadataSync.CONTENT_URI, insertedValues);

        long metadataId = ContentUris.parseId(metadataUri);
        assertEquals(true, metadataId > 0);

        // Check if RawContact table is updated  after inserting metadata.
        assertStoredValue(rawContactUri, RawContacts.ACCOUNT_TYPE, TEST_ACCOUNT_TYPE1);
        assertStoredValue(rawContactUri, RawContacts.ACCOUNT_NAME, TEST_ACCOUNT_NAME1);
        assertStoredValue(rawContactUri, RawContacts.BACKUP_ID, backupId);
        assertStoredValue(rawContactUri, RawContacts.DATA_SET, TEST_DATA_SET1);
        assertStoredValue(rawContactUri, RawContacts.SEND_TO_VOICEMAIL, "1");
        assertStoredValue(rawContactUri, RawContacts.STARRED, "1");
        assertStoredValue(rawContactUri, RawContacts.PINNED, "2");

        // Update the MetadataSync table.
        String updatedJson = "{\n" +
                "  \"unique_contact_id\": {\n" +
                "    \"account_type\": \"CUSTOM_ACCOUNT\",\n" +
                "    \"custom_account_type\": " + TEST_ACCOUNT_TYPE1 + ",\n" +
                "    \"account_name\": " + TEST_ACCOUNT_NAME1 + ",\n" +
                "    \"contact_id\": " + backupId + ",\n" +
                "    \"data_set\": \"GOOGLE_PLUS\"\n" +
                "  },\n" +
                "  \"contact_prefs\": {\n" +
                "    \"send_to_voicemail\": false,\n" +
                "    \"starred\": false,\n" +
                "    \"pinned\": 1\n" +
                "  }\n" +
                "  }";
        ContentValues updatedValues = new ContentValues();
        updatedValues.put(MetadataSync.RAW_CONTACT_BACKUP_ID, backupId);
        updatedValues.put(MetadataSync.ACCOUNT_TYPE, TEST_ACCOUNT_TYPE1);
        updatedValues.put(MetadataSync.ACCOUNT_NAME, TEST_ACCOUNT_NAME1);
        updatedValues.put(MetadataSync.DATA_SET, TEST_DATA_SET1);
        updatedValues.put(MetadataSync.DATA, updatedJson);
        updatedValues.put(MetadataSync.DELETED, deleted);
        assertEquals(1, mResolver.update(MetadataSync.CONTENT_URI, updatedValues, null, null));

        // Check if the update is correct.
        assertStoredValue(rawContactUri, RawContacts.ACCOUNT_TYPE, TEST_ACCOUNT_TYPE1);
        assertStoredValue(rawContactUri, RawContacts.ACCOUNT_NAME, TEST_ACCOUNT_NAME1);
        assertStoredValue(rawContactUri, RawContacts.DATA_SET, TEST_DATA_SET1);
        assertStoredValue(rawContactUri, RawContacts.SEND_TO_VOICEMAIL, "0");
        assertStoredValue(rawContactUri, RawContacts.STARRED, "0");
        assertStoredValue(rawContactUri, RawContacts.PINNED, "1");
    }

    public void testInsertMetadataWithoutUpdateTables() {
        // If raw contact doesn't exist, don't update raw contact tables.
        String backupId = "newBackupId";
        String deleted = "0";
        String insertJson = "{\n" +
                "  \"unique_contact_id\": {\n" +
                "    \"account_type\": \"CUSTOM_ACCOUNT\",\n" +
                "    \"custom_account_type\": " + TEST_ACCOUNT_TYPE1 + ",\n" +
                "    \"account_name\": " + TEST_ACCOUNT_NAME1 + ",\n" +
                "    \"contact_id\": " + backupId + ",\n" +
                "    \"data_set\": \"GOOGLE_PLUS\"\n" +
                "  },\n" +
                "  \"contact_prefs\": {\n" +
                "    \"send_to_voicemail\": true,\n" +
                "    \"starred\": true,\n" +
                "    \"pinned\": 2\n" +
                "  }\n" +
                "  }";

        // Insert to MetadataSync table.
        ContentValues insertedValues = new ContentValues();
        insertedValues.put(MetadataSync.RAW_CONTACT_BACKUP_ID, backupId);
        insertedValues.put(MetadataSync.ACCOUNT_TYPE, TEST_ACCOUNT_TYPE1);
        insertedValues.put(MetadataSync.ACCOUNT_NAME, TEST_ACCOUNT_NAME1);
        insertedValues.put(MetadataSync.DATA_SET, TEST_DATA_SET1);
        insertedValues.put(MetadataSync.DATA, insertJson);
        insertedValues.put(MetadataSync.DELETED, deleted);
        Uri metadataUri = mResolver.insert(MetadataSync.CONTENT_URI, insertedValues);

        long metadataId = ContentUris.parseId(metadataUri);
        assertEquals(true, metadataId > 0);
    }

    public void testFailUpdateDeletedMetadata() {
        String backupId = "backupId001";
        String newData = "{\n" +
                "  \"unique_contact_id\": {\n" +
                "    \"account_type\": \"CUSTOM_ACCOUNT\",\n" +
                "    \"custom_account_type\": " + TEST_ACCOUNT_TYPE1 + ",\n" +
                "    \"account_name\": " + TEST_ACCOUNT_NAME1 + ",\n" +
                "    \"contact_id\": " + backupId + ",\n" +
                "    \"data_set\": \"GOOGLE_PLUS\"\n" +
                "  },\n" +
                "  \"contact_prefs\": {\n" +
                "    \"send_to_voicemail\": false,\n" +
                "    \"starred\": false,\n" +
                "    \"pinned\": 1\n" +
                "  }\n" +
                "  }";

        ContentValues  newValues =  new ContentValues();
        newValues.put(MetadataSync.ACCOUNT_NAME, TEST_ACCOUNT_NAME1);
        newValues.put(MetadataSync.ACCOUNT_TYPE, TEST_ACCOUNT_TYPE1);
        newValues.put(MetadataSync.DATA_SET, TEST_DATA_SET1);
        newValues.put(MetadataSync.RAW_CONTACT_BACKUP_ID, backupId);
        newValues.put(MetadataSync.DATA, newData);
        newValues.put(MetadataSync.DELETED, 1);

        try {
            mResolver.insert(MetadataSync.CONTENT_URI, newValues);
            fail("the update was expected to fail, but it succeeded");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    public void testInsertWithNullData() {
        ContentValues  newValues =  new ContentValues();
        String data = null;
        String backupId = "backupId002";
        newValues.put(MetadataSync.ACCOUNT_NAME, TEST_ACCOUNT_NAME1);
        newValues.put(MetadataSync.ACCOUNT_TYPE, TEST_ACCOUNT_TYPE1);
        newValues.put(MetadataSync.DATA_SET, TEST_DATA_SET1);
        newValues.put(MetadataSync.RAW_CONTACT_BACKUP_ID, backupId);
        newValues.put(MetadataSync.DATA, data);
        newValues.put(MetadataSync.DELETED, 0);

        try {
            mResolver.insert(MetadataSync.CONTENT_URI, newValues);
        } catch (IllegalArgumentException e) {
            // Expected.
        }
    }

    public void testUpdateWithNullData() {
        ContentValues  newValues =  new ContentValues();
        String data = null;
        newValues.put(MetadataSync.ACCOUNT_NAME, TEST_ACCOUNT_NAME1);
        newValues.put(MetadataSync.ACCOUNT_TYPE, TEST_ACCOUNT_TYPE1);
        newValues.put(MetadataSync.DATA_SET, TEST_DATA_SET1);
        newValues.put(MetadataSync.RAW_CONTACT_BACKUP_ID, TEST_BACKUP_ID1);
        newValues.put(MetadataSync.DATA, data);
        newValues.put(MetadataSync.DELETED, 0);

        try {
            mResolver.update(MetadataSync.CONTENT_URI, newValues, null, null);
        } catch (IllegalArgumentException e) {
            // Expected.
        }
    }

    public void testUpdateForMetadataSyncId() {
        Cursor c = mResolver.query(MetadataSync.CONTENT_URI, new String[] {MetadataSync._ID},
                SELECTION_BY_TEST_ACCOUNT1, null, null);
        assertEquals(1, c.getCount());
        c.moveToNext();
        long metadataSyncId = c.getLong(0);
        c.close();

        Uri metadataUri = ContentUris.withAppendedId(MetadataSync.CONTENT_URI, metadataSyncId);
        ContentValues  newValues =  new ContentValues();
        String newData = "{\n" +
                "  \"unique_contact_id\": {\n" +
                "    \"account_type\": \"CUSTOM_ACCOUNT\",\n" +
                "    \"custom_account_type\": " + TEST_ACCOUNT_TYPE1 + ",\n" +
                "    \"account_name\": " + TEST_ACCOUNT_NAME1 + ",\n" +
                "    \"contact_id\": " + TEST_BACKUP_ID1 + ",\n" +
                "    \"data_set\": \"GOOGLE_PLUS\"\n" +
                "  },\n" +
                "  \"contact_prefs\": {\n" +
                "    \"send_to_voicemail\": false,\n" +
                "    \"starred\": false,\n" +
                "    \"pinned\": 1\n" +
                "  }\n" +
                "  }";
        newValues.put(MetadataSync.DATA, newData);
        newValues.put(MetadataSync.DELETED, 0);
        assertEquals(1, mResolver.update(metadataUri, newValues, null, null));
        assertStoredValue(metadataUri, MetadataSync.DATA, newData);
    }

    public void testDeleteMetadata() {
        //insert another metadata for TEST_ACCOUNT
        insertMetadata(TEST_ACCOUNT_NAME1, TEST_ACCOUNT_TYPE1, TEST_DATA_SET1, "2", TEST_DATA1, 0);
        Cursor c = mResolver.query(MetadataSync.CONTENT_URI, null, SELECTION_BY_TEST_ACCOUNT1,
                null, null);
        assertEquals(2, c.getCount());
        int numOfDeletion = mResolver.delete(MetadataSync.CONTENT_URI, SELECTION_BY_TEST_ACCOUNT1,
                null);
        assertEquals(2, numOfDeletion);
        c = mResolver.query(MetadataSync.CONTENT_URI, null, SELECTION_BY_TEST_ACCOUNT1,
                null, null);
        assertEquals(0, c.getCount());
    }

    public void testBulkInsert() {
        Cursor c = mResolver.query(MetadataSync.CONTENT_URI, new String[] {MetadataSync._ID},
                SELECTION_BY_TEST_ACCOUNT1, null, null);
        assertEquals(1, c.getCount());

        ContentValues values1 = getMetadataContentValues(
                TEST_ACCOUNT_NAME1, TEST_ACCOUNT_TYPE1, TEST_DATA_SET1, "123", TEST_DATA1, 0);
        ContentValues values2 = getMetadataContentValues(
                TEST_ACCOUNT_NAME1, TEST_ACCOUNT_TYPE1, TEST_DATA_SET1, "456", TEST_DATA1, 0);
        ContentValues[] values = new ContentValues[] {values1, values2};

        mResolver.bulkInsert(MetadataSync.CONTENT_URI, values);
        c = mResolver.query(MetadataSync.CONTENT_URI, new String[] {MetadataSync._ID},
                SELECTION_BY_TEST_ACCOUNT1, null, null);
        assertEquals(3, c.getCount());
    }

    public void testBatchOperations() throws Exception {
        // Two mentadata_sync entries in the beginning, one for TEST_ACCOUNT1 and another for
        // TEST_ACCOUNT2
        Cursor c = mResolver.query(MetadataSync.CONTENT_URI, new String[] {MetadataSync._ID},
                null, null, null);
        assertEquals(2, c.getCount());

        String updatedData = "{\n" +
                "  \"unique_contact_id\": {\n" +
                "    \"account_type\": \"CUSTOM_ACCOUNT\",\n" +
                "    \"custom_account_type\": " + TEST_ACCOUNT_TYPE1 + ",\n" +
                "    \"account_name\": " + TEST_ACCOUNT_NAME1 + ",\n" +
                "    \"contact_id\": " + TEST_BACKUP_ID1 + ",\n" +
                "    \"data_set\": \"GOOGLE_PLUS\"\n" +
                "  },\n" +
                "  \"contact_prefs\": {\n" +
                "    \"send_to_voicemail\": true,\n" +
                "    \"starred\": false,\n" +
                "    \"pinned\": 5\n" +
                "  }\n" +
                "  }";

        String newBackupId = "2222";
        String newData = "{\n" +
                "  \"unique_contact_id\": {\n" +
                "    \"account_type\": \"CUSTOM_ACCOUNT\",\n" +
                "    \"custom_account_type\": " + TEST_ACCOUNT_TYPE1 + ",\n" +
                "    \"account_name\": " + TEST_ACCOUNT_NAME1 + ",\n" +
                "    \"contact_id\": " + newBackupId + ",\n" +
                "    \"data_set\": \"GOOGLE_PLUS\"\n" +
                "  },\n" +
                "  \"contact_prefs\": {\n" +
                "    \"send_to_voicemail\": true,\n" +
                "    \"starred\": false,\n" +
                "    \"pinned\": 5\n" +
                "  }\n" +
                "  }";

        ArrayList<ContentProviderOperation> ops = Lists.newArrayList();
        ops.add(ContentProviderOperation.newUpdate(MetadataSync.CONTENT_URI)
                .withSelection(SELECTION_BY_TEST_ACCOUNT1, null)
                .withValue(MetadataSync.ACCOUNT_NAME, TEST_ACCOUNT_NAME1)
                .withValue(MetadataSync.ACCOUNT_TYPE, TEST_ACCOUNT_TYPE1)
                .withValue(MetadataSync.DATA_SET, TEST_DATA_SET1)
                .withValue(MetadataSync.RAW_CONTACT_BACKUP_ID, TEST_BACKUP_ID1)
                .withValue(MetadataSync.DATA, updatedData)
                .withValue(MetadataSync.DELETED, 0)
                .build());

        ops.add(ContentProviderOperation.newInsert(MetadataSync.CONTENT_URI)
                .withValue(MetadataSync.ACCOUNT_NAME, TEST_ACCOUNT_NAME1)
                .withValue(MetadataSync.ACCOUNT_TYPE, TEST_ACCOUNT_TYPE1)
                .withValue(MetadataSync.DATA_SET, TEST_DATA_SET1)
                .withValue(MetadataSync.RAW_CONTACT_BACKUP_ID, newBackupId)
                .withValue(MetadataSync.DATA, newData)
                .withValue(MetadataSync.DELETED, 0)
                .build());

        ops.add(ContentProviderOperation.newDelete(MetadataSync.CONTENT_URI)
                .withSelection(SELECTION_BY_TEST_ACCOUNT2, null)
                .build());

        // Batch three operations: update the metadata_entry of TEST_ACCOUNT1; insert one new
        // metadata_entry for TEST_ACCOUNT1; delete metadata_entry of TEST_ACCOUNT2
        mResolver.applyBatch(MetadataSync.METADATA_AUTHORITY, ops);

        // After the batch operations, there should be two metadata_entry for TEST_ACCOUNT1 with
        // new data value and no metadata_entry for TEST_ACCOUNT2.
        c = mResolver.query(MetadataSync.CONTENT_URI, new String[] {MetadataSync.DATA},
                SELECTION_BY_TEST_ACCOUNT1, null, null);
        assertEquals(2, c.getCount());
        Set<String> actualData = new HashSet<>();
        while (c.moveToNext()) {
            actualData.add(c.getString(0));
        }
        c.close();
        MoreAsserts.assertContentsInAnyOrder(actualData, updatedData, newData);

        c = mResolver.query(MetadataSync.CONTENT_URI, new String[] {MetadataSync._ID},
                SELECTION_BY_TEST_ACCOUNT2, null, null);
        assertEquals(0, c.getCount());
    }

    private void setupData() {
        mTestAccount = new AccountWithDataSet(TEST_ACCOUNT_NAME1, TEST_ACCOUNT_TYPE1,
                TEST_DATA_SET1);
        long rawContactId1 = RawContactUtil.createRawContactWithAccountDataSet(
                mResolver, mTestAccount);
        createAccount(TEST_ACCOUNT_NAME1, TEST_ACCOUNT_TYPE1, TEST_DATA_SET1);
        insertMetadata(getDefaultValues());

        // Insert another entry for another account
        createAccount(TEST_ACCOUNT_NAME2, TEST_ACCOUNT_TYPE2, TEST_DATA_SET2);
        insertMetadata(TEST_ACCOUNT_NAME2, TEST_ACCOUNT_TYPE2, TEST_DATA_SET2, TEST_BACKUP_ID2,
                TEST_DATA2, 0);
    }

    private ContentValues getDefaultValues() {
        defaultValues = new ContentValues();
        defaultValues.put(MetadataSync.ACCOUNT_NAME, TEST_ACCOUNT_NAME1);
        defaultValues.put(MetadataSync.ACCOUNT_TYPE, TEST_ACCOUNT_TYPE1);
        defaultValues.put(MetadataSync.DATA_SET, TEST_DATA_SET1);
        defaultValues.put(MetadataSync.RAW_CONTACT_BACKUP_ID, TEST_BACKUP_ID1);
        defaultValues.put(MetadataSync.DATA, TEST_DATA1);
        defaultValues.put(MetadataSync.DELETED, 0);
        return defaultValues;
    }

    private long insertMetadata(String accountName, String accountType, String dataSet,
            String backupId, String data, int deleted) {
        return insertMetadata(getMetadataContentValues(
                accountName, accountType, dataSet, backupId, data, deleted));
    }

    private ContentValues getMetadataContentValues(String accountName, String accountType,
            String dataSet, String backupId, String data, int deleted) {
        ContentValues values = new ContentValues();
        values.put(MetadataSync.ACCOUNT_NAME, accountName);
        values.put(MetadataSync.ACCOUNT_TYPE, accountType);
        values.put(MetadataSync.DATA_SET, dataSet);
        values.put(MetadataSync.RAW_CONTACT_BACKUP_ID, backupId);
        values.put(MetadataSync.DATA, data);
        values.put(MetadataSync.DELETED, deleted);
        return values;
    }

    private long insertMetadata(ContentValues values) {
        return ContentUris.parseId(mResolver.insert(MetadataSync.CONTENT_URI, values));
    }
}
