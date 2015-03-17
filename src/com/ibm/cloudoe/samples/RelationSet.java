/*
 * NewsTerp Engine - We report.  You decipher.
 * copyright (c) 2007 Colin Bayer, Jack Hebert
 *
 * CSE 472 Spring 2007 final project
 */

package com.ibm.cloudoe.samples;

import java.util.HashSet;
import java.util.Set;

public class RelationSet {
	public RelationSet(String aID) {
		mID = aID;
		mRelations = new HashSet<Relation>();
	}

	public void add(Relation aRel) {
		mRelations.add(aRel);
	}

	public String toSerialRep() {
		String rv = "set \"" + mID + "\";\n";

		for (Relation r : mRelations) {
			rv += r.toSerialRep() + ";\n";
		}

		rv += "endset;\n";

		return rv;
	}

	private Set<Relation> mRelations;
	private String mID;
}
