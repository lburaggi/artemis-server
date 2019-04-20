package nz.co.fortytwo.signalk.artemis.handler;

import static nz.co.fortytwo.signalk.artemis.util.Config.AMQ_CONTENT_TYPE;
import static nz.co.fortytwo.signalk.artemis.util.Config.AMQ_CONTENT_TYPE_JSON_DELTA;
import static nz.co.fortytwo.signalk.artemis.util.SignalKConstants.UPDATES;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import mjson.Json;
import nz.co.fortytwo.signalk.artemis.util.Config;
import nz.co.fortytwo.signalk.artemis.util.ConfigConstants;
import nz.co.fortytwo.signalk.artemis.util.SignalKConstants;
import nz.co.fortytwo.signalk.artemis.util.SignalkKvConvertor;
import nz.co.fortytwo.signalk.artemis.util.Util;

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

/**
 * Converts SignalK delta format to map format
 * 
 * @author robert
 * 
 */

public class DeltaMsgHandler extends BaseHandler {

	private static Logger logger = LogManager.getLogger(DeltaMsgHandler.class);

	public DeltaMsgHandler() throws Exception {
		
		if (logger.isDebugEnabled())
			logger.debug("Initialising for : {} ", uuid);
		try {

			// start listening
			initSession(null, "internal.delta",RoutingType.ANYCAST);
		} catch (Exception e) {
			logger.error(e, e);
		}
	}
	/**
	 * Reads Delta format JSON and creates a kv message pairs. Does nothing if json
	 * is not an update, and returns the original message
	 * 
	 * @param node
	 * @return
	 */
	@Override
	public void consume(Message message) {

		// is this s delta message
		if (!AMQ_CONTENT_TYPE_JSON_DELTA.equals(message.getStringProperty(AMQ_CONTENT_TYPE)))
			return;

		Json node = Util.readBodyBuffer(message.toCore());

		if (logger.isDebugEnabled())
			logger.debug("Delta msg: {}", node.toString());

		// deal with diff format
		if (Util.isDelta(node)) {
			// try {
			try {
				processDelta(message, node);
			} catch (Exception e) {
				logger.error(e,e);
			}

		}
		return;
	}

	protected void processDelta(Message message, Json node) throws Exception {
		if (logger.isDebugEnabled())
			logger.debug("Saving delta: {}", node.toString());
		//fix the timestamp for demo
		if(Config.getConfigPropertyBoolean(ConfigConstants.DEMO)&&node.has(UPDATES)) {
			for(Json j:node.at(UPDATES).asJsonList()){
				if (!j.isObject()) continue;
				j.set(SignalKConstants.timestamp, Util.getIsoTimeString());
			}
		}
		SignalkKvConvertor.parseDelta(this,message, node);
		node.clear(true);
	}


}
