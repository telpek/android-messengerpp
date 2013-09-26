package org.solovyev.android.messenger.accounts.connection;

import android.content.Context;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;
import org.solovyev.android.messenger.accounts.Account;
import org.solovyev.android.messenger.accounts.AccountConnectionException;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static org.robolectric.Robolectric.application;

@RunWith(RobolectricTestRunner.class)
public class AccountConnectionsTest {

	@Nonnull
	private AccountConnections connections;

	@Nonnull
	private Account account;

	@Nonnull
	private AccountConnection connection;

	@Before
	public void setUp() throws Exception {
		connections = new AccountConnections(application);
		connections.setExecutor(new Executor() {
			@Override
			public void execute(@Nonnull Runnable command) {
				command.run();
			}
		});
		account = newMockAccountWithStaticConnection();
		connection = prepareStaticConnectionForAccount(account);
	}

	@Nonnull
	private static Account newMockAccountWithStaticConnection() {
		final Account account = mock(Account.class);
		when(account.isEnabled()).thenReturn(true);
		prepareStaticConnectionForAccount(account);
		return account;
	}

	@Nonnull
	private static AccountConnection prepareStaticConnectionForAccount(@Nonnull final Account account) {
		final AccountConnection connection = newMockConnection(account);
		when(account.newConnection(any(Context.class))).thenReturn(connection);
		return connection;
	}

	@Nonnull
	private static Account newMockAccount() {
		final Account account = mock(Account.class);
		when(account.isEnabled()).thenReturn(true);
		prepareConnectionForAccount(account);
		return account;
	}

	private static void prepareConnectionForAccount(@Nonnull final Account account) {
		when(account.newConnection(any(Context.class))).thenAnswer(new Answer<Object>() {
			@Override
			public Object answer(InvocationOnMock invocation) throws Throwable {
				return newMockConnection(account);
			}
		});
	}

	@Nonnull
	private static AccountConnection newMockConnection(@Nonnull Account account) {
		final AccountConnection connection = mock(AccountConnection.class);

		when(connection.isStopped()).thenReturn(true);
		when(connection.isInternetConnectionRequired()).thenReturn(true);
		try {
			doAnswer(new Answer() {
				@Override
				public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
					when(connection.isStopped()).thenReturn(false);
					return null;
				}
			}).when(connection).start();
		} catch (AccountConnectionException e) {
			fail(e.getMessage());
		}

		doAnswer(new Answer() {
			@Override
			public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
				when(connection.isStopped()).thenReturn(true);
				return null;
			}
		}).when(connection).stop();
		when(connection.getAccount()).thenReturn(account);

		return connection;
	}

	@Test
	public void testShouldStartConnectionIfNetworkConnectionExists() throws Exception {
		connections.startConnectionsFor(Arrays.asList(account), true);
		verify(connection, times(1)).start();
	}

	@Test
	public void testShouldNotStartConnectionIfNetworkConnectionDoesntExist() throws Exception {
		connections.startConnectionsFor(Arrays.asList(account), false);
		verify(connection, times(0)).start();
	}

	@Test
	public void testShouldNotStartConnectionIfConnectionIsRunning() throws Exception {
		when(connection.isStopped()).thenReturn(false);
		connections.startConnectionsFor(Arrays.asList(account), false);
		verify(connection, times(0)).start();
	}

	@Test
	public void testShouldReuseConnectionIfExists() throws Exception {
		connections.startConnectionsFor(Arrays.asList(account), true);
		verify(connection, times(1)).start();
		verify(account, times(1)).newConnection(any(Context.class));

		connections.startConnectionsFor(Arrays.asList(account), true);
		verify(account, times(1)).newConnection(any(Context.class));
	}

	@Test
	public void testShouldRestartConnectionIfExistsAndStopped() throws Exception {
		connections.startConnectionsFor(Arrays.asList(account), true);
		verify(connection, times(1)).start();
		verify(account, times(1)).newConnection(any(Context.class));
		connection.stop();

		connections.startConnectionsFor(Arrays.asList(account), true);
		verify(connection, times(2)).start();
	}

	@Test
	public void testShouldNotRestartConnectionIfExistsAndNotStopped() throws Exception {
		connections.startConnectionsFor(Arrays.asList(account), true);
		verify(connection, times(1)).start();
		verify(account, times(1)).newConnection(any(Context.class));

		connections.startConnectionsFor(Arrays.asList(account), true);
		verify(connection, times(1)).start();
	}

	@Test
	public void testShouldNotRestartConnectionIfExistsAndStoppedButNoInternet() throws Exception {
		connections.startConnectionsFor(Arrays.asList(account), true);
		verify(connection, times(1)).start();
		verify(account, times(1)).newConnection(any(Context.class));
		connection.stop();

		connections.startConnectionsFor(Arrays.asList(account), false);
		verify(connection, times(1)).start();
	}

	@Test
	public void testShouldStopAllConnections() throws Exception {
		final Connections c = new Connections(10);

		this.connections.startConnectionsFor(c.accounts, true);
		for(int i = 0; i < c.count; i++) {
			final AccountConnection connection = c.getConnection(i);
			verify(connection, times(1)).start();
			assertFalse(connection.isStopped());
		}

		for(int i = 0; i < c.count/2; i++) {
			c.getConnection(i).stop();
		}

		this.connections.tryStopAll();
		c.assertAllStopped();
	}

	@Test
	public void testShouldStopInternetDependantConnections() throws Exception {
		final Connections c = new Connections(10);

		int runningUpTo = c.count / 2;
		for(int i = 0; i < runningUpTo; i++) {
			when(c.getConnection(i).isInternetConnectionRequired()).thenReturn(false);
		}

		connections.startConnectionsFor(c.accounts, true);
		c.assertAllRunning();

		connections.onNoInternetConnection();
		c.assertRunningUpTo(runningUpTo);
		c.assertStoppedFrom(runningUpTo);
	}

	private static final class Connections {
		@Nonnull
		private final List<Account> accounts = new ArrayList<Account>();

		@Nonnull
		private final List<AccountConnection> connections = new ArrayList<AccountConnection>();

		private final int count;

		private Connections(int count) {
			this.count = count;
			for(int i = 0; i < count; i++) {
				final Account account = newMockAccountWithStaticConnection();
				accounts.add(account);
				connections.add(account.newConnection(application));
			}
		}

		@Nonnull
		public AccountConnection getConnection(int i) {
			return this.connections.get(i);
		}

		@Nonnull
		public Account getAccount(int i) {
			return this.accounts.get(i);
		}

		public void assertAllStopped() {
			assertStoppedUpTo(this.count);
		}

		public void assertStoppedFrom(int from) {
			for(int i = from; i < this.count; i++) {
				final AccountConnection connection = connections.get(i);
				verify(connection, times(1)).stop();
				assertTrue(connection.isStopped());
			}
		}

		public void assertStoppedUpTo(int upTo) {
			for(int i = 0; i < upTo; i++) {
				final AccountConnection connection = connections.get(i);
				verify(connection, times(1)).stop();
				assertTrue(connection.isStopped());
			}
		}

		public void assertAllRunning() {
			assertRunningUpTo(this.count);
		}

		public void assertRunningFrom(int from) {
			for(int i = from; i < this.count; i++) {
				final AccountConnection connection = connections.get(i);
				assertFalse(connection.isStopped());
			}
		}

		public void assertRunningUpTo(int upTo) {
			for(int i = 0; i < upTo; i++) {
				final AccountConnection connection = connections.get(i);
				assertFalse(connection.isStopped());
			}
		}
	}
}
