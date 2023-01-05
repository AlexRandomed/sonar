/*
 * Copyright © "Open Digital Education", 2016
 *
 * This program is published by "Open Digital Education".
 * You must indicate the name of the software and the company in any production /contribution
 * using the software and indicate on the home page of the software industry in question,
 * "powered by Open Digital Education" with a reference to the website: https://opendigitaleducation.com/.
 *
 * This program is free software, licensed under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, version 3 of the License.
 *
 * You can redistribute this application and/or modify it since you respect the terms of the GNU Affero General Public License.
 * If you modify the source code and then use this modified source code in your creation, you must make available the source code of your modifications.
 *
 * You should have received a copy of the GNU Affero General Public License along with the software.
 * If not, please see : <http://www.gnu.org/licenses/>. Full compliance requires reading the terms of this license and following its directives.

 */

package org.entcore.common.emailstate;

import org.entcore.common.emailstate.impl.DefaultEmailValidationService;
import org.entcore.common.emailstate.impl.DefaultUserValidationService;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.LocalMap;

public class EmailStateFactory {
	private Vertx vertx;
	private JsonObject config;
	private JsonObject moduleConfig;
	private UserValidationService handler;

	public EmailStateFactory() {
	}

	private static class EmailStateFactoryHolder {
		private static final EmailStateFactory instance = new EmailStateFactory();
	}

	public static EmailStateFactory getFactory() {
		return EmailStateFactoryHolder.instance;
	}

	public void init(Vertx vertx, JsonObject config) {
		this.vertx = vertx;
		this.moduleConfig = config;
		this.config = config.getJsonObject("emailValidationConfig");
		if (this.config == null ) {
			LocalMap<Object, Object> server = vertx.sharedData().getLocalMap("server");
			String s = (String) server.get("emailValidationConfig");
			if (s != null) {
				this.config = new JsonObject(s);
			} else {
				this.config = new JsonObject();
			}
		}
	}

	public static UserValidationService getInstance() {
		return getFactory().getService();
	}

	public UserValidationService getService() {
		if (handler == null ) {
			handler = new DefaultUserValidationService( config, new DefaultEmailValidationService(vertx, moduleConfig) );
		}
		return handler;
	}
}
