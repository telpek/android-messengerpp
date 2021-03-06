/*
 * Copyright 2013 serso aka se.solovyev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.solovyev.android.messenger.icons;

import android.content.Context;
import android.widget.ImageView;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.solovyev.android.http.ImageLoader;
import org.solovyev.android.messenger.users.User;
import org.solovyev.common.text.Strings;

/**
 * User: serso
 * Date: 3/14/13
 * Time: 8:07 PM
 */
public final class HttpRealmIconService implements RealmIconService {

	@Nonnull
	private final Context context;

	@Nonnull
	private final ImageLoader imageLoader;

	private final int defaultUserIconResId;

	private final int defaultUsersIconResId;

	@Nonnull
	private final UrlGetter iconUrlGetter;

	@Nonnull
	private final UrlGetter photoUrlGetter;

	public HttpRealmIconService(@Nonnull Context context,
								@Nonnull ImageLoader imageLoader,
								int defaultUserIconResId,
								int defaultUsersIconResId,
								@Nonnull UrlGetter iconUrlGetter,
								@Nonnull UrlGetter photoUrlGetter) {
		this.context = context;
		this.imageLoader = imageLoader;
		this.defaultUserIconResId = defaultUserIconResId;
		this.defaultUsersIconResId = defaultUsersIconResId;
		this.iconUrlGetter = iconUrlGetter;
		this.photoUrlGetter = photoUrlGetter;
	}

	@Override
	public void setUserIcon(@Nonnull User user, @Nonnull ImageView imageView) {
		final String userIconUrl = iconUrlGetter.getUrl(user);
		if (!Strings.isEmpty(userIconUrl)) {
			assert userIconUrl != null;
			this.imageLoader.loadImage(userIconUrl, imageView, defaultUserIconResId);
		} else {
			imageView.setImageDrawable(context.getResources().getDrawable(defaultUserIconResId));
		}
	}

	@Override
	public void setUserPhoto(@Nonnull User user, @Nonnull ImageView imageView) {
		final String userPhotoUrl = photoUrlGetter.getUrl(user);
		if (!Strings.isEmpty(userPhotoUrl)) {
			assert userPhotoUrl != null;
			this.imageLoader.loadImage(userPhotoUrl, imageView, defaultUserIconResId);
		} else {
			imageView.setImageDrawable(context.getResources().getDrawable(defaultUserIconResId));
		}
	}

	@Override
	public void fetchUsersIcons(@Nonnull List<User> users) {
		for (User contact : users) {
			fetchUserIcon(contact);
		}
	}

	@Override
	public void setUsersIcon(@Nonnull List<User> users, @Nonnull ImageView imageView) {
		imageView.setImageDrawable(context.getResources().getDrawable(defaultUsersIconResId));
	}

	public void fetchUserIcon(@Nonnull User user) {
		final String userIconUrl = iconUrlGetter.getUrl(user);
		if (!Strings.isEmpty(userIconUrl)) {
			assert userIconUrl != null;
			this.imageLoader.loadImage(userIconUrl);
		}
	}

    /*
	**********************************************************************
    *
    *                           STATIC
    *
    **********************************************************************
    */

	public static interface UrlGetter {

		@Nullable
		String getUrl(@Nonnull User user);

	}

	private static final class UrlFromPropertyGetter implements UrlGetter {

		@Nonnull
		private final String propertyName;

		private UrlFromPropertyGetter(@Nonnull String propertyName) {
			this.propertyName = propertyName;
		}

		@Nullable
		@Override
		public String getUrl(@Nonnull User user) {
			return user.getPropertyValueByName(propertyName);
		}
	}

	@Nonnull
	public static UrlGetter newUrlFromPropertyGetter(@Nonnull String propertyName) {
		return new UrlFromPropertyGetter(propertyName);
	}
}
