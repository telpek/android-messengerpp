package org.solovyev.android.messenger.realms.xmpp;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jivesoftware.smack.Connection;
import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.RosterEntry;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smackx.packet.VCard;
import org.solovyev.android.messenger.accounts.Account;
import org.solovyev.android.messenger.accounts.AccountConnectionException;
import org.solovyev.android.messenger.entities.Entity;
import org.solovyev.android.messenger.entities.EntityImpl;
import org.solovyev.android.messenger.users.AccountUserService;
import org.solovyev.android.messenger.users.User;
import org.solovyev.android.messenger.users.Users;
import org.solovyev.android.properties.AProperty;
import org.solovyev.android.security.base64.ABase64StringEncoder;

import static org.solovyev.android.properties.Properties.newProperty;

/**
 * User: serso
 * Date: 2/24/13
 * Time: 8:45 PM
 */
class XmppAccountUserService extends AbstractXmppRealmService implements AccountUserService {

	@Nonnull
	private static final String TAG = "M++/" + XmppAccountUserService.class.getSimpleName();

	XmppAccountUserService(@Nonnull XmppAccount realm, @Nonnull XmppConnectionAware connectionAware) {
		super(realm, connectionAware);
	}

	@Nonnull
	private static List<User> checkPresenceStatuses(@Nonnull Connection connection, @Nonnull List<User> users, @Nonnull Account account) {
		final List<User> result = new ArrayList<User>(users.size());

		final Roster roster = connection.getRoster();
		for (final User user : users) {
			result.add(checkPresence(account, roster, user));
		}

		return result;
	}

	@Nonnull
	private static User checkPresence(@Nonnull Account account, @Nonnull Roster roster, @Nonnull final User user) {
		return user.cloneWithNewStatus(isUserOnline(account, roster, user.getEntity()));
	}

	private static boolean isUserOnline(@Nonnull Account account, @Nonnull Roster roster, @Nonnull final Entity entity) {
		final boolean online;
		if (account.isAccountUser(entity)) {
			// realm user => always online
			online = true;
		} else {
			final RosterEntry entry = roster.getEntry(entity.getAccountEntityId());
			if (entry != null) {
				final Presence presence = roster.getPresence(entry.getUser());
				online = presence.isAvailable();
			} else {
				online = false;
			}
		}
		return online;
	}

	@Nullable
	@Override
	public User getUserById(@Nonnull final String accountUserId) throws AccountConnectionException {
		return doOnConnection(new UserLoader(getAccount(), accountUserId));
	}

	@Nonnull
	@Override
	public List<User> getUserContacts(@Nonnull final String accountUserId) throws AccountConnectionException {
		return doOnConnection(new UserContactsLoader(getAccount(), accountUserId));
	}

	@Nonnull
	@Override
	public List<User> checkOnlineUsers(@Nonnull final List<User> users) throws AccountConnectionException {
		return doOnConnection(new OnlineUsersChecker(getAccount(), users));
	}

    /*
	**********************************************************************
    *
    *                           STATIC
    *
    **********************************************************************
    */

	private static class UserLoader implements XmppConnectedCallable<User> {

		@Nonnull
		private final Account account;

		@Nonnull
		private final String accountUserId;

		public UserLoader(@Nonnull Account account, @Nonnull String accountUserId) {
			this.account = account;
			this.accountUserId = accountUserId;
		}

		@Override
		public User call(@Nonnull Connection connection) throws AccountConnectionException, XMPPException {
			final User result;

			if (account.isAccountUser(accountUserId)) {
				// realm user cannot be found in roster ->  information should be loaded separately
				result = toAccountUser(account.getId(), accountUserId, null, connection);
			} else {
				// try to find user contacts in roster
				final RosterEntry entry = connection.getRoster().getEntry(accountUserId);
				if (entry != null) {
					result = toUser(account.getId(), entry.getUser(), entry.getName(), connection, account);
				} else {
					result = null;
				}
			}

			return result;
		}
	}

	@Nonnull
	public static User toUser(@Nonnull String realmId, @Nonnull String accountUserId, @Nullable String name, @Nonnull Connection connection, @Nonnull Account account) throws XMPPException {
		final Entity entity = EntityImpl.newEntity(realmId, accountUserId);
		final List<AProperty> properties = loadUserProperties(true, accountUserId, isUserOnline(account, connection.getRoster(), entity), connection, name);
		return Users.newUser(entity, Users.newNeverSyncedUserSyncData(), properties);
	}

	@Nonnull
	public static User toAccountUser(@Nonnull String realmId, @Nonnull String realmUserId, @Nullable String name, @Nonnull Connection connection) throws XMPPException {
		final Entity entity = EntityImpl.newEntity(realmId, realmUserId);
		final List<AProperty> properties = loadUserProperties(true, realmUserId, true, connection, name);
		return Users.newUser(entity, Users.newNeverSyncedUserSyncData(), properties);
	}

	@Nonnull
	private static List<AProperty> loadUserProperties(boolean loadVCard,
													  @Nonnull String accountUserId,
													  boolean available,
													  @Nonnull Connection connection,
													  @Nullable String name) throws XMPPException {
		final List<AProperty> result = new ArrayList<AProperty>();

		result.add(newProperty(User.PROPERTY_ONLINE, String.valueOf(available)));

		if (loadVCard) {
			try {

				final VCard userCard = new VCard();

				userCard.load(connection, accountUserId);

				result.add(newProperty(User.PROPERTY_FIRST_NAME, userCard.getFirstName()));
				result.add(newProperty(User.PROPERTY_LAST_NAME, userCard.getLastName()));
				result.add(newProperty(User.PROPERTY_NICKNAME, userCard.getNickName()));
				result.add(newProperty(User.PROPERTY_EMAIL, userCard.getEmailHome()));
				result.add(newProperty(User.PROPERTY_PHONE, userCard.getPhoneHome("VOICE")));
				result.add(newProperty(XmppRealm.USER_PROPERTY_AVATAR_HASH, userCard.getAvatarHash()));

				final byte[] avatar = userCard.getAvatar();
				if (avatar != null) {
					result.add(newProperty(XmppRealm.USER_PROPERTY_AVATAR_BASE64, ABase64StringEncoder.getInstance().convert(avatar)));
				}

				// full name
				final String fullName = userCard.getField("FN");
				Users.tryParseNameProperties(result, fullName);
			} catch (XMPPException e) {
				// For some reason vcard loading may return timeout exception => investigate this behaviour
				// NOTE: pidgin loads user information also very slow
				Log.w(TAG, e.getMessage(), e);
			}
		} else {
			Users.tryParseNameProperties(result, name);
		}

		return result;
	}

	private static class UserContactsLoader implements XmppConnectedCallable<List<User>> {

		@Nonnull
		private final Account account;

		@Nonnull
		private final String realmUserId;

		private UserContactsLoader(@Nonnull Account account, @Nonnull String realmUserId) {
			this.account = account;
			this.realmUserId = realmUserId;
		}

		@Override
		public List<User> call(@Nonnull final Connection connection) throws AccountConnectionException, XMPPException {

			if (account.getUser().getEntity().getAccountEntityId().equals(realmUserId)) {
				// realm user => load contacts through the roster
				final Roster roster = connection.getRoster();
				final Collection<RosterEntry> entries = roster.getEntries();

				final List<User> result = new ArrayList<User>(entries.size());
				for (RosterEntry entry : entries) {
					result.add(toUser(account.getId(), entry.getUser(), entry.getName(), connection, account));
				}

				return result;
			} else {
				// we cannot load contacts for contacts in xmpp
				return Collections.emptyList();
			}

		}
	}

	private static class OnlineUsersChecker implements XmppConnectedCallable<List<User>> {

		@Nonnull
		private final Account account;

		@Nonnull
		private final List<User> users;

		public OnlineUsersChecker(@Nonnull Account account, @Nonnull List<User> users) {
			this.account = account;
			this.users = users;
		}

		@Override
		public List<User> call(@Nonnull Connection connection) throws AccountConnectionException, XMPPException {
			return checkPresenceStatuses(connection, users, account);
		}
	}
}
