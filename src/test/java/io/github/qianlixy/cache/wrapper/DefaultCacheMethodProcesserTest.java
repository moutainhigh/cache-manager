package io.github.qianlixy.cache.wrapper;

import java.io.IOException;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import io.github.qianlixy.cache.CacheAdapter;
import io.github.qianlixy.cache.context.ApplicationContext;
import io.github.qianlixy.cache.context.CacheContext;
import io.github.qianlixy.cache.impl.AbstractCacheAdapterFactory;
import net.sourceforge.groboutils.junit.v1.MultiThreadedTestRunner;
import net.sourceforge.groboutils.junit.v1.TestRunnable;

public class DefaultCacheMethodProcesserTest {
	
	@Before
	public void before() throws IOException {
		AbstractCacheAdapterFactory<Object> factory = new AbstractCacheAdapterFactory<Object>() {
			@Override
			public CacheAdapter buildCacheAdapter() throws IOException {
				return null;
			}
		};
		ApplicationContext.set(ApplicationContext.KEY_CACHE_ADAPTER_FACTORY, factory);
		ApplicationContext.set(ApplicationContext.KEY_DEFAULT_CACHE_TIME, 120);
	}

	@Test
	public void testDoProcess_queryMethod() throws Throwable {
		ProceedingJoinPoint joinPoint = EasyMock.createMock(ProceedingJoinPoint.class);
		Signature signature = new Signature() {
			@Override
			public String toShortString() {return null;}
			@Override
			public String toLongString() {return new String("method name");}
			@Override
			public String getName() {return null;}
			@Override
			public int getModifiers() {return 0;}
			@Override
			public String getDeclaringTypeName() {return null;}
			@Override
			@SuppressWarnings("rawtypes")
			public Class getDeclaringType() {return null;}
		};
		
		EasyMock.expect(joinPoint.getSignature()).andReturn(signature).anyTimes();
		EasyMock.expect(joinPoint.proceed()).andReturn(null).once();
		
		CacheContext cacheContext = EasyMock.createMock(CacheContext.class);
		EasyMock.expect(cacheContext.isQuery()).andReturn(true).anyTimes();
		EasyMock.replay(joinPoint, cacheContext);
		
		
		
		TestRunnable[] trs = new TestRunnable[10];
		for (int i = 0; i < trs.length; i++) {
			trs[i] = new TestRunnable() {
				@Override
				public void runTest() throws Throwable {
					DefaultCacheMethodProcesser methodProcesser = new DefaultCacheMethodProcesser(joinPoint , cacheContext);
					methodProcesser.doProcess();
				}
			};
		}
		
		MultiThreadedTestRunner mttr = new MultiThreadedTestRunner(trs);
		
		try {
			mttr.runTestRunnables();
		} catch (Throwable e) {
			e.printStackTrace();
		}
		
		EasyMock.verify(joinPoint, cacheContext);
	}
	
}
