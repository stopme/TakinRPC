package com.takin.rpc.server.invoke;

import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;
import com.google.inject.Inject;
import com.takin.rpc.remoting.exception.NoImplClassException;
import com.takin.rpc.remoting.netty4.RemotingProtocol;
import com.takin.rpc.server.GuiceDI;
import com.takin.rpc.server.ServiceInfosHolder;
import com.takin.rpc.server.tcc.TccProvider;
import com.takin.rpc.server.tcc.TccProvider.Tcc;

import net.sf.cglib.reflect.FastClass;
import net.sf.cglib.reflect.FastMethod;

/**
 * 此处可以加入事务控制
 *
 * @author WangYazhou
 * @date  2017年5月8日 上午11:57:40
 * @see
 */
@Singleton
public class CGlibInvoker implements Invoker {

    private static final Logger logger = LoggerFactory.getLogger(CGlibInvoker.class);

    @Inject
    public CGlibInvoker() {
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Object invoke(RemotingProtocol msg) throws Exception {
        Stopwatch watch = Stopwatch.createStarted();
        Object[] args = msg.getArgs();
        Class<?> implClass = GuiceDI.getInstance(ServiceInfosHolder.class).getImplClass(msg.getDefineClass(), msg.getImplClass());
        if (implClass == null) {
            throw new NoImplClassException(msg.getDefineClass().getName());
        }

        logger.info(implClass.getName());

        Tcc tcc = GuiceDI.getInstance(TccProvider.class).getCompensable(implClass);

        try {
            FastClass fastClazz = FastClass.create(implClass);
            // fast class反射调用  
            Object target = fastClazz.newInstance();

            FastMethod method = fastClazz.getMethod(msg.getMethod(), msg.getmParamsTypes());
            Object obj = method.invoke(target, args);
            logger.info(String.format("cglib invoke use:%s", watch.toString()));
            if (tcc != null) {
                doconfirmorcancel(msg.getDefineClass(), tcc.getConfirm(), msg.getMethod(), msg.getmParamsTypes(), args);
            }
            return obj;
        } catch (Exception e) {
            if (tcc != null) {
                doconfirmorcancel(msg.getDefineClass(), tcc.getCancel(), msg.getMethod(), msg.getmParamsTypes(), args);
                return "do cancel";
            } else {
                throw e;
            }
        }
    }

    private void doconfirmorcancel(Class<?> clazz, String lookup, String method, //
                    Class<?>[] mParamsTypes, Object[] args) throws Exception {
        Class<?> confirmclass = GuiceDI.getInstance(ServiceInfosHolder.class).getImplClass(clazz, lookup);
        FastClass confrimfast = FastClass.create(confirmclass);
        // fast class反射调用  
        Object confirmtarget = confrimfast.newInstance();

        FastMethod confirmmethod = confrimfast.getMethod(method, mParamsTypes);
        confirmmethod.invoke(confirmtarget, args);
    }

}
