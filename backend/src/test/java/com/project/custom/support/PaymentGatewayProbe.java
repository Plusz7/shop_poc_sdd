package com.project.custom.support;

import com.project.custom.payment.domain.PaymentGateway;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Wraps the {@link PaymentGateway} bean and records, for every call, whether a database transaction was open
 * on the calling thread — the provider must never be called inside one (Principle V, R-13).
 */
public class PaymentGatewayProbe implements BeanPostProcessor {

    private final List<Boolean> transactionActiveOnCall = new CopyOnWriteArrayList<>();

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (!(bean instanceof PaymentGateway gateway)) {
            return bean;
        }
        return Proxy.newProxyInstance(PaymentGateway.class.getClassLoader(), new Class<?>[]{PaymentGateway.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == PaymentGateway.class) {
                        transactionActiveOnCall.add(TransactionSynchronizationManager.isActualTransactionActive());
                    }
                    try {
                        return method.invoke(gateway, args);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                });
    }

    /** For each gateway call since the last {@link #reset()}: was a transaction open? */
    public List<Boolean> transactionActiveOnCall() {
        return List.copyOf(transactionActiveOnCall);
    }

    public void reset() {
        transactionActiveOnCall.clear();
    }
}
