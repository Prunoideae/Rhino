/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

// API class

package dev.latvian.mods.rhino;

import dev.latvian.mods.rhino.util.DefaultRemapper;
import dev.latvian.mods.rhino.util.Remapper;
import dev.latvian.mods.rhino.util.wrap.TypeWrappers;

/**
 * Factory class that Rhino runtime uses to create new {@link Context}
 * instances.  A <code>ContextFactory</code> can also notify listeners
 * about context creation and release.
 * <p>
 * When the Rhino runtime needs to create new {@link Context} instance during
 * execution of {@link Context#enter()} or {@link Context}, it will call
 * {@link #makeContext()} of the current global ContextFactory.
 * See {@link #getGlobal()} and {@link #initGlobal(ContextFactory)}.
 * <p>
 * It is also possible to use explicit ContextFactory instances for Context
 * creation. This is useful to have a set of independent Rhino runtime
 * instances under single JVM. See {@link #call(ContextAction)}.
 * <p>
 * The following example demonstrates Context customization to terminate
 * scripts running more then 10 seconds and to provide better compatibility
 * with JavaScript code using MSIE-specific features.
 * <pre>
 * import dev.latvian.mods.rhino.*;
 *
 * class MyFactory extends ContextFactory
 * {
 *
 *     // Custom {@link Context} to store execution time.
 *     private static class MyContext extends Context
 *     {
 *         long startTime;
 *     }
 *
 *     static {
 *         // Initialize GlobalFactory with custom factory
 *         ContextFactory.initGlobal(new MyFactory());
 *     }
 *
 *     // Override {@link #makeContext()}
 *     protected Context makeContext()
 *     {
 *         MyContext cx = new MyContext();
 *         // Make Rhino runtime to call observeInstructionCount
 *         // each 10000 bytecode instructions
 *         cx.setInstructionObserverThreshold(10000);
 *         return cx;
 *     }
 *
 *     // Override {@link #hasFeature(Context, int)}
 *     public boolean hasFeature(Context cx, int featureIndex)
 *     {
 *         // Turn on maximum compatibility with MSIE scripts
 *         switch (featureIndex) {
 *             case {@link Context#FEATURE_MEMBER_EXPR_AS_FUNCTION_NAME}:
 *                 return true;
 *
 *             case {@link Context#FEATURE_RESERVED_KEYWORD_AS_IDENTIFIER}:
 *                 return true;
 *
 *             case {@link Context#FEATURE_PARENT_PROTO_PROPERTIES}:
 *                 return false;
 *         }
 *         return super.hasFeature(cx, featureIndex);
 *     }
 *
 *     // Override {@link #observeInstructionCount(Context, int)}
 *     protected void observeInstructionCount(Context cx, int instructionCount)
 *     {
 *         MyContext mcx = (MyContext)cx;
 *         long currentTime = System.currentTimeMillis();
 *         if (currentTime - mcx.startTime &gt; 10*1000) {
 *             // More then 10 seconds from Context creation time:
 *             // it is time to stop the script.
 *             // Throw Error instance to ensure that script will never
 *             // get control back through catch or finally.
 *             throw new Error();
 *         }
 *     }
 *
 *     // Override {@link #doTopCall(Callable,
 * Context, Scriptable,
 * Scriptable, Object[])}
 *     protected Object doTopCall(Callable callable,
 *                                Context cx, Scriptable scope,
 *                                Scriptable thisObj, Object[] args)
 *     {
 *         MyContext mcx = (MyContext)cx;
 *         mcx.startTime = System.currentTimeMillis();
 *
 *         return super.doTopCall(callable, cx, scope, thisObj, args);
 *     }
 *
 * }
 * </pre>
 */

public class ContextFactory {
	private static final ContextFactory global = new ContextFactory();

	private volatile boolean sealed;

	private final Object listenersLock = new Object();
	private volatile Object listeners;
	private boolean disabledListening;
	TypeWrappers typeWrappers;
	Remapper remapper = DefaultRemapper.INSTANCE;

	/**
	 * Listener of {@link Context} creation and release events.
	 */
	public interface Listener {
		/**
		 * Notify about newly created {@link Context} object.
		 */
		void contextCreated(Context cx);

		/**
		 * Notify that the specified {@link Context} instance is no longer
		 * associated with the current thread.
		 */
		void contextReleased(Context cx);
	}

	/**
	 * Get global ContextFactory.
	 *
	 * @see #hasExplicitGlobal()
	 * @see #initGlobal(ContextFactory)
	 */
	public static ContextFactory getGlobal() {
		return global;
	}

	/**
	 * Create new {@link Context} instance to be associated with the current
	 * thread.
	 * This is a callback method used by Rhino to create {@link Context}
	 * instance when it is necessary to associate one with the current
	 * execution thread. <code>makeContext()</code> is allowed to call
	 * {@link Context#seal(Object)} on the result to prevent
	 * {@link Context} changes by hostile scripts or applets.
	 */
	protected Context makeContext() {
		return new Context(this);
	}

	/**
	 * Implementation of {@link Context#hasFeature(int featureIndex)}.
	 * This can be used to customize {@link Context} without introducing
	 * additional subclasses.
	 */
	protected boolean hasFeature(Context cx, int featureIndex) {
		return switch (featureIndex) {
			case Context.FEATURE_MEMBER_EXPR_AS_FUNCTION_NAME -> false;
			case Context.FEATURE_RESERVED_KEYWORD_AS_IDENTIFIER -> true;
			case Context.FEATURE_PARENT_PROTO_PROPERTIES -> true;
			case Context.FEATURE_DYNAMIC_SCOPE -> false;
			case Context.FEATURE_STRICT_VARS -> false;
			case Context.FEATURE_STRICT_EVAL -> false;
			case Context.FEATURE_LOCATION_INFORMATION_IN_ERROR -> false;
			case Context.FEATURE_STRICT_MODE -> false;
			case Context.FEATURE_WARNING_AS_ERROR -> false;
			case Context.FEATURE_ENHANCED_JAVA_ACCESS -> false;
			case Context.FEATURE_V8_EXTENSIONS -> true;
			case Context.FEATURE_THREAD_SAFE_OBJECTS -> false;
			case Context.FEATURE_INTEGER_WITHOUT_DECIMAL_PLACE -> false;
			case Context.FEATURE_LITTLE_ENDIAN -> false;
			default -> throw new IllegalArgumentException(String.valueOf(featureIndex));
		};
	}

	/**
	 * Create class loader for generated classes.
	 * This method creates an instance of the default implementation
	 * of {@link GeneratedClassLoader}. Rhino uses this interface to load
	 * generated JVM classes when no {@link SecurityController}
	 * is installed.
	 * Application can override the method to provide custom class loading.
	 */
	protected GeneratedClassLoader createClassLoader(final ClassLoader parent) {
		return new DefiningClassLoader(parent);
	}

	/**
	 * Get ClassLoader to use when searching for Java classes.
	 * Unless it was explicitly initialized with
	 * {@link #initApplicationClassLoader(ClassLoader)} the method returns
	 * null to indicate that Thread.getContextClassLoader() should be used.
	 */
	public final ClassLoader getApplicationClassLoader() {
		return null;
	}

	/**
	 * Execute top call to script or function.
	 * When the runtime is about to execute a script or function that will
	 * create the first stack frame with scriptable code, it calls this method
	 * to perform the real call. In this way execution of any script
	 * happens inside this function.
	 */
	protected Object doTopCall(Callable callable, Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
		Object result = callable.call(cx, scope, thisObj, args);
		return result instanceof ConsString ? result.toString() : result;
	}

	/**
	 * Implementation of
	 * {@link Context#observeInstructionCount(int instructionCount)}.
	 * This can be used to customize {@link Context} without introducing
	 * additional subclasses.
	 */
	protected void observeInstructionCount(Context cx, int instructionCount) {
	}

	protected void onContextCreated(Context cx) {
		Object listeners = this.listeners;
		for (int i = 0; ; ++i) {
			Listener l = (Listener) Kit.getListener(listeners, i);
			if (l == null) {
				break;
			}
			l.contextCreated(cx);
		}
	}

	protected void onContextReleased(Context cx) {
		Object listeners = this.listeners;
		for (int i = 0; ; ++i) {
			Listener l = (Listener) Kit.getListener(listeners, i);
			if (l == null) {
				break;
			}
			l.contextReleased(cx);
		}
	}

	public final void addListener(Listener listener) {
		checkNotSealed();
		synchronized (listenersLock) {
			if (disabledListening) {
				throw new IllegalStateException();
			}
			listeners = Kit.addListener(listeners, listener);
		}
	}

	public final void removeListener(Listener listener) {
		checkNotSealed();
		synchronized (listenersLock) {
			if (disabledListening) {
				throw new IllegalStateException();
			}
			listeners = Kit.removeListener(listeners, listener);
		}
	}

	/**
	 * The method is used only to implement
	 * Context.disableStaticContextListening()
	 */
	final void disableContextListening() {
		checkNotSealed();
		synchronized (listenersLock) {
			disabledListening = true;
			listeners = null;
		}
	}

	/**
	 * Checks if this is a sealed ContextFactory.
	 *
	 * @see #seal()
	 */
	public final boolean isSealed() {
		return sealed;
	}

	/**
	 * Seal this ContextFactory so any attempt to modify it like to add or
	 * remove its listeners will throw an exception.
	 *
	 * @see #isSealed()
	 */
	public final void seal() {
		checkNotSealed();
		sealed = true;
	}

	protected final void checkNotSealed() {
		if (sealed) {
			throw new IllegalStateException();
		}
	}

	/**
	 * Call {@link ContextAction#run(Context cx)}
	 * using the {@link Context} instance associated with the current thread.
	 * If no Context is associated with the thread, then
	 * {@link #makeContext()} will be called to construct
	 * new Context instance. The instance will be temporary associated
	 * with the thread during call to {@link ContextAction#run(Context)}.
	 *
	 * @see ContextFactory#call(ContextAction)
	 * @see Context#call(ContextFactory factory, Callable callable,
	 * Scriptable scope, Scriptable thisObj,
	 * Object[] args)
	 */
	public final <T> T call(ContextAction<T> action) {
		return Context.call(this, action);
	}

	/**
	 * Get a context associated with the current thread, creating one if need
	 * be. The Context stores the execution state of the JavaScript engine, so
	 * it is required that the context be entered before execution may begin.
	 * Once a thread has entered a Context, then getCurrentContext() may be
	 * called to find the context that is associated with the current thread.
	 * <p>
	 * Calling <code>enterContext()</code> will return either the Context
	 * currently associated with the thread, or will create a new context and
	 * associate it with the current thread. Each call to
	 * <code>enterContext()</code> must have a matching call to
	 * {@link Context#exit()}.
	 * <pre>
	 *      Context cx = contextFactory.enterContext();
	 *      try {
	 *          ...
	 *          cx.evaluateString(...);
	 *      } finally {
	 *          Context.exit();
	 *      }
	 * </pre>
	 * Instead of using <code>enterContext()</code>, <code>exit()</code> pair consider
	 * using {@link #call(ContextAction)} which guarantees proper association
	 * of Context instances with the current thread.
	 * With this method the above example becomes:
	 * <pre>
	 *      ContextFactory.call(new ContextAction() {
	 *          public Object run(Context cx) {
	 *              ...
	 *              cx.evaluateString(...);
	 *              return null;
	 *          }
	 *      });
	 * </pre>
	 *
	 * @return a Context associated with the current thread
	 * @see Context#getCurrentContext()
	 * @see Context#exit()
	 * @see #call(ContextAction)
	 */
	public Context enterContext() {
		return enterContext(null);
	}

	/**
	 * Get a Context associated with the current thread, using the given
	 * Context if need be.
	 * <p>
	 * The same as <code>enterContext()</code> except that <code>cx</code>
	 * is associated with the current thread and returned if the current thread
	 * has no associated context and <code>cx</code> is not associated with any
	 * other thread.
	 *
	 * @param cx a Context to associate with the thread if possible
	 * @return a Context associated with the current thread
	 * @throws IllegalStateException if <code>cx</code> is already associated
	 *                               with a different thread
	 * @see #enterContext()
	 * @see #call(ContextAction)
	 */
	public final Context enterContext(Context cx) {
		return Context.enter(cx, this);
	}
}