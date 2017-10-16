package io.github.qianlixy.cache.wrapper;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.aspectj.lang.ProceedingJoinPoint;

import io.github.qianlixy.cache.CacheAdapter;
import io.github.qianlixy.cache.context.ApplicationContext;
import io.github.qianlixy.cache.context.CacheContext;
import io.github.qianlixy.cache.exception.CacheNotExistException;
import io.github.qianlixy.cache.exception.CacheOutOfDateException;
import io.github.qianlixy.cache.exception.ConsistentTimeException;
import io.github.qianlixy.cache.exception.ExecuteSourceMethodException;

/**
 * 默认的缓存方法包装类
 * @author qianli_xy@163.com
 * @since 1.0.0
 * @date 2017年10月14日 下午10:34:59
 */
public class DefaultCacheMethodProcesser implements CacheMethodProcesser {
	
	private Thread lastThread;
	private Object sourceData;
	
	private ProceedingJoinPoint joinPoint;
	private CacheContext cacheContext;
	private CacheAdapter cacheAdapter;
	private int cacheTime;
	
	private String fullMethodName;
	
	public DefaultCacheMethodProcesser(ProceedingJoinPoint joinPoint, 
			CacheContext cacheContext) throws IOException {
		this.joinPoint = joinPoint;
		this.cacheContext = cacheContext;
		this.cacheAdapter = ApplicationContext.getCacheAdaperFactory().buildCacheAdapter();
		this.cacheTime = ApplicationContext.getDefaultCacheTime();
		this.lastThread = Thread.currentThread();
		this.fullMethodName = joinPoint.getSignature().toLongString();
	}

	@Override
	public void putCache(Object source) throws ConsistentTimeException {
		putCache(source, cacheTime);
	}
	
	@Override
	public void putCache(Object source, int time) throws ConsistentTimeException {
		cacheAdapter.set(cacheContext.getDynamicUniqueMark(), wrap(source), time);
		cacheContext.setLastQueryTime(ApplicationContext.getConsistentTimeProvider().newInstance());
	}
	
	@Override
	public Object getCache() throws CacheOutOfDateException, CacheNotExistException {
		//获取缓存
		Object cache = cacheAdapter.get(cacheContext.getDynamicUniqueMark());
		
		//缓存为null，抛出缓存不存在异常
		if(null == cache) throw new CacheNotExistException();
		
		//获取源方法关联表的修改时间
		Set<Long> lastAlterTime = new HashSet<>();
		Collection<String> methodTalbeMap = cacheContext.getTables();
		
		//找不到源方法对应的表，说明还没有查询过，所以直接返回null
		if(null == methodTalbeMap) return null;
		
		//判断缓存是否超时失效
		for (String table : methodTalbeMap) {
			long time = cacheContext.getTableLastAlterTime(table);
			if(time > 0) lastAlterTime.add(time);
		}
		if (lastAlterTime.size() > 0) {
			long lastQueryTime = cacheContext.getLastQueryTime();
			for (Long alterTime : lastAlterTime) {
				if(alterTime > lastQueryTime) {
					LOGGER.debug("Cached data is out of date on [{}]", cacheContext.toString());
					throw new CacheOutOfDateException();
				}
			}
		}
		
		//缓存存在，没有超时失效，返回有效缓存
		LOGGER.debug("Use cached client data on [{}]", cacheContext.toString());
		return unwrap(cache);
	}

	@Override
	public String toString() {
		return getFullMethodName();
	}

	@Override
	public Class<?> getTargetClass() {
		return joinPoint.getTarget().getClass();
	}

	@Override
	public String getMethodName() {
		return joinPoint.getSignature().getName();
	}

	@Override
	public Object[] getArgs() {
		return joinPoint.getArgs();
	}
	
	@Override
	public String getFullMethodName() {
		return fullMethodName;
	}

	@Override
	public Object doProcess() throws ExecuteSourceMethodException {
		//源数据为空或者新线程中执行源方法，获取源数据
		if(null == sourceData || lastThread != Thread.currentThread()) {
			//同一查询条件锁，只允许一个线程执行源方法，其余线程等源方法执行完毕后拿到返回值直接返回
			if(cacheContext.isQuery()) {
				synchronized (fullMethodName.intern()) {
					if(null == sourceData) {
						callSourceMethod();
					}
				}
			} else {
				callSourceMethod();
			}
		}
		return unwrap(sourceData);
	}

	private void callSourceMethod() throws ExecuteSourceMethodException {
		LOGGER.debug("Start execute source method [{}]", getFullMethodName());
		try {
			sourceData = wrap(joinPoint.proceed());
			lastThread = Thread.currentThread();
		} catch (Throwable e) {
			throw new ExecuteSourceMethodException(e);
		}
	}

	@Override
	public Object doProcessAndCache() throws ConsistentTimeException, ExecuteSourceMethodException {
		return doProcessAndCache(cacheTime);
	}

	@Override
	public Object doProcessAndCache(int time) throws ConsistentTimeException, ExecuteSourceMethodException {
		Object source = doProcess();
		Boolean isQuery = cacheContext.isQuery();
		if(null != isQuery && isQuery)
			putCache(source, time);
		return source;
	}
	
	@Override
	public int getCacheTime() {
		return cacheTime;
	}

	@Override
	public void setCacheTime(int cacheTime) {
		this.cacheTime = cacheTime;
	}
	
	/**
	 * 包装对象，如果为null返回{@link Null}
	 * @param obj 源对象
	 * @return 包装后的对象
	 */
	private Object wrap(Object obj) {
		return null == obj ? new Null() : obj;
	}
	
	/**
	 * 解包对象，如果包装对象类型为{@link Null}将返回null，否则返回源对象
	 * @param obj 包装对象
	 * @return 解包后对象
	 */
	private Object unwrap(Object obj) {
		return obj instanceof Null ? null : obj;
	}
	
}
