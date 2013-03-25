package org.solovyev.android.messenger.messages;

import android.database.Cursor;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.solovyev.android.messenger.chats.ChatMessage;
import org.solovyev.android.messenger.entities.Entity;
import org.solovyev.android.messenger.entities.EntityImpl;
import org.solovyev.android.messenger.entities.EntityMapper;
import org.solovyev.android.messenger.users.UserService;
import org.solovyev.common.Converter;

import javax.annotation.Nonnull;

/**
 * User: serso
 * Date: 6/9/12
 * Time: 10:15 PM
 */
public class ChatMessageMapper implements Converter<Cursor, ChatMessage> {

    @Nonnull
    private final UserService userService;

    public ChatMessageMapper(@Nonnull UserService userService) {
        this.userService = userService;
    }

    @Nonnull
    @Override
    public ChatMessage convert(@Nonnull Cursor c) {
        final Entity messageEntity = EntityMapper.newInstanceFor(0).convert(c);

        final String chatId = c.getString(3);

        final LiteChatMessageImpl liteChatMessage = LiteChatMessageImpl.newInstance(messageEntity);
        liteChatMessage.setAuthor(EntityImpl.fromEntityId(c.getString(4)));
        if (!c.isNull(5)) {
            final String recipientId = c.getString(5);
            liteChatMessage.setRecipient(EntityImpl.fromEntityId(recipientId));
        }
        final DateTimeFormatter dateTimeFormatter = ISODateTimeFormat.basicDateTime();

        liteChatMessage.setSendDate(dateTimeFormatter.parseDateTime(c.getString(6)));
        final Long sendTime = c.getLong(7);
        liteChatMessage.setTitle(c.getString(8));
        liteChatMessage.setBody(c.getString(9));
        final boolean read = c.getInt(10) == 1;

        return Messages.newInstance(liteChatMessage, read);
    }
}
