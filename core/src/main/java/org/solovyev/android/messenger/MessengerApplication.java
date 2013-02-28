package org.solovyev.android.messenger;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import com.google.inject.Inject;
import com.handmark.pulltorefresh.library.internal.LoadingLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joda.time.DateTimeZone;
import org.solovyev.android.Views;
import org.solovyev.android.messenger.chats.ChatService;
import org.solovyev.android.messenger.messages.ChatMessageService;
import org.solovyev.android.messenger.realms.RealmService;
import org.solovyev.android.messenger.security.AuthService;
import org.solovyev.android.messenger.security.AuthServiceFacade;
import org.solovyev.android.messenger.sync.SyncService;
import org.solovyev.android.messenger.users.UserService;
import org.solovyev.android.network.NetworkStateService;
import org.solovyev.android.prefs.BooleanPreference;
import org.solovyev.android.prefs.Preference;
import org.solovyev.android.prefs.StringPreference;
import org.solovyev.common.datetime.FastDateTimeZoneProvider;
import roboguice.RoboGuice;

/**
 * User: serso
 * Date: 5/25/12
 * Time: 8:16 PM
 */
public class MessengerApplication extends Application implements MessengerServiceLocator, MessengerMultiPaneManager {

    /*
    **********************************************************************
    *
    *                           AUTO INJECTED FIELDS
    *
    **********************************************************************
    */

    @Inject
    @NotNull
    private AuthService authService;

    @Inject
    @NotNull
    private AuthServiceFacade authServiceFacade;

    @Inject
    @NotNull
    private ChatMessageService chatMessageService;

    @Inject
    @NotNull
    private UserService userService;

    @Inject
    @NotNull
    private ChatService chatService;

    @Inject
    @NotNull
    private SyncService syncService;

    @Inject
    @NotNull
    private RealmService realmService;

    @Inject
    @NotNull
    private NetworkStateService networkStateService;

    // for tests only!
    @NotNull
    public static MessengerApplication getInstance() {
        return instance;
    }

    @Override
    @NotNull
    public ChatMessageService getChatMessageService() {
        return chatMessageService;
    }

    @Override
    @NotNull
    public UserService getUserService() {
        return userService;
    }

    @Override
    @NotNull
    public ChatService getChatService() {
        return chatService;
    }

    @Override
    @NotNull
    public AuthService getAuthService() {
        return authService;
    }

    @Override
    @NotNull
    public SyncService getSyncService() {
        return syncService;
    }

    @Override
    @NotNull
    public RealmService getRealmService() {
        return realmService;
    }

    @Override
    @NotNull
    public AuthServiceFacade getAuthServiceFacade() {
        return authServiceFacade;
    }

    @Override
    @NotNull
    public NetworkStateService getNetworkStateService() {
        return networkStateService;
    }

    /*
    **********************************************************************
    *
    *                           OWN FIELDS
    *
    **********************************************************************
    */

    @NotNull
    private static MessengerApplication instance;

    public MessengerApplication() {
        instance = this;
    }

    @NotNull
    public static MessengerServiceLocator getServiceLocator() {
        return instance;
    }

    @NotNull
    public static MessengerMultiPaneManager getMultiPaneManager() {
        return instance;
    }

    public static class Preferences {

        public static class Gui {
            public static class Chat {
                public static Preference<Boolean> showUserIcon = BooleanPreference.of("gui.chat.showUserIcon", true);
                public static Preference<Boolean> showContactIconInChat = BooleanPreference.of("gui.chat.showContactIconInChat", true);
                public static Preference<Boolean> showContactIconInPrivateChat = BooleanPreference.of("gui.chat.showContactIconInPrivateChat", true);
                public static Preference<UserIconPosition> userMessagesPosition = StringPreference.ofEnum("gui.chat.userMessagesPosition", UserIconPosition.left, UserIconPosition.class);

                public static enum UserIconPosition {
                    left,
                    right
                }
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // initialize Joda time for android
        System.setProperty("org.joda.time.DateTimeZone.Provider", FastDateTimeZoneProvider.class.getName());

        DateTimeZone.setDefault(DateTimeZone.UTC);

        RoboGuice.getBaseApplicationInjector(this).injectMembers(this);

        // init services
        this.userService.init();
        this.chatService.init();

        // load persistence data
        this.realmService.load();
        this.authService.load();

        this.networkStateService.startListening(this);

        final Intent intent = new Intent();
        intent.setClass(this, MessengerService.class);
        startService(intent);
    }

    /*
    **********************************************************************
    *
    *                           MULTI PANE MANAGER
    *
    **********************************************************************
    */

    @Override
    public boolean isDualPane(@NotNull Activity activity) {
        if (activity.findViewById(R.id.content_second_pane) != null) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean isTriplePane(@NotNull Activity activity) {
        if (activity.findViewById(R.id.content_third_pane) != null) {
            return true;
        } else {
            return false;
        }
    }

    @NotNull
    @Override
    public ViewGroup getFirstPane(@NotNull Activity activity) {
        return (ViewGroup) activity.findViewById(R.id.content_first_pane);
    }

    @NotNull
    @Override
    public ViewGroup getSecondPane(@NotNull Activity activity) {
        return (ViewGroup) activity.findViewById(R.id.content_second_pane);
    }

    @NotNull
    @Override
    public ViewGroup getThirdPane(@NotNull Activity activity) {
        return (ViewGroup) activity.findViewById(R.id.content_third_pane);
    }

    @Override
    public boolean isFirstPane(@Nullable View parent) {
        return parent != null && parent.getId() == R.id.content_first_pane;
    }

    @Override
    public boolean isSecondPane(@Nullable View parent) {
        return parent != null && parent.getId() == R.id.content_second_pane;
    }

    @Override
    public boolean isThirdPane(@Nullable View parent) {
        return parent != null && parent.getId() == R.id.content_third_pane;
    }

    @Override
    public void fillContentPane(@NotNull Activity activity, @Nullable View paneParent, @NotNull View pane) {
        if (this.isDualPane(activity)) {
            if (this.isFirstPane(paneParent)) {
                pane.setBackgroundDrawable(getResources().getDrawable(R.drawable.right_border));
                pane.setPadding(0, 0, 0, 0);
            } else if (this.isSecondPane(paneParent)) {
                pane.setBackgroundColor(getResources().getColor(R.color.base_bg_lighter));
            } else if (this.isTriplePane(activity) && this.isThirdPane(paneParent)) {
                if (Views.getScreenOrientation(activity) == Configuration.ORIENTATION_LANDSCAPE) {
                    pane.setBackgroundDrawable(getResources().getDrawable(R.drawable.left_border));
                } else {
                    pane.setBackgroundColor(getResources().getColor(R.color.base_bg_lighter));
                }
            }
        } else if (this.isFirstPane(paneParent)) {
            pane.setBackgroundColor(getResources().getColor(R.color.base_bg_lighter));
        }
    }

    @Override
    public void fillLoadingLayout(@NotNull Activity activity, @Nullable View paneParent, @NotNull Resources resources, @NotNull LoadingLayout loadingView) {
        loadingView.setTextColor(resources.getColor(R.color.text));
        if (this.isDualPane(activity)) {
            if (this.isFirstPane(paneParent)) {
                loadingView.setBackgroundColor(resources.getColor(R.color.base_bg));
            } else if (this.isSecondPane(paneParent)) {
                loadingView.setBackgroundColor(resources.getColor(R.color.base_bg_lighter));
            } else if (this.isTriplePane(activity) && this.isThirdPane(paneParent)) {
                if (Views.getScreenOrientation(activity) == Configuration.ORIENTATION_LANDSCAPE) {
                    loadingView.setBackgroundColor(resources.getColor(R.color.base_bg));
                } else {
                    loadingView.setBackgroundColor(resources.getColor(R.color.base_bg_lighter));
                }
            }
        } else if (this.isFirstPane(paneParent)) {
            loadingView.setBackgroundColor(resources.getColor(R.color.base_bg_lighter));
        }
    }
}