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

package org.solovyev.android.messenger.realms.vk.registration;

import com.google.inject.Singleton;
import org.solovyev.android.messenger.registration.RegistrationService;

import javax.annotation.Nonnull;

/**
 * User: serso
 * Date: 5/25/12
 * Time: 8:23 PM
 */

@Singleton
public class DummyRegistrationService implements RegistrationService {

	@Override
	public void requestVerificationCode(@Nonnull String phoneNumber, @Nonnull String firstName, @Nonnull String lastName) {
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	public boolean checkVerificationCode(@Nonnull String verificationCode) {
		return true;  //To change body of implemented methods use File | Settings | File Templates.
	}
}
