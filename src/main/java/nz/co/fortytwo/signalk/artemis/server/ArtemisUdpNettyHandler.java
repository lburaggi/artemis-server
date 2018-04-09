/*
 * 
 * Copyright (C) 2012-2014 R T Huitema. All Rights Reserved.
 * Web: www.42.co.nz
 * Email: robert@42.co.nz
 * Author: R T Huitema
 * 
 * This file is part of the signalk-server-java project
 * 
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
 * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
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
package nz.co.fortytwo.signalk.artemis.server;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.CharsetUtil;
import mjson.Json;
import nz.co.fortytwo.signalk.artemis.util.Config;
import nz.co.fortytwo.signalk.artemis.util.Util;
import nz.co.fortytwo.signalk.util.ConfigConstants;

@Sharable
public class ArtemisUdpNettyHandler extends SimpleChannelInboundHandler<DatagramPacket> {

	private Logger logger = LogManager.getLogger(ArtemisUdpNettyHandler.class);
	private BiMap<String, InetSocketAddress> socketList = HashBiMap.create();
	private BiMap<String, ClientSession> sessionList = HashBiMap.create();
	private Map<String, ChannelHandlerContext> channelList = new HashMap<>();
	private BiMap<String, ClientProducer> producerList = HashBiMap.create();

	private String outputType;
	//private ClientConsumer consumer;

	public ArtemisUdpNettyHandler(String outputType) throws Exception {
		this.outputType = outputType;
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		// Send greeting for a new connection.
		NioDatagramChannel udpChannel = (NioDatagramChannel) ctx.channel();
		if (logger.isDebugEnabled())
			logger.debug("channelActive:" + udpChannel.localAddress());
		// TODO: associate the ip with a user?
		// TODO: get user login

		if (logger.isDebugEnabled())
			logger.debug("channelActive, ready:" + ctx);

	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
		String request = packet.content().toString(CharsetUtil.UTF_8);
		if (logger.isDebugEnabled())
			logger.debug("Sender " + packet.sender() + " sent request:" + request);
		String session = packet.sender().getAddress().getHostAddress()+":"+packet.sender().getPort();//ctx.channel().id().asLongText();
		
		if (!socketList.inverse().containsKey(packet.sender())) {
			
			SimpleString tempQ = new SimpleString(session);
			NioDatagramChannel udpChannel = (NioDatagramChannel) ctx.channel();
			String localAddress = udpChannel.localAddress().toString();
			String remoteAddress = packet.sender().getAddress().getHostAddress(); 
			SubscriptionManagerFactory.getInstance().add(session, session, outputType, localAddress, remoteAddress);
			socketList.put(session, packet.sender());
			if (logger.isDebugEnabled())
				logger.debug("Added Sender " + packet.sender() + ", session:" + session);
			ctx.channel()
					.writeAndFlush(new DatagramPacket(
							Unpooled.copiedBuffer(Util.getWelcomeMsg().toString() + "\r\n", CharsetUtil.UTF_8),
							packet.sender()));
			// setup consumer
			if(!sessionList.containsKey(session)){
				ClientSession txSession = Util.getVmSession(Config.getConfigProperty(Config.ADMIN_USER),
						Config.getConfigProperty(Config.ADMIN_PWD));
				txSession.createTemporaryQueue(new SimpleString("outgoing.reply." + session), RoutingType.MULTICAST, tempQ);
				txSession.start();
				sessionList.put(session, txSession);
				producerList.put(session, txSession.createProducer());
				
				ClientConsumer consumer = txSession.createConsumer(tempQ, false);
				consumer.setMessageHandler(new MessageHandler() {
	
					@Override
					public void onMessage(ClientMessage message) {
						try {
							process(message);
						} catch (Exception e) {
							logger.error(e.getMessage(), e);
						}
	
					}
				});
				
			}
			if(!channelList.containsKey(session)){
				channelList.put(session,ctx);
			}
		}

		Map<String, Object> headers = getHeaders(socketList.inverse().get(packet.sender()));
		ClientMessage ex = sessionList.get(session).createMessage(true);
		ex.getBodyBuffer().writeString(request);
		ex.putStringProperty(Config.AMQ_REPLY_Q, session);
		
		for (String hdr : headers.keySet()) {
			ex.putStringProperty(hdr, headers.get(hdr).toString());
		}
		producerList.get(session).send(Config.INCOMING_RAW, ex);
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) {
		ctx.flush();
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		cause.printStackTrace();
		ctx.close();
	}

	@Override
	public boolean acceptInboundMessage(Object msg) throws Exception {
		if (msg instanceof Json || msg instanceof String)
			return true;
		return super.acceptInboundMessage(msg);
	}

	private Map<String, Object> getHeaders(String wsSession) {
		Map<String, Object> headers = new HashMap<>();
		headers.put(Config.AMQ_SESSION_ID, wsSession);
		headers.put(Config.MSG_SRC_IP, socketList.get(wsSession).getHostString());
		headers.put(Config.MSG_SRC_BUS, "udp." + wsSession.replace('.', '_'));
		headers.put(ConfigConstants.OUTPUT_TYPE, outputType);
		return headers;
	}

	@Override
	protected void finalize() throws Throwable {
		channelList.clear();
		socketList.clear();
		for(Entry<String, ClientProducer> entry:producerList.entrySet())
			entry.getValue().close();
		for(Entry<String, ClientSession> entry:sessionList.entrySet())
			entry.getValue().close();
	}

	public void process(ClientMessage message) throws Exception {

		String msg = Util.readBodyBufferToString(message);
		if (logger.isDebugEnabled())
			logger.debug("UDP sending msg : " + msg);
		if (msg != null) {
			// get the session
			String session = message.getStringProperty(Config.AMQ_SUB_DESTINATION);
			if (logger.isDebugEnabled())
				logger.debug("UDP session id:" + session);
			if (Config.SK_SEND_TO_ALL.equals(session)) {
				// udp
				
					for (InetSocketAddress client : socketList.values()) {
						if (logger.isDebugEnabled())
							logger.debug("Sending udp: " + msg);
						// udpCtx.pipeline().writeAndFlush(msg+"\r\n");
						((NioDatagramChannel) channelList.get(session).channel()).writeAndFlush(
								new DatagramPacket(Unpooled.copiedBuffer(msg + "\r\n", CharsetUtil.UTF_8), client));
						if (logger.isDebugEnabled())
							logger.debug("Sent udp to " + client);
					}
				
				
			} else {

				// udp
		
					final InetSocketAddress client = socketList.get(session);
					if (logger.isDebugEnabled())
						logger.debug("Sending udp: " + msg);
					// udpCtx.pipeline().writeAndFlush(msg+"\r\n");
					((NioDatagramChannel) channelList.get(session).channel()).writeAndFlush(
							new DatagramPacket(Unpooled.copiedBuffer(msg + "\r\n", CharsetUtil.UTF_8), client));
					if (logger.isDebugEnabled())
						logger.debug("Sent udp for session: " + session);
					// TODO: how do we tell when a UDP client is gone
				
				
			}
		}

	}
}