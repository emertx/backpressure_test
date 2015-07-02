Came across some weird behavior while using methods `doOnSubscribe` and `doOnUnsubscribe` of `Observable`. The reason being that those methods use operators `OperatorDoOnSubscribe` & `OperatorDoOnUnsubscribe`. 

```java
public class OperatorDoOnSubscribe<T> implements Operator<T, T> {
    private final Action0 subscribe;

    /**
     * Constructs an instance of the operator with the callback that gets invoked when the modified Observable is subscribed
     * @param subscribe the action that gets invoked when the modified {@link rx.Observable} is subscribed
     */
    public OperatorDoOnSubscribe(Action0 subscribe) {
        this.subscribe = subscribe;
    }

    @Override
    public Subscriber<? super T> call(final Subscriber<? super T> child) {
        subscribe.call();
        // Pass through since this operator is for notification only, there is
        // no change to the stream whatsoever.
        return child;
    }
}
```

```java
public class OperatorDoOnUnsubscribe<T> implements Operator<T, T> {
    private final Action0 unsubscribe;

    /**
     * Constructs an instance of the operator with the callback that gets invoked when the modified Observable is unsubscribed
     * @param unsubscribe The action that gets invoked when the modified {@link rx.Observable} is unsubscribed
     */
    public OperatorDoOnUnsubscribe(Action0 unsubscribe) {
        this.unsubscribe = unsubscribe;
    }

    @Override
    public Subscriber<? super T> call(final Subscriber<? super T> child) {
        child.add(Subscriptions.create(unsubscribe));

        // Pass through since this operator is for notification only, there is
        // no change to the stream whatsoever.
        return child;
    }
}
```

These operators when passed on to `lift` in `Observable`, get wrapped inside a new `Observable` which on subscription passes the incoming `Subscriber` to the provided operator and then call `onStart` of the `Subscriber`. Since essentially the above mentioned operators just return the incoming subscriber, there is a possibility of `onStart` getting called multiple times (which has the potential to mess up backpressure)

**PARTIAL CODE TO REPRODUCE**

```java
private static class FlatMapFunc implements Func1<Integer, Observable<String>> {
    @Override
    public Observable<String> call(final Integer count) {
        return Observable.create(new Observable.OnSubscribe<String>() {
            @Override
            public void call(Subscriber<? super String> subscriber) {
                doLongBlockingTask();
                if (!subscriber.isUnsubscribed()) {
                    subscriber.onNext("My number is: " + count + "\n");
                    if (!subscriber.isUnsubscribed()) {
                        subscriber.onCompleted();
                    }
                }
            }
        });
    }
    private void doLongBlockingTask() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
```

```java
int count = 100; // Should be > 16 for Android & > 128 for other platforms
Observable
        .from(getIntegerList(count)) 
        .doOnSubscribe(new OnSubscribeAction())
        .observeOn(Schedulers.io())
        .flatMap(new FlatMapFunc())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
                new PrivateSubscriber(
                        (TextView) findViewById(R.id.backpressure_missing_text)
                )
        );
```

In the above code, `observeOn` (operator `OperatorObserveOn`) applies backpressure by `request(RxRingBuffer.SIZE)` in `onStart` of the `Subscriber` it creates and returns. When `OperatorDoOnSubscribe` gets lifted, it also calls `onStart` of the same subscriber which again make a request for `RxRingBuffer.SIZE` items. So when a `Producer` gets set, it sees that `RxRingBuffer.SIZE * 2` items got requested and will start emitting. But if it happens to be faster than its consumer, will result in `MissingBackpressureException`

**Relevant code from `OperatorObserveOn`** 
```java
@Override
public void onNext(final T t) {
    if (isUnsubscribed()) {
        return;
    }
    // The `queue` that `OperatorObserveOn` uses is bound to `RxRingBuffer.SIZE`
    if (!queue.offer(on.next(t))) { 
        onError(new MissingBackpressureException());
        return;
    }
    schedule();
}
```
