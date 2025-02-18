/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package dev.latvian.mods.rhino.ast;

import dev.latvian.mods.rhino.CompilerEnvirons;
import dev.latvian.mods.rhino.ErrorReporter;
import dev.latvian.mods.rhino.Parser;
import dev.latvian.mods.rhino.Token;

import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Node for the root of a parse tree.  It contains the statements and functions
 * in the script, and a list of {@link Comment} nodes associated with the script
 * as a whole.  Node type is {@link Token#SCRIPT}.
 *
 * <p>Note that the tree itself does not store errors. To collect the parse errors
 * and warnings, pass an {@link ErrorReporter} to the
 * {@link Parser} via the
 * {@link CompilerEnvirons}.
 */
public class AstRoot extends ScriptNode {

	private SortedSet<Comment> comments;

	{
		type = Token.SCRIPT;
	}

	public AstRoot() {
	}

	public AstRoot(int pos) {
		super(pos);
	}

	/**
	 * Returns comment set
	 *
	 * @return comment set, sorted by start position. Can be {@code null}.
	 */
	public SortedSet<Comment> getComments() {
		return comments;
	}

	/**
	 * Sets comment list, and updates the parent of each entry to point
	 * to this node.  Replaces any existing comments.
	 *
	 * @param comments comment list.  can be {@code null}.
	 */
	public void setComments(SortedSet<Comment> comments) {
		if (comments == null) {
			this.comments = null;
		} else {
			if (this.comments != null) {
				this.comments.clear();
			}
			for (Comment c : comments) {
				addComment(c);
			}
		}
	}

	/**
	 * Add a comment to the comment set.
	 *
	 * @param comment the comment node.
	 * @throws IllegalArgumentException if comment is {@code null}
	 */
	public void addComment(Comment comment) {
		assertNotNull(comment);
		if (comments == null) {
			comments = new TreeSet<>(new AstNode.PositionComparator());
		}
		comments.add(comment);
		comment.setParent(this);
	}

	/**
	 * Visits the comment nodes in the order they appear in the source code.
	 * The comments are not visited by the {@link #visit} function - you must
	 * use this function to visit them.
	 *
	 * @param visitor the callback object.  It is passed each comment node.
	 *                The return value is ignored.
	 */
	public void visitComments(NodeVisitor visitor) {
		if (comments != null) {
			for (Comment c : comments) {
				visitor.visit(c);
			}
		}
	}

	/**
	 * Visits the AST nodes, then the comment nodes.
	 * This method is equivalent to calling {@link #visit}, then
	 * {@link #visitComments}.  The return value
	 * is ignored while visiting comment nodes.
	 *
	 * @param visitor the callback object.
	 */
	public void visitAll(NodeVisitor visitor) {
		visit(visitor);
		visitComments(visitor);
	}
}
