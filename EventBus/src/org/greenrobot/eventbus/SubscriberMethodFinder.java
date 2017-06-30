/*
 * Copyright (C) 2012-2016 Markus Junginger, greenrobot (http://greenrobot.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.greenrobot.eventbus;

import org.greenrobot.eventbus.meta.SubscriberInfo;
import org.greenrobot.eventbus.meta.SubscriberInfoIndex;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class SubscriberMethodFinder {
	/*
	 * In newer class files, compilers may add methods. Those are called bridge or synthetic methods. EventBus must
	 * ignore both. There modifiers are not public but defined in the Java class file format:
	 * http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.6-200-A.1
	 */
	private static final int BRIDGE = 0x40;
	private static final int SYNTHETIC = 0x1000;

	private static final int MODIFIERS_IGNORE = Modifier.ABSTRACT | Modifier.STATIC | BRIDGE | SYNTHETIC;
	//缓存：订阅者类型-响应方法集合的映射
	private static final Map<Class<?>, List<SubscriberMethod>> METHOD_CACHE = new ConcurrentHashMap<>();

	private List<SubscriberInfoIndex> subscriberInfoIndexes;
	private final boolean strictMethodVerification;
	private final boolean ignoreGeneratedIndex;

	private static final int POOL_SIZE = 4;
	private static final FindState[] FIND_STATE_POOL = new FindState[POOL_SIZE];

	SubscriberMethodFinder(List<SubscriberInfoIndex> subscriberInfoIndexes, boolean strictMethodVerification,
			boolean ignoreGeneratedIndex) {
		this.subscriberInfoIndexes = subscriberInfoIndexes;
		this.strictMethodVerification = strictMethodVerification;
		this.ignoreGeneratedIndex = ignoreGeneratedIndex;
	}

	/**
	 * @phone 从缓存中查找订阅者的响应方法集合
	 */
	List<SubscriberMethod> findSubscriberMethods(Class<?> subscriberClass) {
		//从缓存中该订阅者的响应方法集合
		List<SubscriberMethod> subscriberMethods = METHOD_CACHE.get(subscriberClass);
		if (subscriberMethods != null) {
			//之前订阅过，直接返回从缓存中获取的数据
			return subscriberMethods;
		}

		if (ignoreGeneratedIndex) {
			//使用反射来获取订阅者中的响应方法
			subscriberMethods = findUsingReflection(subscriberClass);
		} else {
			//利用注解来获得订阅者中的响应方法
			subscriberMethods = findUsingInfo(subscriberClass);
		}
		if (subscriberMethods.isEmpty()) {
			throw new EventBusException("Subscriber " + subscriberClass
					+ " and its super classes have no public methods with the @Subscribe annotation");
		} else {
			//存入缓存
			METHOD_CACHE.put(subscriberClass, subscriberMethods);
			return subscriberMethods;
		}
	}

	private List<SubscriberMethod> findUsingInfo(Class<?> subscriberClass) {
		FindState findState = prepareFindState();
		findState.initForSubscriber(subscriberClass);
		while (findState.clazz != null) {
			findState.subscriberInfo = getSubscriberInfo(findState);
			if (findState.subscriberInfo != null) {
				SubscriberMethod[] array = findState.subscriberInfo.getSubscriberMethods();
				for (SubscriberMethod subscriberMethod : array) {
					if (findState.checkAdd(subscriberMethod.method, subscriberMethod.eventType)) {
						findState.subscriberMethods.add(subscriberMethod);
					}
				}
			} else {
				findUsingReflectionInSingleClass(findState);
			}
			findState.moveToSuperclass();
		}
		return getMethodsAndRelease(findState);
	}

	private List<SubscriberMethod> getMethodsAndRelease(FindState findState) {
		List<SubscriberMethod> subscriberMethods = new ArrayList<>(findState.subscriberMethods);
		findState.recycle();
		synchronized (FIND_STATE_POOL) {
			for (int i = 0; i < POOL_SIZE; i++) {
				if (FIND_STATE_POOL[i] == null) {
					FIND_STATE_POOL[i] = findState;
					break;
				}
			}
		}
		return subscriberMethods;
	}

	private FindState prepareFindState() {
		synchronized (FIND_STATE_POOL) {
			for (int i = 0; i < POOL_SIZE; i++) {
				FindState state = FIND_STATE_POOL[i];
				if (state != null) {
					FIND_STATE_POOL[i] = null;
					return state;
				}
			}
		}
		return new FindState();
	}

	private SubscriberInfo getSubscriberInfo(FindState findState) {
		if (findState.subscriberInfo != null && findState.subscriberInfo.getSuperSubscriberInfo() != null) {
			SubscriberInfo superclassInfo = findState.subscriberInfo.getSuperSubscriberInfo();
			if (findState.clazz == superclassInfo.getSubscriberClass()) {
				return superclassInfo;
			}
		}
		if (subscriberInfoIndexes != null) {
			for (SubscriberInfoIndex index : subscriberInfoIndexes) {
				SubscriberInfo info = index.getSubscriberInfo(findState.clazz);
				if (info != null) {
					return info;
				}
			}
		}
		return null;
	}

	private List<SubscriberMethod> findUsingReflection(Class<?> subscriberClass) {
		FindState findState = prepareFindState();
		findState.initForSubscriber(subscriberClass);
		while (findState.clazz != null) {
			//在当前类中通过反射查找到当前类的响应方法
			findUsingReflectionInSingleClass(findState);
			//指针从当前类移到父类，继续查找父类
			findState.moveToSuperclass();
		}
		return getMethodsAndRelease(findState);
	}

	private void findUsingReflectionInSingleClass(FindState findState) {
		Method[] methods;
		try {
			// This is faster than getMethods, especially when subscribers are fat classes like Activities
			// 通过反射获得订阅者所有方法
			// 这里首先优先使用更快getDeclaredMethods，其次使用getMethods方法）
			methods = findState.clazz.getDeclaredMethods();
		} catch (Throwable th) {
			// Workaround for java.lang.NoClassDefFoundError, see https://github.com/greenrobot/EventBus/issues/149
			methods = findState.clazz.getMethods();
			findState.skipSuperClasses = true;
		}
		for (Method method : methods) {
			//方法修饰符
			int modifiers = method.getModifiers();
			//找出public修饰，且无其他忽略修饰符的方法
			if ((modifiers & Modifier.PUBLIC) != 0 && (modifiers & MODIFIERS_IGNORE) == 0) {
				//方法的形参
				Class<?>[] parameterTypes = method.getParameterTypes();
				//订阅者的响应方法只能有一个参数
				if (parameterTypes.length == 1) {
					//修饰方法的Subscribe注解
					Subscribe subscribeAnnotation = method.getAnnotation(Subscribe.class);
					if (subscribeAnnotation != null) {
						Class<?> eventType = parameterTypes[0];
						//校验是否添加该方法
						if (findState.checkAdd(method, eventType)) {
							//线程模式
							ThreadMode threadMode = subscribeAnnotation.threadMode();
							//订阅者的响应方法校验通过，添加到响应方法集合
							findState.subscriberMethods.add(new SubscriberMethod(method, eventType, threadMode,
									subscribeAnnotation.priority(), subscribeAnnotation.sticky()));
						}
					}
				} else if (strictMethodVerification && method.isAnnotationPresent(Subscribe.class)) {
					String methodName = method.getDeclaringClass().getName() + "." + method.getName();
					throw new EventBusException("@Subscribe method " + methodName
							+ "must have exactly 1 parameter but has " + parameterTypes.length);
				}
			} else if (strictMethodVerification && method.isAnnotationPresent(Subscribe.class)) {
				String methodName = method.getDeclaringClass().getName() + "." + method.getName();
				throw new EventBusException(
						methodName + " is a illegal @Subscribe method: must be public, non-static, and non-abstract");
			}
		}
	}

	static void clearCaches() {
		METHOD_CACHE.clear();
	}

	static class FindState {
		final List<SubscriberMethod> subscriberMethods = new ArrayList<>();//订阅者响应方法集合
		final Map<Class, Object> anyMethodByEventType = new HashMap<>();//第一次校验：事件类型-响应方法映射
		final Map<String, Class> subscriberClassByMethodKey = new HashMap<>();//第二次校验：方法签名-订阅者类名映射
		final StringBuilder methodKeyBuilder = new StringBuilder(128);

		Class<?> subscriberClass;
		Class<?> clazz;
		boolean skipSuperClasses;
		SubscriberInfo subscriberInfo;

		void initForSubscriber(Class<?> subscriberClass) {
			this.subscriberClass = clazz = subscriberClass;
			skipSuperClasses = false;
			subscriberInfo = null;
		}

		void recycle() {
			subscriberMethods.clear();
			anyMethodByEventType.clear();
			subscriberClassByMethodKey.clear();
			methodKeyBuilder.setLength(0);
			subscriberClass = null;
			clazz = null;
			skipSuperClasses = false;
			subscriberInfo = null;
		}

		boolean checkAdd(Method method, Class<?> eventType) {
			// 2 level check: 1st level with event type only (fast), 2nd level with complete signature when required.
			// Usually a subscriber doesn't have methods listening to the same event type.
			//两级检查：1.检查事件，2.检查签名(由方法名+事件名共同生成)
			Object existing = anyMethodByEventType.put(eventType, method);
			if (existing == null) {
				//该事件类型的响应方法，之前不存在，校验通过
				return true;
			} else {
				if (existing instanceof Method) {
					//该事件类型的响应方法，之前存在
					if (!checkAddWithMethodSignature((Method) existing, eventType)) {
						// Paranoia check
						throw new IllegalStateException();
					}
					// Put any non-Method object to "consume" the existing Method
					anyMethodByEventType.put(eventType, this);
				}
				return checkAddWithMethodSignature(method, eventType);
			}
		}

		//使用方法签名校验
		private boolean checkAddWithMethodSignature(Method method, Class<?> eventType) {
			methodKeyBuilder.setLength(0);//字符串内容置为“”空字符串
			methodKeyBuilder.append(method.getName());
			methodKeyBuilder.append('>').append(eventType.getName());
			//方法签名
			String methodKey = methodKeyBuilder.toString();
			Class<?> methodClass = method.getDeclaringClass();
			Class<?> methodClassOld = subscriberClassByMethodKey.put(methodKey, methodClass);
			if (methodClassOld == null || methodClassOld.isAssignableFrom(methodClass)) {
				// Only add if not already found in a sub class
				//在子类没有该方法时，校验通过
				return true;
			} else {
				// Revert the put, old class is further down the class hierarchy
				// 恢复使用OldClass（OldClass是子类的方法）
				subscriberClassByMethodKey.put(methodKey, methodClassOld);
				return false;
			}
		}

		void moveToSuperclass() {
			if (skipSuperClasses) {
				clazz = null;
			} else {
				clazz = clazz.getSuperclass();
				String clazzName = clazz.getName();
				/** Skip system classes, this just degrades performance. */
				if (clazzName.startsWith("java.") || clazzName.startsWith("javax.")
						|| clazzName.startsWith("android.")) {
					clazz = null;
				}
			}
		}
	}

}
