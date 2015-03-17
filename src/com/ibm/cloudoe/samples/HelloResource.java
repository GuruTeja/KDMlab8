package com.ibm.cloudoe.samples;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import java.io.*;
import java.net.URL;
import java.util.*;

@Path("/hello")
public class HelloResource {

	private static String FILE_NAME = "input.txt";

	@GET
	public String getInformation() {

		// 'VCAP_APPLICATION' is in JSON format, it contains useful information about a deployed application
		// String envApp = System.getenv("VCAP_APPLICATION");

		// 'VCAP_SERVICES' contains all the credentials of services bound to this application.
		// String envServices = System.getenv("VCAP_SERVICES");
		// JSONObject sysEnv = new JSONObject(System.getenv());

		return "Hi Pardhu!";
	}

	@GET
	@Produces("application/json")
	@Path("/KE/{sentence}")
	public String getKE(@PathParam("sentence") String sentence) throws JSONException, IOException {

		JSONArray others = new JSONArray();

		if (sentence.isEmpty()) {
			JSONObject jsonObject = new JSONObject();
			jsonObject.put("error", "Please send some text for analysis. Detected empty text.");
			return jsonObject.toString();
		}

		String root_path = this.getClass().getClassLoader().getResource("/").getPath();
		others.put(root_path);

		File outputFile = File.createTempFile("input", "text");

//		FileOutputStream fos = new FileOutputStream(outputFile);

		String path = outputFile.getAbsolutePath();
		others.put(path);

		PrintWriter printWriter = new PrintWriter(path);
		printWriter.println(sentence);
		printWriter.close();
//		fos.close();

		String nlp_path = this.getClass().getClassLoader().getResource("models").getPath();

		URL wn_path = null;
		int numToShow = 350;
		try {
			wn_path = this.getClass().getClassLoader()
					.getResource("dict");
			System.out.println(wn_path);
			others.put(wn_path);
		} catch (Exception e) {
			System.out.println(e.toString());
			others.put(e.toString());
		}
		int extr_phase = 1;

		/* build OpenNLP processing objects */
		if (!NLPToolkitManager.init(nlp_path
				+ "/english/sentdetect/EnglishSD.bin.gz", nlp_path
				+ "/english/tokenize/EnglishTok.bin.gz", nlp_path
				+ "/english/parser/tag.bin.gz", nlp_path
				+ "/english/parser/tagdict", nlp_path
				+ "/english/chunker/EnglishChunk.bin.gz", wn_path)) {
			System.err.println("Error creating NLP objects, exiting...");
			return "Error creating NLP objects, exiting...";
		}

		/* provide space to store the processed articles... */
		// TaggedArticle[] articles = new TaggedArticle[aArgs.length - idx];
		ArrayList<TaggedArticle> articleList = new ArrayList<TaggedArticle>();

		/* chop up and tag all of our articles. */
//		for (int n = 0; idx < aArgs.length; idx++, n++) {
		try {
			NewsRepoReader reader = new NewsRepoReader(path);
			NewsRepoArticle article = reader.GetNextArticle();
			int count = 0;
			while (article != null) {
				count += 1;
				System.out.println("*****************************");
				System.out.println(count + "/"
						+ reader.GetNumberOfArticle());
				articleList.add(new TaggedArticle(article.getUrl(), article
						.getArticle()));
				article = reader.GetNextArticle();
			}
			// articles[n] = new TaggedArticle(aArgs[idx], aArgs[idx]);
		} catch (IOException e) {
			System.out.println(e);
			others.put(e.toString());
		}
//		}

		// TODO: convert arrayList back to array?

		// do per-article-set fancy stuff here.
		HashMap<TaggedSentence.Chunk, Integer> np_pop_index = new HashMap<TaggedSentence.Chunk, Integer>();
		HashMap<TaggedSentence.Chunk, Integer> vp_pop_index = new HashMap<TaggedSentence.Chunk, Integer>();
		RelationExtractor re;

		if (extr_phase == 1) {
			re = new Phase1RelationExtractor();
		} else {
			re = new Phase2RelationExtractor();
		}

		int a_i = 0, s_i = 0;

		ArrayList<RelationSet> rel_sets = new ArrayList<RelationSet>();

		for (TaggedArticle a : articleList) {
			s_i = 0;

			RelationSet set = new RelationSet(a.getID());

			for (TaggedSentence s : a.getSentences()) {
				Relation[] r = null;

				if ((r = re.extract(s)) != null && r.length != 0) {
					/*
					 * System.out.println("Extracted relations for article " +
					 * a_i + ", sentence " + s_i + ": " + Arrays.toString(r));
					 */
					for (Relation rel : r) {
						rel.annotate(new SentenceNoAnnotation(s_i));
						rel.annotate(new HumanReadableSentenceAnnotation(s
								.humanReadableSentence()));
						set.add(rel);
					}
				}

				/*
				 * if (s.getChunks(ChunkType.SBAR).length != 0) {
				 * System.out.println(a_i + ":" + s_i + " (" + s + "; " +
				 * Arrays.toString(s.getChunks()) + ") has an SBAR."); }
				 */

				for (TaggedSentence.Chunk ck : s.getChunks()) {
					Integer ck_ct = null;

					if (ck.getType() == ChunkType.NP) {
						/*
						 * skip wh-determiners (which, that), wh-pronouns (what,
						 * who), and personal pronouns (it, he, she)
						 */
						TaggedWord[] w = ck.getWords();

						if (w.length == 1
								&& (w[0].getPOS() == PartOfSpeech.PERS_PN)
								|| (w[0].getPOS() == PartOfSpeech.WH_DET)
								|| (w[0].getPOS() == PartOfSpeech.WH_PN))
							continue;

						if ((ck_ct = np_pop_index.get(ck)) != null) {
							np_pop_index.put(ck, new Integer(
									ck_ct.intValue() + 1));
						} else {
							np_pop_index.put(ck, new Integer(1));
						}
					} else if (ck.getType() == ChunkType.VP) {
						if ((ck_ct = vp_pop_index.get(ck)) != null) {
							vp_pop_index.put(ck, new Integer(
									ck_ct.intValue() + 1));
						} else {
							vp_pop_index.put(ck, new Integer(1));
						}
					}
				}

				s_i++;
			}

			rel_sets.add(set);

			a_i++;
		}

		// don't ask why Java doesn't let you make genericized arrays. just
		// accept that this line works, and move on.
		Map.Entry<TaggedSentence.Chunk, Integer>[] np_pop_entries = (Map.Entry<TaggedSentence.Chunk, Integer>[]) new Map.Entry[0];

		np_pop_entries = np_pop_index.entrySet().toArray(np_pop_entries);

		Arrays.sort(np_pop_entries,
				new Comparator<Map.Entry<TaggedSentence.Chunk, Integer>>() {
					public int compare(
							Map.Entry<TaggedSentence.Chunk, Integer> aA,
							Map.Entry<TaggedSentence.Chunk, Integer> aB) {
						return -aA.getValue().compareTo(aB.getValue());
					}
				});

		Map.Entry<TaggedSentence.Chunk, Integer>[] vp_pop_entries = (Map.Entry<TaggedSentence.Chunk, Integer>[]) new Map.Entry[0];

		vp_pop_entries = vp_pop_index.entrySet().toArray(vp_pop_entries);

		Arrays.sort(vp_pop_entries,
				new Comparator<Map.Entry<TaggedSentence.Chunk, Integer>>() {
					public int compare(
							Map.Entry<TaggedSentence.Chunk, Integer> aA,
							Map.Entry<TaggedSentence.Chunk, Integer> aB) {
						return -aA.getValue().compareTo(aB.getValue());
					}
				});

		System.out
				.println("Most popular " + numToShow + " NPs in article set:");
		numToShow = np_pop_entries.length;
		for (int i = 0; i < numToShow; i++) {
			System.out.println(np_pop_entries[i].getKey() + " ("
					+ np_pop_entries[i].getValue() + ")");
		}

		System.out
				.println("Most popular " + numToShow + " VPs in article set:");
		numToShow = vp_pop_entries.length;
		for (int i = 0; i < numToShow; i++) {
			System.out.println(vp_pop_entries[i].getKey() + " ("
					+ vp_pop_entries[i].getValue() + ")");
		}

		System.out.println("==END==");
		others.put("Beginning Relations");
		// dump relation sets to a file.
		ArrayList<Object> relations = new ArrayList<Object>();

		for (RelationSet rs : rel_sets) {
			HashMap<String, Object> map = new HashMap<String, Object>();

			relations.add(rs.toObjectRep());
			System.out.println(rs.toSerialRep());
		}

		JSONObject object = new JSONObject();
		object.put("results", relations);
//		object.put("others", others);
		return object.toString();
	}

}