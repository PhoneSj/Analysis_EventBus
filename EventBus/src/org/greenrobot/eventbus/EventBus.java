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

import android.os.Looper;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;

/**
 * EventBus is a central publish/subscribe event system for Android. Events are posted ({@link #post(Object)}) to the
 * bus, which delivers it to subscribers that have a matching handler method for the event type. To receive events,
 * subscribers must register themselves to the bus using {@link #register(Object)}. Once registered, subscribers receive
 * events until {@link #unregister(Object)} is called. Event handling methods must be annotated by {@link Subscribe},
 * must be public, return nothing (void), and have exactly one parameter (the event).
 *
 * @author Markus Junginger, greenrobot
 */
public class EventBus {

	/** Log tag, apps may override it. */
	public static String TAG = "EventBus";

	static volatile EventBus defaultInstance;

	private static final EventBusBuilder DEFAULT_BUILDER = new EventBusBuilder();
	private static final Map<Class<?>, List<Class<?>>> eventTypesCache = new HashMap<>();

	private final Map<Class<?>, CopyOnWriteArrayList<Subscription>> subscriptionsByEventType;
	private final Map<Object, List<Class<?>>> typesBySubscriber;
	private final Map<Class<?>, Object> stickyEvents;

	//线程内部的数据存储类，不与其他线程共享
	private final ThreadLocal<PostingThreadState> currentPostingThreadState = new ThreadLocal<PostingThreadState>() {
		@Override
		protected PostingThreadState initialValue() {
			return new PostingThreadState();
		}
	};

	private final HandlerPoster mainThreadPoster;
	private final BackgroundPoster backgroundPoster;
	private final AsyncPoster asyncPoster;
	private final SubscriberMethodFinder subscriberMethodFinder;
	private final ExecutorService executorService;

	private final boolean throwSubscriberException;
	private final boolean logSubscriberExceptions;
	private final boolean logNoSubscriberMessages;
	private final boolean sendSubscriberExceptionEvent;
	private final boolean sendNoSubscriberEvent;
	private final boolean eventInheritance;

	private final int indexCount;

	/** Convenience singleton for apps using a process-wide EventBus instance. */
	/** @phone 使用双重检查的单例模式创建EventBus对象 */
	public static EventBus getDefault() {
		if (defaultInstance == null) {
			synchronized (EventBus.class) {
				if (defaultInstance == null) {
					defaultInstance = new EventBus();
				}
			}
		}
		return defaultInstance;
	}

	public static EventBusBuilder builder() {
		return new EventBusBuilder();
	}

	/** For unit test primarily. */
	public static void clearCaches() {
		SubscriberMethodFinder.clearCaches();
		eventTypesCache.clear();
	}

	/**
	 * Creates a new EventBus instance; each instance is a separate scope in which events are delivered. To use a
	 * central bus, consider {@link #getDefault()}.
	 */
	public EventBus() {
		this(DEFAULT_BUILDER);
	}

	EventBus(EventBusBuilder builder) {
		subscriptionsByEventType = new HashMap<>();
		typesBySubscriber = new HashMap<>();
		stickyEvents = new ConcurrentHashMap<>();
		mainThreadPoster = new HandlerPoster(this, Looper.getMainLooper(), 10);
		backgroundPoster = new BackgroundPoster(this);
		asyncPoster = new AsyncPoster(this);
		indexCount = builder.subscriberInfoIndexes != null ? builder.subscriberInfoIndexes.size() : 0;
		subscriberMethodFinder = new SubscriberMethodFinder(builder.subscriberInfoIndexes,
				builder.strictMethodVerification, builder.ignoreGeneratedIndex);
		logSubscriberExceptions = builder.logSubscriberExceptions;
		logNoSubscriberMessages = builder.logNoSubscriberMessages;
		sendSubscriberExceptionEvent = builder.sendSubscriberExceptionEvent;
		sendNoSubscriberEvent = builder.sendNoSubscriberEvent;
		throwSubscriberException = builder.throwSubscriberException;
		eventInheritance = builder.eventInheritance;
		executorService = builder.executorService;
	}

	/**
	 * Registers the given subscriber to receive events. Subscribers must call {@link #unregister(Object)} once they are
	 * no longer interested in receiving events.
	 * <p/>
	 * Subscribers have event handling methods that must be annotated by {@link Subscribe}. The {@link Subscribe}
	 * annotation also allows configuration like {@link ThreadMode} and priority.
	 */
	public void register(Object subscriber) {
		Class<?> subscriberClass = subscriber.getClass();
		//该订阅者的响应方法集合
		List<SubscriberMethod> subscriberMethods = subscriberMethodFinder.findSubscriberMethods(subscriberClass);
		synchronized (this) {
			//遍历响应方法，开始注册
			for (SubscriberMethod subscriberMethod : subscriberMethods) {
				subscribe(subscriber, subscriberMethod);
			}
		}
	}

	// Must be called in synchronized block
	private void subscribe(Object subscriber, SubscriberMethod subscriberMethod) {
		Class<?> eventType = subscriberMethod.eventType;
		Subscription newSubscription = new Subscription(subscriber, subscriberMethod);
		//从缓存中，获得该事件类型的所有订阅信息(包括订阅者和响应方法)集合
		CopyOnWriteArrayList<Subscription> subscriptions = subscriptionsByEventType.get(eventType);
		if (subscriptions == null) {
			//之前没有该事件类型的订阅信息集合，即刻创建并存入缓存中
			subscriptions = new CopyOnWriteArrayList<>();
			subscriptionsByEventType.put(eventType, subscriptions);
		} else {
			if (subscriptions.contains(newSubscription)) {
				//该事件类型，订阅者之前已经订阅了
				throw new EventBusException(
						"Subscriber " + subscriber.getClass() + " already registered to event " + eventType);
			}
		}

		//根据优先级调整订阅信息集合中元素的位置
		int size = subscriptions.size();
		for (int i = 0; i <= size; i++) {
			if (i == size || subscriberMethod.priority > subscriptions.get(i).subscriberMethod.priority) {
				subscriptions.add(i, newSubscription);
				break;
			}
		}

		//订阅者订阅的所有事件
		List<Class<?>> subscribedEvents = typesBySubscriber.get(subscriber);
		if (subscribedEvents == null) {
			subscribedEvents = new ArrayList<>();
			typesBySubscriber.put(subscriber, subscribedEvents);
		}
		subscribedEvents.add(eventType);

		//若该方法是粘性方法，则判断实际否有粘性事件需要立即响应
		if (subscriberMethod.sticky) {
			if (eventInheritance) {
				// Existing sticky events of all subclasses of eventType have to be considered.
				// Note: Iterating over all events may be inefficient with lots of sticky events,
				// thus data structure should be changed to allow a more efficient lookup
				// (e.g. an additional map storing sub classes of super classes: Class -> List<Class>).
				Set<Map.Entry<Class<?>, Object>> entries = stickyEvents.entrySet();
				for (Map.Entry<Class<?>, Object> entry : entries) {
					Class<?> candidateEventType = entry.getKey();
					if (eventType.isAssignableFrom(candidateEventType)) {
						Object stickyEvent = entry.getValue();
						checkPostStickyEventToSubscription(newSubscription, stickyEvent);
					}
				}
			} else {
				Object stickyEvent = stickyEvents.get(eventType);
				checkPostStickyEventToSubscription(newSubscription, stickyEvent);
			}
		}
	}

	private void checkPostStickyEventToSubscription(Subscription newSubscription, Object stickyEvent) {
		if (stickyEvent != null) {
			// If the subscriber is trying to abort the event, it will fail (event is not tracked in posting state)
			// --> Strange corner case, which we don't take care of here.
			postToSubscription(newSubscription, stickyEvent, Looper.getMainLooper() == Looper.myLooper());
		}
	}

	public synchronized boolean isRegistered(Object subscriber) {
		return typesBySubscriber.containsKey(subscriber);
	}

	/** Only updates subscriptionsByEventType, not typesBySubscriber! Caller must update typesBySubscriber. */
	private void unsubscribeByEventType(Object subscriber, Class<?> eventType) {
		//订阅该事件的所有订阅信息
		List<Subscription> subscriptions = subscriptionsByEventType.get(eventType);
		if (subscriptions != null) {
			//从订阅信息中移除该订阅者的订阅信息
			int size = subscriptions.size();
			for (int i = 0; i < size; i++) {
				Subscription subscription = subscriptions.get(i);
				if (subscription.subscriber == subscriber) {
					subscription.active = false;
					subscriptions.remove(i);
					i--;
					size--;
				}
			}
		}
	}

	/** Unregisters the given subscriber from all event classes. */
	public synchronized void unregister(Object subscriber) {
		//订阅者所有订阅的事件
		List<Class<?>> subscribedTypes = typesBySubscriber.get(subscriber);
		if (subscribedTypes != null) {
			//遍历订阅者订阅的所有事件，移除订阅信息
			for (Class<?> eventType : subscribedTypes) {
				unsubscribeByEventType(subscriber, eventType);
			}
			//从（订阅者-事件类型集）中删除该订阅者
			typesBySubscriber.remove(subscriber);
		} else {
			Log.w(TAG, "Subscriber to unregister was not registered before: " + subscriber.getClass());
		}
	}

	/** Posts the given event to the event bus. */
	public void post(Object event) {
		PostingThreadState postingState = currentPostingThreadState.get();
		List<Object> eventQueue = postingState.eventQueue;
		eventQueue.add(event);

		//是否正在执行
		if (!postingState.isPosting) {
			//是否在主线程
			postingState.isMainThread = Looper.getMainLooper() == Looper.myLooper();
			postingState.isPosting = true;
			if (postingState.canceled) {
				throw new EventBusException("Internal error. Abort state was not reset");
			}
			try {
				while (!eventQueue.isEmpty()) {
					//执行分发队列中的消息
					postSingleEvent(eventQueue.remove(0), postingState);
				}
			} finally {
				postingState.isPosting = false;
				postingState.isMainThread = false;
			}
		}
	}

	/**
	 * Called from a subscriber's event handling method, further event delivery will be canceled. Subsequent subscribers
	 * won't receive the event. Events are usually canceled by higher priority subscribers (see
	 * {@link Subscribe#priority()}). Canceling is restricted to event handling methods running in posting thread
	 * {@link ThreadMode#POSTING}.
	 */
	public void cancelEventDelivery(Object event) {
		PostingThreadState postingState = currentPostingThreadState.get();
		if (!postingState.isPosting) {
			throw new EventBusException(
					"This method may only be called from inside event handling methods on the posting thread");
		} else if (event == null) {
			throw new EventBusException("Event may not be null");
		} else if (postingState.event != event) {
			throw new EventBusException("Only the currently handled event may be aborted");
		} else if (postingState.subscription.subscriberMethod.threadMode != ThreadMode.POSTING) {
			throw new EventBusException(" event handlers may only abort the incoming event");
		}

		postingState.canceled = true;
	}

	/**
	 * Posts the given event to the event bus and holds on to the event (because it is sticky). The most recent sticky
	 * event of an event's type is kept in memory for future access by subscribers using {@link Subscribe#sticky()}.
	 */
	public void postSticky(Object event) {
		synchronized (stickyEvents) {
			stickyEvents.put(event.getClass(), event);
		}
		// Should be posted after it is putted, in case the subscriber wants to remove immediately
		post(event);
	}

	/**
	 * Gets the most recent sticky event for the given type.
	 *
	 * @see #postSticky(Object)
	 */
	public <T> T getStickyEvent(Class<T> eventType) {
		synchronized (stickyEvents) {
			return eventType.cast(stickyEvents.get(eventType));
		}
	}

	/**
	 * Remove and gets the recent sticky event for the given event type.
	 *
	 * @see #postSticky(Object)
	 */
	public <T> T removeStickyEvent(Class<T> eventType) {
		synchronized (stickyEvents) {
			return eventType.cast(stickyEvents.remove(eventType));
		}
	}

	/**
	 * Removes the sticky event if it equals to the given event.
	 *
	 * @return true if the events matched and the sticky event was removed.
	 */
	public boolean removeStickyEvent(Object event) {
		synchronized (stickyEvents) {
			Class<?> eventType = event.getClass();
			Object existingEvent = stickyEvents.get(eventType);
			if (event.equals(existingEvent)) {
				stickyEvents.remove(eventType);
				return true;
			} else {
				return false;
			}
		}
	}

	/**
	 * Removes all sticky events.
	 */
	public void removeAllStickyEvents() {
		synchronized (stickyEvents) {
			stickyEvents.clear();
		}
	}

	public boolean hasSubscriberForEvent(Class<?> eventClass) {
		List<Class<?>> eventTypes = lookupAllEventTypes(eventClass);
		if (eventTypes != null) {
			int countTypes = eventTypes.size();
			for (int h = 0; h < countTypes; h++) {
				Class<?> clazz = eventTypes.get(h);
				CopyOnWriteArrayList<Subscription> subscriptions;
				synchronized (this) {
					subscriptions = subscriptionsByEventType.get(clazz);
				}
				if (subscriptions != null && !subscriptions.isEmpty()) {
					return true;
				}
			}
		}
		return false;
	}

	private void postSingleEvent(Object event, PostingThreadState postingState) throws Error {
		Class<?> eventClass = event.getClass();
		boolean subscriptionFound = false;
		//是否响应该事件的父类/接口的订阅
		if (eventInheritance) {
			//事件类型的所有父类或接口
			List<Class<?>> eventTypes = lookupAllEventTypes(eventClass);
			int countTypes = eventTypes.size();
			for (int h = 0; h < countTypes; h++) {
				Class<?> clazz = eventTypes.get(h);
				subscriptionFound |= postSingleEventForEventType(event, postingState, clazz);
			}
		} else {
			subscriptionFound = postSingleEventForEventType(event, postingState, eventClass);
		}
		if (!subscriptionFound) {
			if (logNoSubscriberMessages) {
				Log.d(TAG, "No subscribers registered for event " + eventClass);
			}
			if (sendNoSubscriberEvent && eventClass != NoSubscriberEvent.class
					&& eventClass != SubscriberExceptionEvent.class) {
				post(new NoSubscriberEvent(this, event));
			}
		}
	}

	private boolean postSingleEventForEventType(Object event, PostingThreadState postingState, Class<?> eventClass) {
		CopyOnWriteArrayList<Subscription> subscriptions;
		synchronized (this) {
			//订阅该事件类型的订阅信息集合
			subscriptions = subscriptionsByEventType.get(eventClass);
		}
		if (subscriptions != null && !subscriptions.isEmpty()) {
			//遍历所有订阅信息，分发该事件
			for (Subscription subscription : subscriptions) {
				//将事件、订阅信息赋给postingState
				postingState.event = event;
				postingState.subscription = subscription;
				boolean aborted = false;
				try {
					postToSubscription(subscription, event, postingState.isMainThread);
					aborted = postingState.canceled;
				} finally {
					postingState.event = null;
					postingState.subscription = null;
					postingState.canceled = false;
				}
				if (aborted) {
					break;
				}
			}
			return true;
		}
		return false;
	}

	private void postToSubscription(Subscription subscription, Object event, boolean isMainThread) {
		switch (subscription.subscriberMethod.threadMode) {
			case POSTING:
				//不切换线程执行，默认
				invokeSubscriber(subscription, event);
				break;
			case MAIN:
				//响应方法在主线程中执行
				if (isMainThread) {
					invokeSubscriber(subscription, event);
				} else {
					mainThreadPoster.enqueue(subscription, event);
				}
				break;
			case BACKGROUND:
				//响应方法在后台线程中执行
				if (isMainThread) {
					backgroundPoster.enqueue(subscription, event);
				} else {
					invokeSubscriber(subscription, event);
				}
				break;
			case ASYNC:
				//重新开启一个线程执行
				asyncPoster.enqueue(subscription, event);
				break;
			default:
				throw new IllegalStateException("Unknown thread mode: " + subscription.subscriberMethod.threadMode);
		}
	}

	/** Looks up all Class objects including super classes and interfaces. Should also work for interfaces. */
	private static List<Class<?>> lookupAllEventTypes(Class<?> eventClass) {
		synchronized (eventTypesCache) {
			List<Class<?>> eventTypes = eventTypesCache.get(eventClass);
			if (eventTypes == null) {
				eventTypes = new ArrayList<>();
				Class<?> clazz = eventClass;
				while (clazz != null) {
					eventTypes.add(clazz);
					addInterfaces(eventTypes, clazz.getInterfaces());
					clazz = clazz.getSuperclass();
				}
				eventTypesCache.put(eventClass, eventTypes);
			}
			return eventTypes;
		}
	}

	/** Recurses through super interfaces. */
	static void addInterfaces(List<Class<?>> eventTypes, Class<?>[] interfaces) {
		for (Class<?> interfaceClass : interfaces) {
			if (!eventTypes.contains(interfaceClass)) {
				eventTypes.add(interfaceClass);
				addInterfaces(eventTypes, interfaceClass.getInterfaces());
			}
		}
	}

	/**
	 * Invokes the subscriber if the subscriptions is still active. Skipping subscriptions prevents race conditions
	 * between {@link #unregister(Object)} and event delivery. Otherwise the event might be delivered after the
	 * subscriber unregistered. This is particularly important for main thread delivery and registrations bound to the
	 * live cycle of an Activity or Fragment.
	 */
	void invokeSubscriber(PendingPost pendingPost) {
		Object event = pendingPost.event;
		Subscription subscription = pendingPost.subscription;
		PendingPost.releasePendingPost(pendingPost);
		if (subscription.active) {
			invokeSubscriber(subscription, event);
		}
	}

	void invokeSubscriber(Subscription subscription, Object event) {
		try {
			//反射调用订阅者的响应方法
			subscription.subscriberMethod.method.invoke(subscription.subscriber, event);
		} catch (InvocationTargetException e) {
			handleSubscriberException(subscription, event, e.getCause());
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("Unexpected exception", e);
		}
	}

	private void handleSubscriberException(Subscription subscription, Object event, Throwable cause) {
		if (event instanceof SubscriberExceptionEvent) {
			if (logSubscriberExceptions) {
				// Don't send another SubscriberExceptionEvent to avoid infinite event recursion, just log
				Log.e(TAG, "SubscriberExceptionEvent subscriber " + subscription.subscriber.getClass()
						+ " threw an exception", cause);
				SubscriberExceptionEvent exEvent = (SubscriberExceptionEvent) event;
				Log.e(TAG,
						"Initial event " + exEvent.causingEvent + " caused exception in " + exEvent.causingSubscriber,
						exEvent.throwable);
			}
		} else {
			if (throwSubscriberException) {
				throw new EventBusException("Invoking subscriber failed", cause);
			}
			if (logSubscriberExceptions) {
				Log.e(TAG, "Could not dispatch event: " + event.getClass() + " to subscribing class "
						+ subscription.subscriber.getClass(), cause);
			}
			if (sendSubscriberExceptionEvent) {
				SubscriberExceptionEvent exEvent = new SubscriberExceptionEvent(this, cause, event,
						subscription.subscriber);
				post(exEvent);
			}
		}
	}

	/** For ThreadLocal, much faster to set (and get multiple values). */
	final static class PostingThreadState {
		final List<Object> eventQueue = new ArrayList<Object>();//事件队列
		boolean isPosting;//是否执行分发事件
		boolean isMainThread;//是否时主线程
		Subscription subscription;//订阅信息（包括订阅者、订阅方法）
		Object event;//订阅的事件类型
		boolean canceled;//是否取消
	}

	ExecutorService getExecutorService() {
		return executorService;
	}

	// Just an idea: we could provide a callback to post() to be notified, an alternative would be events, of course...
	/* public */interface PostCallback {
		void onPostCompleted(List<SubscriberExceptionEvent> exceptionEvents);
	}

	@Override
	public String toString() {
		return "EventBus[indexCount=" + indexCount + ", eventInheritance=" + eventInheritance + "]";
	}
}
