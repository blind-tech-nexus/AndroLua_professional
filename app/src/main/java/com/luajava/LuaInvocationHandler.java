/*
 * $Id: LuaInvocationHandler.java,v 1.4 2006/12/22 14:06:40 thiago Exp $
 * Copyright (C) 2003-2007 Kepler Project.
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.luajava;

import com.androlua.LuaContext;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * Class that implements the InvocationHandler interface.
 * This class is used in the LuaJava's proxy system.
 * When a proxy object is accessed, the method invoked is
 * called from Lua
 * @author Rizzato
 * @author Thiago Ponte
 */
public class LuaInvocationHandler implements InvocationHandler {
	private final LuaContext mContext;
	private LuaObject obj;

	public LuaInvocationHandler(LuaObject obj) {
		this.obj = obj;
		mContext=obj.getLuaState().getContext();
	}

	/**
	 * Function called when a proxy object function is invoked.
	 */
	public Object invoke(Object proxy, Method method, Object[] args) throws LuaException {
		synchronized (obj.L) {
			String methodName = method.getName();
			if (method.getDeclaringClass() == Object.class) {
				switch (methodName) {
					case "toString": return "LuaProxy@" + System.identityHashCode(proxy) + "[" + obj + "]";
					case "hashCode": return System.identityHashCode(proxy);
					case "equals": return proxy == args[0];
				}
			}
			LuaObject func;
			if (obj.isFunction()){
				func = obj;
			}
			else{
				func = obj.getField(methodName);
			}
			Class<?> retType = method.getReturnType();

			if (func.isNil()) {
				if (!java.lang.reflect.Modifier.isAbstract(method.getModifiers())) {
					try { return method.invoke(proxy, args); } catch (Throwable ignored) {}
				}
				if (retType.equals(boolean.class) || retType.equals(Boolean.class))
					return false;
				else if (retType.isPrimitive() || Number.class.isAssignableFrom(retType))
					return 0;
				else
					return null;
			}

			Object ret = null;
			try {
				if (retType.equals(Void.class) || retType.equals(void.class)) {
					func.call(args);
					ret = null;
				}
				else {
					ret = func.call(args);
					if (ret != null && ret instanceof Double) {
						ret = LuaState.convertLuaNumber((Double) ret, retType);
					} else if (ret != null && retType == String.class && !(ret instanceof String)) {
						ret = String.valueOf(ret);
					}
				}
			}
			catch (LuaException e) {
				if (mContext != null) mContext.sendError(methodName, e);
				else e.printStackTrace();
			}  	
			if (ret == null)
				if (retType.equals(boolean.class) || retType.equals(Boolean.class))
					return false;
				else if (retType.isPrimitive() || Number.class.isAssignableFrom(retType))
					return 0;
			return ret;
		}
	}
}
