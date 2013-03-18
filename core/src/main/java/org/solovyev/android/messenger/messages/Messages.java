package org.solovyev.android.messenger.messages;

import android.text.Html;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.solovyev.android.messenger.chats.Chat;
import org.solovyev.android.messenger.chats.ChatMessage;
import org.solovyev.android.messenger.entities.Entity;
import org.solovyev.android.messenger.users.User;
import org.solovyev.android.messenger.users.Users;
import org.solovyev.common.text.Strings;

import javax.annotation.Nonnull;

/**
 * User: serso
 * Date: 3/7/13
 * Time: 3:50 PM
 */
public final class Messages {

    private Messages() {
        throw new AssertionError();
    }

    @Nonnull
    public static CharSequence getMessageTime(@Nonnull ChatMessage message) {
        final LocalDate sendDate = message.getSendDate().toLocalDate();
        final LocalDate today = DateTime.now().toLocalDate();
        final LocalDate yesterday = today.minusDays(1);

        if (sendDate.toDateTimeAtStartOfDay().compareTo(today.toDateTimeAtStartOfDay()) == 0) {
            // today
            // print time
            return DateTimeFormat.shortTime().print(message.getSendDate());
        } else if (sendDate.toDateTimeAtStartOfDay().compareTo(yesterday.toDateTimeAtStartOfDay()) == 0) {
            // yesterday
            // todo serso: translate
            return "Yesterday";// + ", " + DateTimeFormat.shortTime().print(sendDate);
        } else {
            // the days before yesterday
            return DateTimeFormat.shortDate().print(sendDate);
        }
    }

    @Nonnull
    public static CharSequence getMessageTitle(@Nonnull Chat chat, @Nonnull ChatMessage message, @Nonnull User user) {
        final String authorName = getMessageAuthorDisplayName(chat, message, user);
        if (Strings.isEmpty(authorName)) {
            return Html.fromHtml(message.getBody());
        } else {
            return authorName + ": " + Html.fromHtml(message.getBody());
        }
    }

    @Nonnull
    private static String getMessageAuthorDisplayName(@Nonnull Chat chat, @Nonnull ChatMessage message, @Nonnull User user) {
        final Entity author = message.getAuthor();
        if (user.getEntity().equals(author)) {
            // todo serso: translate
            return "Me";
        } else {
            if (!chat.isPrivate()) {
                return Users.getDisplayNameFor(author);
            } else {
                return "";
            }
        }
    }
}