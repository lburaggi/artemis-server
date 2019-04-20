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
import static nz.co.fortytwo.signalk.artemis.util.Config.AMQ_CONTENT_TYPE_JSON_DELTA;
import static nz.co.fortytwo.signalk.artemis.util.Config.MSG_SRC_BUS;
import static nz.co.fortytwo.signalk.artemis.util.Config.MSG_SRC_TYPE;
import static nz.co.fortytwo.signalk.artemis.util.Config.AMQ_CONTENT_TYPE_N2K;
import static nz.co.fortytwo.signalk.artemis.util.SignalKConstants.UPDATES;
import static nz.co.fortytwo.signalk.artemis.util.SignalKConstants.dot;
import static nz.co.fortytwo.signalk.artemis.util.SignalKConstants.self_str;
import static nz.co.fortytwo.signalk.artemis.util.SignalKConstants.vessels;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.server.transformer.Transformer;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import mjson.Json;
import nz.co.fortytwo.signalk.artemis.graal.ContextHolder;
import nz.co.fortytwo.signalk.artemis.util.Config;
import nz.co.fortytwo.signalk.artemis.util.ConfigConstants;
import nz.co.fortytwo.signalk.artemis.util.SignalKConstants;
import nz.co.fortytwo.signalk.artemis.util.SignalkKvConvertor;
import nz.co.fortytwo.signalk.artemis.util.Util;

/**
 * Processes N2K messages from canboat or similar. Converts the N2k messages to signalk
 * 
 * @author robert 
 * 
 */

public class N2kMsgHandler extends JsBaseHandler {

	private static Logger logger = LogManager.getLogger(N2kMsgHandler.class);
	
	
	public N2kMsgHandler() throws Exception {
		logger.info("Started N2kMsgTransformer with {} JS engine..", pool.getEngineName());
		if (logger.isDebugEnabled())
			logger.debug("Initialising for : {} ", uuid);
		try {

			// start listening
			initSession(null, "internal.n2k",RoutingType.ANYCAST);
		} catch (Exception e) {
			logger.error(e, e);
		}
	}

	@Override
	public void consume(Message message) {
		
		if (!AMQ_CONTENT_TYPE_N2K.equals(message.getStringProperty(AMQ_CONTENT_TYPE)))
			return;
		
		String bodyStr = Util.readBodyBufferToString(message.toCore()).trim();
		if (logger.isDebugEnabled())
			logger.debug("N2K Message: {}", bodyStr);
		
		if (StringUtils.isNotBlank(bodyStr) ) {
			try {
				if (logger.isDebugEnabled())
					logger.debug("Processing N2K: {}",bodyStr);
				
				Json json = null;
				ContextHolder ctx = pool.borrowObject();
				try {	
					Object result = ctx.invokeMember("n2kMapper","toDelta", bodyStr);
	
					if (logger.isDebugEnabled())
						logger.debug("Processed N2K: {} ",result);
	
					if (result == null || StringUtils.isBlank(result.toString()) || result.toString().startsWith("Error")) {
						logger.error("{},{}", bodyStr, result);
						return;
					}
					if (result==null || StringUtils.isBlank(result.toString())|| result.toString().startsWith("WARN")) {
						logger.warn(bodyStr + "," + result);
						return;
					}
					
					json = Json.read(result.toString());
					
				}finally {
					pool.returnObject(ctx);
				}		
				if(json==null || !json.isObject())return;
				
				json.set(SignalKConstants.CONTEXT, vessels + dot + Util.fixSelfKey(self_str));
				
				if (logger.isDebugEnabled())
					logger.debug("Converted N2K msg: {}", json.toString());
				//add type.bus to source
				String type = message.getStringProperty(MSG_SRC_TYPE);
				String bus = message.getStringProperty(MSG_SRC_BUS);
				for(Json j:json.at(UPDATES).asJsonList()){
					Util.convertSource(this, message, j, bus, type);
					//fix the timestamp for demo
					if(Config.getConfigPropertyBoolean(ConfigConstants.DEMO)) {
						if (!j.isObject()) continue;
						j.set(SignalKConstants.timestamp, Util.getIsoTimeString());
					}
				}
				//now its a signalk delta msg
				message.putStringProperty(AMQ_CONTENT_TYPE, AMQ_CONTENT_TYPE_JSON_DELTA);
				SignalkKvConvertor.parseDelta(this,message, json);
				json.clear(true);
			} catch (Exception e) {
				logger.error(e, e);
				
			}
		}
		return;
	}

	

	


}
