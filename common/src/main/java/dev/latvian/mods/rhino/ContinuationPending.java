/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package dev.latvian.mods.rhino;

import java.io.Serial;

/**
 * Exception thrown by
 * {@link Context#executeScriptWithContinuations(Script, Scriptable)}
 * and {@link Context#callFunctionWithContinuations(Callable, Scriptable, Object[])}
 * when execution encounters a continuation captured by
 * {@link Context#captureContinuation()}.
 * Exception will contain the captured state needed to restart the continuation
 * with {@link Context#resumeContinuation(Object, Scriptable, Object)}.
 *
 * @author Norris Boyd
 */
public class ContinuationPending extends RuntimeException {
	@Serial
	private static final long serialVersionUID = 4956008116771118856L;
	private NativeContinuation continuationState;
	private Object applicationState;

	/**
	 * Construct a ContinuationPending exception. Internal call only;
	 * users of the API should get continuations created on their behalf by
	 * calling {@link Context#executeScriptWithContinuations(Script, Scriptable)}
	 * and {@link Context#callFunctionWithContinuations(Callable, Scriptable, Object[])}
	 * Creating subclasses allowed.
	 *
	 * @param continuationState Internal Continuation object
	 */
	protected ContinuationPending(NativeContinuation continuationState) {
		this.continuationState = continuationState;
	}

	/**
	 * Get continuation object. The only
	 * use for this object is to be passed to
	 * {@link Context#resumeContinuation(Object, Scriptable, Object)}.
	 *
	 * @return continuation object
	 */
	public Object getContinuation() {
		return continuationState;
	}

	/**
	 * Set continuation object. Allows subclasses to modify the internal state.
	 *
	 * @param continuation object
	 */
	public void setContinuation(NativeContinuation continuation) {
		this.continuationState = continuation;
	}

	/**
	 * @return internal continuation state
	 */
	NativeContinuation getContinuationState() {
		return continuationState;
	}

	/**
	 * Store an arbitrary object that applications can use to associate
	 * their state with the continuation.
	 *
	 * @param applicationState arbitrary application state
	 */
	public void setApplicationState(Object applicationState) {
		this.applicationState = applicationState;
	}

	/**
	 * @return arbitrary application state
	 */
	public Object getApplicationState() {
		return applicationState;
	}
}
