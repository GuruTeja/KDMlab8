/*
 * NewsTerp Engine - We report.  You decipher.
 * copyright (c) 2007 Colin Bayer, Jack Hebert
 *
 * CSE 472 Spring 2007 final project
 */
package com.ibm.cloudoe.samples;

public interface RelationExtractor {
	public Relation[] extract(TaggedSentence aSent);
}
