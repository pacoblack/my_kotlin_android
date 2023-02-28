package com.example.myapplication;

import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.internal.disposables.EmptyDisposable;
import io.reactivex.internal.schedulers.NewThreadWorker;
import io.reactivex.internal.schedulers.RxThreadFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class ContextAwareScheduler extends Scheduler {
    public static final ContextAwareScheduler INSTANCE =
            new ContextAwareScheduler();                       // (1)

    final NewThreadWorker worker;

    private ContextAwareScheduler() {
        this.worker = new NewThreadWorker(
                new RxThreadFactory("ContextAwareScheduler")); // (2)
    }
    @Override
    public Worker createWorker() {
        return new ContextAwareWorker(worker);                 // (3)
    }

    static final class ContextAwareWorker extends Worker {
        final CompositeDisposable tracking;                  // (4)
        final NewThreadWorker worker;

        public ContextAwareWorker(NewThreadWorker worker) {
            this.worker = worker;
            this.tracking = new CompositeDisposable();
        }

        @Override
        public void dispose() {
            tracking.dispose();
        }

        @Override
        public boolean isDisposed() {
            return tracking.isDisposed();
        }

        @Override
        public Disposable schedule(final Runnable run, long delay, TimeUnit unit) {
            if (isDisposed()) {                         // (2)
                return EmptyDisposable.INSTANCE;
            }

            final Object context = ContextManager.get();          // (3)
            Runnable a = new Runnable() {
                @Override
                public void run() {
                    ContextManager.set(context);                // (4)
                    run.run();
                }
            };

            return worker.scheduleActual(a, delay, unit, tracking);
        }
    }

    public static void main (String[] args) {
        Worker w = INSTANCE.createWorker();
        final CountDownLatch cdl = new CountDownLatch(1);

        ContextManager.set(1);
        w.schedule(
            new Runnable() {
                @Override
                public void run() {
                    System.out.println(Thread.currentThread());
                    System.out.println(ContextManager.get());
                }
            });

        ContextManager.set(2);
        w.schedule(new Runnable() {
            @Override
            public void run() {
                System.out.println(Thread.currentThread());
                System.out.println(ContextManager.get());
                cdl.countDown();

            }
        });

        try {
            cdl.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        ContextManager.set(3);

        Observable.timer(500, TimeUnit.MILLISECONDS, INSTANCE)
                .doOnNext(new Consumer<Long>() {
                    @Override
                    public void accept(Long aLong) throws Exception {
                        System.out.println(Thread.currentThread());
                        System.out.println(ContextManager.get());
                    }
                }).blockingFirst();

        w.dispose();
    }
}
