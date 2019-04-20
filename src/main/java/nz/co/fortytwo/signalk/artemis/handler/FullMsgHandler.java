/*
 *
 * Copyright (C) 2012-2014 R T Huitema. All Rights Reserved.
 * Web: www.42.co.nz
 * Email: robert@42.co.nz
 * Author: R T Huitema
 *
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
 * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package nz.co.fortytwo.signalk.artemis.handler;

import static nz.co.fortytwo.signalk.artemis.util.Config.AMQ_CONTENT_TYPE;
import static nz.co.fortytwo.signalk.artemis.util.Config.AMQ_CONTENT_TYPE_JSON_FULL;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import mjson.Json;
import nz.co.fortytwo.signalk.artemis.util.SignalkKvConvertor;
import nz.co.fortytwo.signalk.artemis.util.Util;


/**
 * Processes full format into individual messages.
 * 
 * @author robert
 * 
 */
public class FullMsgHandler extends BaseHandler {

	private static Logger logger = LogManager.getLogger(FullMsgHandler.class);
	
	public FullMsgHandler() {
		super();
		if (logger.isDebugEnabled())
			logger.debug("Initialising for : {} ", uuid);
		try {

			// start listening
			initSession((String)null, "internal.full",RoutingType.ANYCAST);
		} catch (Exception e) {
			logger.error(e, e);
		}
	}

	
	@Override
	public void consume(Message message) {
		
		if (!AMQ_CONTENT_TYPE_JSON_FULL.equals(message.getStringProperty(AMQ_CONTENT_TYPE)))	return;
		Json node = Util.readBodyBuffer( message.toCore());
		
		// deal with full format
		if (Util.isFullFormat(node)) {
			if (logger.isDebugEnabled())
				logger.debug("processing full {} ", node);
			try {
				SignalkKvConvertor.parseFull(this,message, node, "");
				node.clear(true);
			} catch (Exception e) {
				logger.error(e,e);
			}
			
		}
		return;
	}

	
	

}
