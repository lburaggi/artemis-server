package nz.co.fortytwo.signalk.artemis.transformer;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

public class JsPoolFactory extends BasePooledObjectFactory<Context> {
	private static Logger logger = LogManager.getLogger(JsPoolFactory.class);
	protected static Engine engine = Engine.create();
	private static Source bundle0183;
	private static Source bundleN2k;
	
    @Override
    public Context create() {
        try {
			return initEngine();
		} catch (Exception e) {
			logger.error(e,e);
			return null;
		}
    }

    /**
     * Use the default PooledObject implementation.
     */
    @Override
    public PooledObject<Context> wrap(Context context) {
        return new DefaultPooledObject<Context>(context);
    }

    /**
     * When an object is returned to the pool, clear the buffer.
     */
    @Override
    public void passivateObject(PooledObject<Context> pooledObject) {
        //pooledObject.getObject().leave();
    }

    private Context initEngine() throws IOException  {
		logger.info("create js context");
		Context context = Context.newBuilder("js").allowHostAccess(true).build();
			
		if(bundleN2k==null) {
			logger.info("Load n2kMapper: {}", "n2k-signalk/dist/bundle.js");
			bundleN2k = Source.newBuilder("js", Thread.currentThread().getContextClassLoader().getResource("n2k-signalk/dist/bundle.js")).build();
		}
		 Value n2kCtx = context.eval(bundleN2k);
		 if(logger.isDebugEnabled())logger.debug("n2kMapper: {}",n2kCtx.getMemberKeys());
		
		if(bundle0183==null) {
			logger.info("Load 0183 parser: {}", "signalk-parser-nmea0183/dist/bundle.js");
			bundle0183 = Source.newBuilder("js", Thread.currentThread().getContextClassLoader().getResource("signalk-parser-nmea0183/dist/bundle.js")).build();
		}
		 Value jsCtx = context.eval(bundle0183);
		 
		if(logger.isDebugEnabled())logger.debug("0183 Parser: {}",jsCtx.getMemberKeys());
		
		String hooks = IOUtils.toString(getIOStream("signalk-parser-nmea0183/hooks-es5/supported.txt"), Charsets.UTF_8);
		if(logger.isDebugEnabled())logger.debug("Hooks: {}",hooks);

		String[] files = hooks.split("\n");
		
		for (String f : files) {
			// seatalk breaks
			if (f.startsWith("ALK"))
				continue;
			if(logger.isDebugEnabled())logger.debug(f);
			//Invocable inv = (Invocable) engine;
			context.getBindings("js").getMember("parser").invokeMember("loadHook", f.trim());
		}
		logger.info("js context complete");
		return context;
	}

    @Override
	public void destroyObject(PooledObject<Context> p) throws Exception {
		//p.getObject().close();
		super.destroyObject(p);
	}

	private static InputStream getIOStream(String path) {

		if(logger.isDebugEnabled())logger.debug("Return resource {}", path);
		return Thread.currentThread().getContextClassLoader().getResourceAsStream(path);

	}

    // for all other methods, the no-op implementation
    // in BasePooledObjectFactory will suffice
}