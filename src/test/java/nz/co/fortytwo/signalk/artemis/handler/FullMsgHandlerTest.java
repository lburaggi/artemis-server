package nz.co.fortytwo.signalk.artemis.handler;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.same;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionSendMessage;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.easymock.EasyMockRule;
import org.easymock.Mock;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import mjson.Json;
import nz.co.fortytwo.signalk.artemis.handler.FullMsgHandler;
import nz.co.fortytwo.signalk.artemis.intercept.BaseMsgInterceptorTest;
import nz.co.fortytwo.signalk.artemis.util.Config;
import nz.co.fortytwo.signalk.artemis.util.SignalkMapConvertor;

public class FullMsgHandlerTest  extends BaseMsgInterceptorTest {

	private static Logger logger = LogManager.getLogger(FullMsgHandlerTest.class);
	private Json full;
	private Json config;
	private Json delta;
	
	
    private FullMsgHandler handler;// 1
    
    public FullMsgHandlerTest() {
		try {
			delta=Json.read(FileUtils.readFileToString(new File("./src/test/resources/samples/delta/docs-data_model.json")));
			full=Json.read(FileUtils.readFileToString(new File("./src/test/resources/samples/full/docs-data_model.json")));
			config=Json.read(FileUtils.readFileToString(new File("./src/test/resources/samples/signalk-config.json")));
		} catch (IOException e) {
			logger.error(e,e);
		}
	}
    
    @Before
    public void before() throws NoSuchMethodException, SecurityException{
    	handler = partialMockBuilder(FullMsgHandler.class)
	    	.addMockedMethod("sendKvMessage")
	    	.addMockedMethod(BaseHandler.class.getDeclaredMethod("initSession",String.class, String.class,RoutingType.class))
    			.createMock(); 
    }
	
   
	@Test
	public void shouldProcessVessels() throws ActiveMQException {
		
		
		ClientMessage message = getClientMessage(full.toString(), Config.AMQ_CONTENT_TYPE_JSON_FULL, false); 
		handler.sendKvMessage( same(message), anyString(), anyObject(Json.class));
		expectLastCall().times(13);
		replayAll();
		handler.consume(message);
		verifyAll();
	}
	
	@Test
	public void shouldProcessConfig() throws ActiveMQException {
		
		ClientMessage message = getClientMessage(full.toString(), Config.AMQ_CONTENT_TYPE_JSON_FULL, false); 
		handler.sendKvMessage( same(message), anyString(), anyObject(Json.class));
		expectLastCall().times(13);
		replayAll();
		
		handler.consume(message);
		verifyAll();
	}
	
	@Test
	public void shouldAvoidDeltaFormat() throws ActiveMQException {
	
		replayAll();
		
		ClientMessage message = getClientMessage(delta.toString(), Config.AMQ_CONTENT_TYPE_JSON_DELTA, false); 
		handler.consume(message);
		verifyAll();
	}
	@Test
	public void shouldHandleContext() throws Exception {
		
		ClientMessage message = getClientMessage(config.toString(), Config.AMQ_CONTENT_TYPE_JSON_FULL, false); 
		handler.sendKvMessage( same(message), anyString(), anyObject(Json.class));
		expectLastCall().times(51);
		replayAll();
		
		handler.consume(message);
		verifyAll();
	}
	

}
