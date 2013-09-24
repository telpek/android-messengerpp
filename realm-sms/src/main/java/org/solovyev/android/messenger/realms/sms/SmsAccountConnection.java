package org.solovyev.android.messenger.realms.sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.solovyev.android.messenger.MessengerApplication;
import org.solovyev.android.messenger.accounts.Account;
import org.solovyev.android.messenger.accounts.AccountConnectionException;
import org.solovyev.android.messenger.accounts.AccountException;
import org.solovyev.android.messenger.accounts.connection.LoopedAbstractAccountConnection;
import org.solovyev.android.messenger.chats.Chat;
import org.solovyev.android.messenger.chats.ChatMessage;
import org.solovyev.android.messenger.chats.ChatService;
import org.solovyev.android.messenger.messages.LiteChatMessageImpl;
import org.solovyev.android.messenger.messages.Messages;
import org.solovyev.android.messenger.users.User;
import org.solovyev.android.messenger.users.UserService;
import org.solovyev.android.messenger.users.Users;
import org.solovyev.android.properties.AProperty;
import org.solovyev.common.text.Strings;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;

import static org.solovyev.android.messenger.realms.sms.SmsRealm.INTENT_DELIVERED;
import static org.solovyev.android.messenger.realms.sms.SmsRealm.INTENT_RECEIVED;
import static org.solovyev.android.messenger.realms.sms.SmsRealm.INTENT_SENT;
import static org.solovyev.android.properties.Properties.newProperty;

/**
 * User: serso
 * Date: 5/27/13
 * Time: 9:22 PM
 */
final class SmsAccountConnection extends LoopedAbstractAccountConnection<SmsAccount> {

	@Nullable
	private volatile ReportsBroadcastReceiver receiver;

	SmsAccountConnection(@Nonnull SmsAccount account, @Nonnull Context context) {
		super(account, context);
	}

	@Override
	protected void tryConnect() throws AccountConnectionException {
		if (receiver == null) {
			receiver = new ReportsBroadcastReceiver();
			MessengerApplication.getApp().registerReceiver(receiver, new IntentFilter(INTENT_SENT));
			MessengerApplication.getApp().registerReceiver(receiver, new IntentFilter(INTENT_DELIVERED));

			final IntentFilter intentReceivedFilter = new IntentFilter(INTENT_RECEIVED);
			intentReceivedFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
			MessengerApplication.getApp().registerReceiver(receiver, intentReceivedFilter);
		}
	}

	@Override
	protected void disconnect() {
		unregisterReceiver();
	}

	private void unregisterReceiver() {
		if (receiver != null) {
			MessengerApplication.getApp().unregisterReceiver(receiver);
			receiver = null;
		}
	}

	private class ReportsBroadcastReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			try {
				if (intent.getAction().equals(INTENT_RECEIVED)) {
					onSmsReceived(intent);
				} else {
					// todo serso: sent/delivered report
				}

			} catch (AccountException e) {
				Log.e(SmsRealm.TAG, e.getMessage(), e);
			}
		}
	}

	private void onSmsReceived(@Nonnull Intent intent) throws AccountException {
		final Multimap<String, String> messagesByPhoneNumber = getMessagesByPhoneNumber(intent);

		if (!messagesByPhoneNumber.isEmpty()) {
			final SmsAccount account = getAccount();
			final User user = account.getUser();
			final UserService userService = MessengerApplication.getServiceLocator().getUserService();
			final ChatService chatService = MessengerApplication.getServiceLocator().getChatService();

			final List<User> contacts = userService.getUserContacts(user.getEntity());

			for (Map.Entry<String, Collection<String>> entry : messagesByPhoneNumber.asMap().entrySet()) {
				final User contact = findOrCreateContact(entry.getKey(), contacts);
				final Chat chat = chatService.getPrivateChat(user.getEntity(), contact.getEntity());

				final List<ChatMessage> messages = new ArrayList<ChatMessage>(entry.getValue().size());
				for (String message : entry.getValue()) {
					final ChatMessage chatMessage = toChatMessage(message, account, contact, user);
					if (chatMessage != null) {
						messages.add(chatMessage);
					}
				}

				chatService.saveChatMessages(chat.getEntity(), messages, false);
			}
		}

		// WARNING!!!
		// If you uncomment the next line then received SMS will not be put to incoming.
		// Be careful!
		// this.abortBroadcast();
	}

	@Nonnull
	private Multimap<String, String> getMessagesByPhoneNumber(@Nonnull Intent intent) {
		final Multimap<String, String> smss = ArrayListMultimap.create();

		final Bundle extras = intent.getExtras();
		if (extras != null) {
			final Object[] smsExtras = (Object[]) extras.get(SmsRealm.INTENT_EXTRA_PDUS);

			for (Object smsExtra : smsExtras) {
				final SmsMessage sms = SmsMessage.createFromPdu((byte[]) smsExtra);
				final String message = sms.getMessageBody();
				if (!Strings.isEmpty(message)) {
					smss.put(sms.getOriginatingAddress(), message);
				}
			}
		}

		return smss;
	}

	@Nullable
	private ChatMessage toChatMessage(@Nonnull String message, @Nonnull Account account, @Nonnull User from, @Nonnull User to) {
		if (!Strings.isEmpty(message)) {
			final LiteChatMessageImpl liteChatMessage = Messages.newMessage(MessengerApplication.getApp().getChatMessageService().generateEntity(account));
			liteChatMessage.setBody(message);
			liteChatMessage.setAuthor(account.newUserEntity(from.getEntity().getAccountEntityId()));
			liteChatMessage.setRecipient(account.newUserEntity(to.getEntity().getAccountEntityId()));
			liteChatMessage.setSendDate(DateTime.now());
			// new message by default unread
			return Messages.newInstance(liteChatMessage, false);
		} else {
			return null;
		}
	}

	@Nullable
	private User findOrCreateContact(@Nonnull final String phone, @Nonnull List<User> contacts) {
		User result = findContactByPhone(phone, contacts);
		if (result == null) {
			result = toUser(phone);

			final SmsAccount account = getAccount();
			MessengerApplication.getServiceLocator().getUserService().mergeUserContacts(account.getUser().getEntity(), Arrays.asList(result), false, false);
		}
		return result;
	}

	@Nonnull
	private User toUser(@Nonnull String phone) {
		final SmsAccount account = getAccount();

		final List<AProperty> properties = new ArrayList<AProperty>();
		properties.add(newProperty(User.PROPERTY_PHONES, phone));

		return Users.newUser(account.getRealm().getId(), phone, Users.newNeverSyncedUserSyncData(), properties);
	}

	@Nullable
	private User findContactByPhone(@Nonnull final String phone, @Nonnull List<User> contacts) {
		return Iterables.find(contacts, new Predicate<User>() {
			@Override
			public boolean apply(@Nullable User contact) {
				if (contact != null) {
					// first try find by 'phones' property
					final String phones = contact.getPropertyValueByName(User.PROPERTY_PHONES);
					if (phones != null) {
						for (String contactPhone : Splitter.on(User.PROPERTY_PHONES_SEPARATOR).omitEmptyStrings().split(phones)) {
							if (contactPhone.equals(phone)) {
								return true;
							}
						}
					}

					// then by default phone property
					final String contactPhone = contact.getPropertyValueByName(User.PROPERTY_PHONE);
					if (contactPhone != null && contactPhone.equals(phone)) {
						return true;
					}

					return false;
				} else {
					return false;
				}
			}
		}, null);
	}
}