/*
 * Copyright 2020 Aniket Kumar <anikketkumar786@gmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License or (at your option) version 3 or any later version
 * accepted by the membership of KDE e.V. (or its successor approved
 * by the membership of KDE e.V.), which shall act as a proxy
 * defined in Section 14 of version 3 of the license.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kde.kdeconnect.Plugins.SMSPlugin;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;

import com.klinker.android.send_message.Message;
import com.klinker.android.send_message.MmsSentReceiver;
import com.klinker.android.send_message.Settings;
import com.klinker.android.send_message.Transaction;
import com.klinker.android.send_message.Utils;

import android.content.ContentUris;
import android.content.ContentValues;
import android.provider.Telephony;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.RequiresApi;

import org.apache.commons.lang3.ArrayUtils;
import org.kde.kdeconnect.Helpers.SMSHelper;
import org.kde.kdeconnect.Helpers.TelephonyHelper;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class SmsMmsUtils {

    private static final String SENDING_MESSAGE = "Sending message";

    /**
     * Sends SMS or MMS message.
     *
     * @param context       context in which the method is called.
     * @param textMessage   text body of the message to be sent.
     * @param addressList   List of addresses.
     * @param subID         Note that here subID is of type int and not long because klinker library requires it as int
     *                      I don't really know the exact reason why they implemented it as int instead of long
     */
    public static void sendMessage(Context context, String textMessage, List<SMSHelper.Address> addressList, int subID) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean longTextAsMms = prefs.getBoolean(context.getString(R.string.set_long_text_as_mms), false);
        boolean groupMessageAsMms = prefs.getBoolean(context.getString(R.string.set_group_message_as_mms), true);
        int sendLongAsMmsAfter = Integer.parseInt(
                prefs.getString(context.getString(R.string.convert_to_mms_after),
                context.getString(R.string.convert_to_mms_after_default)));

        try {
            Settings settings = new Settings();
            TelephonyHelper.ApnSetting apnSettings = TelephonyHelper.getPreferredApn(context, subID);

            if (apnSettings != null) {
                settings.setMmsc(apnSettings.getMmsc().toString());
                settings.setProxy(apnSettings.getMmsProxyAddressAsString());
                settings.setPort(Integer.toString(apnSettings.getMmsProxyPort()));
            } else {
                settings.setUseSystemSending(true);
            }

            if (Utils.isDefaultSmsApp(context)) {
                settings.setSendLongAsMms(longTextAsMms);
                settings.setSendLongAsMmsAfter(sendLongAsMmsAfter);
            }

            settings.setGroup(groupMessageAsMms);

            if (subID != -1) {
                settings.setSubscriptionId(subID);
            }

            Transaction transaction = new Transaction(context, settings);
            transaction.setExplicitBroadcastForSentSms(new Intent(context, SmsSentReceiver.class));
            transaction.setExplicitBroadcastForSentMms(new Intent(context, MmsSentReceiver.class));

            List<String> addresses = new ArrayList<>();
            for (SMSHelper.Address address : addressList) {
                addresses.add(address.toString());
            }

            Message message = new Message(textMessage, addresses.toArray(ArrayUtils.EMPTY_STRING_ARRAY));
            message.setSave(true);

            // Sending MMS on android requires the app to be set as the default SMS app,
            // but sending SMS doesn't needs the app to be set as the default app.
            // This is the reason why there are separate branch handling for SMS and MMS.
            if (transaction.checkMMS(message)) {
                if (Utils.isDefaultSmsApp(context)) {
                    if (Utils.isMobileDataEnabled(context)) {
                        com.klinker.android.logger.Log.v("", "Sending new MMS");
                        transaction.sendNewMessage(message, Transaction.NO_THREAD_ID);
                    }
                } else {
                    com.klinker.android.logger.Log.v(SENDING_MESSAGE, "KDE Connect is not set to default SMS app.");
                    //TODO: Notify other end that they need to enable the mobile data in order to send MMS
                }
            } else {
                com.klinker.android.logger.Log.v(SENDING_MESSAGE, "Sending new SMS");
                transaction.sendNewMessage(message, Transaction.NO_THREAD_ID);
            }
            //TODO: Notify other end
        } catch (Exception e) {
            //TODO: Notify other end
            com.klinker.android.logger.Log.e(SENDING_MESSAGE, "Exception", e);
        }
    }

    /**
     * Marks a conversation as read in the database.
     *
     * @param context      the context to get the content provider with.
     * @param recipients   the phone numbers to find the conversation with.
     */
    public static void markConversationRead(Context context, HashSet<String> recipients) {
        new Thread() {
            @RequiresApi(api = Build.VERSION_CODES.KITKAT)
            @Override
            public void run() {
                try {
                    long threadId = Utils.getOrCreateThreadId(context, recipients);
                    markAsRead(context, ContentUris.withAppendedId(Telephony.Threads.CONTENT_URI, threadId), threadId);
                } catch (Exception e) {
                    // the conversation doesn't exist
                    e.printStackTrace();
                }
            }
        }.start();
    }

    private static void markAsRead(Context context, Uri uri, long threadId) {
        Log.v("SMSPlugin", "marking thread with threadId " + threadId + " as read at Uri" + uri);

        if (uri != null && context != null) {
            ContentValues values = new ContentValues(2);
            values.put("read", 1);
            values.put("seen", 1);

            context.getContentResolver().update(uri, values, "(read=0 OR seen=0)", null);
        }
    }
}
