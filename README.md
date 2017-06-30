# Analysis_EventBus

### EventBus实例化

<pre>
<code>
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
</code>
</pre>

由上面代码可知，EventBus是一个**单例模式**，而且使用的双重检查的判空模式。

EventBus的代码分为三个过程：

* 订阅过程
* 事件分发过程
* 取消订阅过程

## 订阅过程

<pre>
<code>
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
</code>
</pre>
<pre>
<code>
	public class SubscriberMethod {
	    final Method method;//响应方法
	    final ThreadMode threadMode;//响应方法的执行线程模式
	    final Class<?> eventType;//事件类型
	    final int priority;//响应优先级
	    final boolean sticky;//是否是粘性事件
		...
	}
</code>
</pre>
> 做了两步操作：
> 
> 1.找出该订阅者订阅的所有响应方法subscriberMethods
> 
> 2.遍历订阅的响应方法，对每个方法注册

<pre>
<code>
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
</code>
</pre>
> SubscriberMethodFinder.findSubscriberMethods()方法中做了如下操作：
> 
> 1.从缓存中检查该订阅者之间是否注册过
> 
> 2.从订阅者类中找到响应方法（这里有两种方法：注解方式、反射方式）
> 3.响应方法存入缓存

<pre>
<code>
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

	static class FindState {
		final List<SubscriberMethod> subscriberMethods = new ArrayList<>();//订阅者响应方法集合
		final Map<Class, Object> anyMethodByEventType = new HashMap<>();//第一次校验：事件类型-响应方法映射
		final Map<String, Class> subscriberClassByMethodKey = new HashMap<>();//第二次校验：方法签名-订阅者类名映射
		...
	}
</code>
</pre>

> 以上两个方法中：
> 
> 1.实例化一个FindState并初始化
> 
> 2.找到订阅者及其父类/接口的所有响应方法，赋值到FindState中
> 
> 3.在找响应方法中，修改时public修饰，且带有subcriber注解的方法才能判定为响应方法。这一点比老版本的EventBut要灵活

<pre>
<code>
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
</code>
</pre>

> 该方法做了不如操作：
> 
> 1.将订阅者订阅的事件存入subscriptionsByEventType中
> 
> 2.根据该订阅者订阅事件的优先级调用Subscription(封装了订阅者、响应方法)在集合中的位置
> 
> 3.subscribedEvents（订阅者-订阅事件类型集合）中删除该订阅者信息
> 
> 4.判断该事件是否是粘性事件

## 事件分发过程

<pre>
<code>
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

	final static class PostingThreadState {
		final List<Object> eventQueue = new ArrayList<Object>();//事件队列
		boolean isPosting;//是否执行分发事件
		boolean isMainThread;//是否时主线程
		Subscription subscription;//订阅信息（包括订阅者、订阅方法）
		Object event;//订阅的事件类型
		boolean canceled;//是否取消
	}
</code>
</pre>

> 1.将该事件加入到事件队列中（当有大量的事件需要响应时，事件会按照队列中的顺序依次响应）
> 
> 2.不停的从事件队列eventQueue中取出订阅事件，并将该事件分发出去执行

<pre>
<code>
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
</code>
</pre>

> 1.判断是否响应事件的父类/接口
> 
> 2.分发每个事件(若包括父父类/接口，每个父类/接口也要分发一次)

<pre>
<code>
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
</code>
</pre>

> 1.从map中取出订阅了该事件的所有订阅者信息Subscriptions
> 
> 2.遍历每个订阅者信息Subscription，根据注解中设置的线程模式ThreadMode，在指定的线程中执行

**ThreadMode**

* POSTING		在当前线程中执行响应方法
* MAIN			在主线程中执行响应方法
* BACKGROUND	若当前是主线程，则开启新线程执行；若不是，则在当前线程中执行
* ASYNC			开启新线程执行

<pre>
<code>
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
</code>
</pre>

> 1.使用反射调用订阅者的响应方法

## 取消订阅过程

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


>
> 1.从typesBySubscriber中找到订阅者订阅的事件
> 2.根据订阅的事件从subscriptionsByEventType（key为订阅事件，value为订阅者信息(封装有订阅者、响应方法)）移除该订阅者的订阅信息Subscription
> 3.从typesBySubscriber中移除该订阅者订阅的事件

## 总结
**最关键的两个数据：**

**typesBySubscriber**:Map<订阅者-订阅事>
**subscriptionsByEventType**：Map<事件类型-订阅信息(订阅者、响应方法的封装)集合>


**订阅关键步骤**
> 1.获得订阅者的所有响应方法（封装有响应方法、订阅事件等）
> 
> 2.根据订阅事件，将Subscription(封装了订阅者、响应方法)存入一个map中（key为订阅事件，value为订阅信息（包含订阅者、响应方法））
> 
> 3. 将订阅者订阅的所有事件加入到typesBySubscriber中

**分发的关键步骤**
> 1.根据订阅事件，从map中（key为订阅事件，value为订阅信息）取出Subscriptions
> 
> 2.遍历Subscriptions,从Subscription中取出订阅者、响应方法，利用反射机制，调用该订阅者的响应方法

**取消订阅的关键步骤**
> 1.从typesBySubscriber移除订阅者订阅的事件
> 
> 2.从subscriptionsByEventType移除订阅者的订阅信息