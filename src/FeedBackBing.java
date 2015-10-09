import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.*;

import org.apache.commons.codec.binary.Base64;
import org.json.*;


public class FeedBackBing {
    //stopwords
    private static HashSet<String> stopwords = new HashSet<String>(Arrays.asList("a", "about", "above", "after", "again", "against", "all",
            "am", "an", "and", "any", "are", "aren't", "as", "at", "be", "because", "been", "before", "being", "below",
            "between", "both", "but", "by", "can't", "cannot", "could", "couldn't", "did", "didn't", "do", "dose", "doesn't",
            "doing", "don't", "down", "during", "each", "few", "for", "from", "further", "had", "hadn't", "has", "hasn't",
            "have", "haven't", "having", "he", "he'd", "he'll", "he's", "her", "here", "here's", "hers", "herself", "him",
            "himself", "his", "how", "how's", "i", "i'd", "i'll", "i'm", "i've", "if", "in", "into", "is", "isn't", "it",
            "it's", "its", "itself", "let's", "me", "more", "most", "mustn't", "my", "myself", "no", "nor", "not", "of", "off",
            "on", "once", "only", "or", "other", "ought", "our", "ours", "ourself", "out", "over", "own", "same", "shan't",
            "she", "she'd", "she'll", "she's", "should", "shouldn't", "so", "some", "such", "than", "that", "that's", "the",
            "their", "theirs", "them", "themselves", "then", "there", "there's", "these", "they", "they'd", "they'll",
            "they're", "they've", "this", "those", "through", "to", "too", "under", "until", "up", "very", "was", "wasn't",
            "we", "we'd", "we'll", "we're", "we've", "were", "weren't", "what", "what's", "when", "when's", "where", "where's",
            "which", "while", "who", "who's", "whom", "why", "why's", "with", "won't", "would", "wouldn't", "you", "you'd",
            "you'll", "you're", "you've", "your", "yours", "yourself", "yourselves"));
    public static int relevant;//number of relevant docs, used to calculate precision
    public static String accountKeyEnc;//encoded account key
    public static void main(String[] args) throws IOException{
        if (args.length != 3) {
            System.out.println("Usage of this script: java FeedbackBing <client-key> <precision> <'query'>");
            return;
        }

        String accountKey = args[0];
        double expectedPrecision = Double.parseDouble(args[1]);
        String query = args[2];

        byte[] accountKeyBytes = Base64.encodeBase64((accountKey + ":" + accountKey).getBytes());
        accountKeyEnc = new String(accountKeyBytes);


        String outFileAddress = System.getProperty("user.dir") + "/transcript.txt";
        FileWriter fileWriter = new FileWriter(outFileAddress);//Write results to the local file

        double precision = Double.MIN_VALUE;

        String s = new String();
        String[] str = query.replaceAll("\\W", " ").trim().split("\\s+");
        for (int i = 0; i < str.length; i++) {
            s += ("'" + str[i]  + "'" + " ");
        }
        s = s.substring(0, s.length() - 1);
        String prefix = "https://api.datamarket.azure.com/Bing/Search/Web?Query=";
        String suffix = "&$top=10&$format=json";
        String bingUrl = prefix + URLEncoder.encode(s, "UTF-8") + suffix;

        JSONArray array = parse(bingUrl);

        printAtBeginning(query, accountKey, bingUrl, expectedPrecision, array.length());

        int iter = 0;
        while (precision < expectedPrecision) {
            if (precision == 0) break;
            fileWriter.write("ROUND: " + (iter + 1) + '\n');
            fileWriter.write("QUERY: " + query + '\n');
            fileWriter.write('\n');

            if (iter == 0 && array.length() < 10) {
                System.out.println("Less than 10 documents in the first iteration, stop");
                break;
            }
            relevant = 0;
            Hashtable<String, Integer> doc = new Hashtable<String, Integer>();
            for (int i = 0; i < array.length(); i++) {
                userFeedback(array, doc, fileWriter, i);
            }
            precision = 1.0 * relevant / array.length();

            fileWriter.write("PRECISION: " + precision + '\n');
            fileWriter.write('\n');
            fileWriter.write('\n');
            fileWriter.write("-----------------------" + '\n');

            String augment = Rocchio(query, array, doc);
            String mQuery = query + " " + augment;

            s = "";
            str = mQuery.split("\\s");
            for (int i = 0; i < str.length; i++) {
                s += ("'" + str[i]  + "'" + " ");
            }
            s = s.substring(0, s.length() - 1);
            bingUrl = prefix + URLEncoder.encode(s, "UTF-8") + suffix;
            array = parse(bingUrl);
            printFeedBackSummary(query, augment, accountKey, bingUrl, precision, expectedPrecision, array.length());
            query = mQuery;
            iter++;
        }
        fileWriter.close();
    }

    //Parse String bingUrl to JSONArray
    private static JSONArray parse(String bingUrl) {
        try {
            URL url = new URL(bingUrl);
            URLConnection urlConnection = url.openConnection();
            urlConnection.setRequestProperty("Authorization", "Basic " + accountKeyEnc);
            InputStream inputStream = (InputStream) urlConnection.getContent();
            byte[] contentRaw = new byte[urlConnection.getContentLength()];
            inputStream.read(contentRaw);
            String content = new String(contentRaw);
            JSONObject obj = new JSONObject(content);
            JSONArray array = obj.getJSONObject("d").getJSONArray("results");
            return array;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    //Ask the user whether the doc is relevant or irrelevant
    private static void userFeedback(JSONArray array, Hashtable<String, Integer> doc, FileWriter fileWriter, int i) {
        System.out.println("Result " + (i + 1));
        System.out.println("[");
        String URL = array.getJSONObject(i).getString("Url");
        String title = array.getJSONObject(i).getString("Title");
        String description = array.getJSONObject(i).getString("Description");
        System.out.println("URL: " + URL);
        System.out.println("Title: " + title);
        System.out.println("Summary: " + description);
        System.out.println("]");
        System.out.print("Relevant(Y/N): ");
        Scanner input = new Scanner(System.in);
        String res = input.nextLine();
        boolean flag = false;
        if (res.matches("[Yy][Ee][Ss]|[Yy]")) {
            flag = true;
            relevant++;
            doc.put(title, 1);
        }
        else doc.put(title, 0);
        System.out.println();
        try {
            fileWriter.write("Result " + (i + 1) + '\n');
            if (flag)
                fileWriter.write("Relevant: " + "YES" + '\n');
            else fileWriter.write("Relevant: " + "NO" + '\n');
            fileWriter.write("[" + '\n');
            fileWriter.write("URL: " + URL + '\n');
            fileWriter.write("Title: " + title + '\n');
            fileWriter.write("Summary: " + description + '\n');
            fileWriter.write("]" + '\n');
            fileWriter.write('\n');
            fileWriter.write('\n');
            fileWriter.write('\n');
        }catch (IOException e) {
            e.printStackTrace();
        }
    }

    //Handle feedback
    private static void printFeedBackSummary(String oQuery, String augment,String accountKey, String bingUrl, double precision, double expectedPrecision, int num) {
        System.out.println("------------------------");
        System.out.println("FEEDBACK SUMMARY");
        System.out.println("Query: " + oQuery);
        System.out.println("Precision: " + precision);
        if (precision >= expectedPrecision) {
            System.out.println("Desired precision reached, done");
        }
        else if (precision == 0)
            System.out.println("No relevant document, STOP");
        else {
            System.out.println("Still below the desired precision of " +  expectedPrecision);
            System.out.println("Indexing results ....");
            System.out.println("Indexing results ....");
            System.out.println("Augmenting by " + augment);
            System.out.println("Parameter:");
            System.out.println("Client Key = " + accountKey);
            System.out.println("Query      = " + (oQuery + " " + augment));
            System.out.println("Precision  = " + expectedPrecision);
            System.out.println("URL: " + bingUrl);
            System.out.println("Total number of results : " + num);
            System.out.println("Bing Search Results:");
            System.out.println("----------------------");
        }
    }

    //Things printed at the beginning
    private static void printAtBeginning(String query, String accountKey, String bingUrl, double expectedPrecision, int num) {
        System.out.println("Parameters:");
        System.out.println("Client Key = " + accountKey);
        System.out.println("Query      = " + query);
        System.out.println("Precision  = " + expectedPrecision);
        System.out.println("URL: " + bingUrl);
        System.out.println("Total number of results : " + num);
        System.out.println("Bing Search Results:");
        System.out.println("----------------------");
    }

    //Use the Rocchio Algorithm for query modification. Add two words each time
    private static String Rocchio(String query, JSONArray array, Hashtable<String, Integer> doc) {
        String[] queryArray = query.trim().toLowerCase().replaceAll("\\W", " ").trim().split("\\s+");
        HashSet<String> set = new HashSet<String>();
        for (int i = 0; i < queryArray.length; i++)
            set.add(queryArray[i]);

        Hashtable<String, Integer> words = new Hashtable<String, Integer>();//word -> position
        Hashtable<Integer, String> invertedWords = new Hashtable<Integer, String>();//position -> words
        List<Integer> R = new LinkedList<Integer>();//set of relevant docs
        List<Integer> NR = new LinkedList<Integer>();//set of irrelevant docs
        List<Integer> q = new LinkedList<Integer>();// query vector
        int RSize = 0;
        int NRSize = 0;

        int p = 0;//The current position of "words"
        for (int i = 0; i < array.length(); i++) {
            String title = array.getJSONObject(i).getString("Title");
            String description = array.getJSONObject(i).getString("Description");
            //parse "description" and "title"
            String[] terms = (description + title).toLowerCase().replaceAll("(?!')\\W", " ").replaceAll("\\d", " ").trim().split("\\s+");
            if (doc.get(title) == 1) { // if the doc is relevant
                RSize++;
                for (int j = 0; j < terms.length; j++) {
                    if (stopwords.contains(terms[j])) continue;// filter the stopwords out
                    if (R.size() < p) {
                        for (int k = R.size(); k < p; k++)
                            R.add(0);
                    }
                    if (!words.containsKey(terms[j])) {
                        q.add(0);
                        words.put(terms[j], p);
                        invertedWords.put(p, terms[j]);
                        R.add(1);
                        p++;
                    } else {
                        int index = words.get(terms[j]);
                        R.set(index, R.get(index) + 1);
                    }
                }
            } else { // if doc irrelevant
                NRSize++;
                for (int j = 0; j < terms.length; j++) {
                    if (NR.size() < p) {
                        for (int k = NR.size(); k < p; k++)
                            NR.add(0);
                    }
                    if (stopwords.contains(terms[j])) continue;
                    if (!words.containsKey(terms[j])) {
                        q.add(0);
                        words.put(terms[j], p);
                        invertedWords.put(p, terms[j]);
                        NR.add(p, 1);
                        p++;
                    } else {
                        int index = words.get(terms[j]);
                        NR.set(index, NR.get(index) + 1);
                    }
                }
            }
        }
        //add zero to the end of R/NR list
        if (R.size() < NR.size()) {
            for (int i = R.size(); i < NR.size(); i++)
                R.add(0);
        }
        if (R.size() > NR.size()) {
            for (int i = NR.size(); i < R.size(); i++)
                NR.add(0);
        }

        for (int i = 0; i < queryArray.length; i++) {
            if (words.containsKey(queryArray[i])) {
                int index = words.get(queryArray[i]);
                q.set(index, q.get(index) + 1);
            }
        }
        //Parameters for Rocchio Algorithms
        double a = 1;
        double b = 0.75;
        double c = 0.15;
        Double[] qm = new Double[words.size()];//modified query vector
        Hashtable<Double, LinkedList<Integer>> h = new Hashtable<Double, LinkedList<Integer>>();//weight --> position


        for (int i = 0; i < qm.length; i++) {
            qm[i] = a * q.get(i) + b / RSize * R.get(i) - c / NRSize * NR.get(i);//calculate modified query vector
            if (qm[i] < 0) qm[i] = 0.0;
            if (!h.containsKey(qm[i])) {
                LinkedList<Integer> list = new LinkedList<Integer>();
                list.add(i);
                h.put(qm[i], list);
            }
            else {
                LinkedList<Integer> list =h.get(qm[i]);
                list.add(i);
                h.put(qm[i], list);
            }
        }
        Arrays.sort(qm, Collections.reverseOrder());//Sort based on the weight
        String[] addWords = new String[2];
        int count = 0;
        label:
        for (int i = 0; i < qm.length; i++) {
            LinkedList<Integer> list = h.get(qm[i]);
            for (int j = 0; j < list.size(); j++) {
                if (!set.contains(invertedWords.get(list.get(j)))) {
                    addWords[count] = invertedWords.get(list.get(j));
                    count++;
                }
                if (count == 2) break label;
            }
        }
        String augment = addWords[0] + " " + addWords[1];
        return augment;
    }
}
