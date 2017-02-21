import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.nio.file.Files;
import java.nio.*;
import edu.smu.tspell.wordnet.*;
import java.io.*; 
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.util.ArrayUtils;
import static javafx.beans.binding.Bindings.concat;
import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.*; 
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
 
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author AvinashKumarPrajapati
 */
public class tfidf {
    
    
     /**
  * Stemmer, implementing the Porter Stemming Algorithm
  *
  * The Stemmer class transforms a word into its root form.  The input
  * word can be provided a character at time (by calling add()), or at once
  * by calling one of the various stem(something) methods.
  */

class Stemmer
{  private char[] b= null;
   private int i,     /* offset into b */
               i_end, /* offset to end of stemmed word */
               j, k;
   private static final int INC = 50;
                     /* unit of size whereby b is increased */
   public Stemmer()
   {  b = new char[INC];
      i = 0;
      i_end = 0;
   }

   /**
    * Add a character to the word being stemmed.  When you are finished
    * adding characters, you can call stem(void) to stem the word.
    */

   public void add(char ch)
   {  if (i == b.length)
      {  char[] new_b = new char[i+INC];
         for (int c = 0; c < i; c++) new_b[c] = b[c];
         b = new_b;
      }
      b[i++] = ch;
   }


   /** Adds wLen characters to the word being stemmed contained in a portion
    * of a char[] array. This is like repeated calls of add(char ch), but
    * faster.
    */

   public void add(char[] w, int wLen)
   {  if (i+wLen >= b.length)
      {  char[] new_b = new char[i+wLen+INC];
         for (int c = 0; c < i; c++) new_b[c] = b[c];
         b = new_b;
      }
      for (int c = 0; c < wLen; c++) b[i++] = w[c];
   }

   /**
    * After a word has been stemmed, it can be retrieved by toString(),
    * or a reference to the internal buffer can be retrieved by getResultBuffer
    * and getResultLength (which is generally more efficient.)
    */
   public String toString() { return new String(b,0,i_end); }

   /**
    * Returns the length of the word resulting from the stemming process.
    */
   public int getResultLength() { return i_end; }

   /**
    * Returns a reference to a character buffer containing the results of
    * the stemming process.  You also need to consult getResultLength()
    * to determine the length of the result.
    */
   public char[] getResultBuffer() { return b; }

   /* cons(i) is true <=> b[i] is a consonant. */

   private final boolean cons(int i)
   {  switch (b[i])
      {  case 'a': case 'e': case 'i': case 'o': case 'u': return false;
         case 'y': return (i==0) ? true : !cons(i-1);
         default: return true;
      }
   }

   /* m() measures the number of consonant sequences between 0 and j. if c is
      a consonant sequence and v a vowel sequence, and <..> indicates arbitrary
      presence,

         <c><v>       gives 0
         <c>vc<v>     gives 1
         <c>vcvc<v>   gives 2
         <c>vcvcvc<v> gives 3
         ....
   */

   private final int m()
   {  int n = 0;
      int i = 0;
      while(true)
      {  if (i > j) return n;
         if (! cons(i)) break; i++;
      }
      i++;
      while(true)
      {  while(true)
         {  if (i > j) return n;
               if (cons(i)) break;
               i++;
         }
         i++;
         n++;
         while(true)
         {  if (i > j) return n;
            if (! cons(i)) break;
            i++;
         }
         i++;
       }
   }

   /* vowelinstem() is true <=> 0,...j contains a vowel */

   private final boolean vowelinstem()
   {  int i; for (i = 0; i <= j; i++) if (! cons(i)) return true;
      return false;
   }

   /* doublec(j) is true <=> j,(j-1) contain a double consonant. */

   private final boolean doublec(int j)
   {  if (j < 1) return false;
      if (b[j] != b[j-1]) return false;
      return cons(j);
   }

   /* cvc(i) is true <=> i-2,i-1,i has the form consonant - vowel - consonant
      and also if the second c is not w,x or y. this is used when trying to
      restore an e at the end of a short word. e.g.

         cav(e), lov(e), hop(e), crim(e), but
         snow, box, tray.

   */

   private final boolean cvc(int i)
   {  if (i < 2 || !cons(i) || cons(i-1) || !cons(i-2)) return false;
      {  int ch = b[i];
         if (ch == 'w' || ch == 'x' || ch == 'y') return false;
      }
      return true;
   }

   private final boolean ends(String s)
   {  int l = s.length();
      int o = k-l+1;
      if (o < 0) return false;
      for (int i = 0; i < l; i++) if (b[o+i] != s.charAt(i)) return false;
      j = k-l;
      return true;
   }

   /* setto(s) sets (j+1),...k to the characters in the string s, readjusting
      k. */

   private final void setto(String s)
   {  int l = s.length();
      int o = j+1;
      for (int i = 0; i < l; i++) b[o+i] = s.charAt(i);
      k = j+l;
   }

   /* r(s) is used further down. */

   private final void r(String s) { if (m() > 0) setto(s); }

   /* step1() gets rid of plurals and -ed or -ing. e.g.

          caresses  ->  caress
          ponies    ->  poni
          ties      ->  ti
          caress    ->  caress
          cats      ->  cat

          feed      ->  feed
          agreed    ->  agree
          disabled  ->  disable

          matting   ->  mat
          mating    ->  mate
          meeting   ->  meet
          milling   ->  mill
          messing   ->  mess

          meetings  ->  meet

   */

   private final void step1()
   {  if (b[k] == 's')
      {  if (ends("sses")) k -= 2; else
         if (ends("ies")) setto("y"); else
         if (b[k-1] != 's') k--;
      }
      if (ends("ied")) setto("y"); else
      if (ends("nly")) setto("n"); else    
      if (ends("ceed")) setto("cess"); else
      if (ends("us")) setto("us"); else 
      if (ends("eed")) { if (m() > 0) k--; } else
      if (ends("ing") && vowelinstem()) setto("e"); else 
      if (ends("ed") && vowelinstem())
      {  k = j;
         if (ends("at")) setto("ate"); else
         if (ends("bl")) setto("ble"); else
         if (ends("iz")) setto("ize"); else
         if (doublec(k))
         {  k--;
            {  int ch = b[k];
               if (ch == 'l' || ch == 's' || ch == 'z') k++;
            }
         }
         else if (m() >= 1 && cvc(k)) setto("e");
     }
     else if (ends("ed")) setto("e"); 
   }

   /* step2() turns terminal y to i when there is another vowel in the stem. */

   private final void step2(){} //{ if (ends("y") && vowelinstem()) b[k] = 'i'; }

   /* step3() maps double suffices to single ones. so -ization ( = -ize plus
      -ation) maps to -ize etc. note that the string before the suffix must give
      m() > 0. */

   private final void step3() { if (k == 0) return; /* For Bug 1 */ switch (b[k-1])
   {
       case 'a': if (ends("ational")) { r("ate"); break; }
                 if (ends("tional")) { r("tion"); break; }
                 break;
       case 'c': if (ends("ency")) { r("ence"); break; }
                 if (ends("ancy")) { r("ance"); break; }
                 break;
       case 'e': if (ends("izer")) { r("ize"); break; }
                 break;
       case 'l': if (ends("bly")) { r("ble"); break; }
                 if (ends("ally")) { r("al"); break; }
                 if (ends("ently")) { r("ent"); break; }
                 if (ends("ely")) { r("e"); break; }
                 if (ends("lessly")) { r("less"); break; }
                 if (ends("fully")) { r("ful"); break; }
                 if (ends("ously")) { r("ous"); break; }
                 break;
       case 'o': if (ends("ization")) { r("ize"); break; }
                 if (ends("ation")) { r("ate"); break; }
                 if (ends("ator")) { r("ate"); break; }
                 break;
       case 's': if (ends("alism")) { r("al"); break; }
                 if (ends("iveness")) { r("ive"); break; }
                 if (ends("fulness")) { r("ful"); break; }
                 if (ends("ousness")) { r("ous"); break; }
                 break;
       case 't': if (ends("ality")) { r("al"); break; }
                 if (ends("ivity")) { r("ive"); break; }
                 if (ends("bility")) { r("ble"); break; }
                 break;
       case 'g': if (ends("logy")) { r("log"); break; }
   } }

   /* step4() deals with -ic-, -full, -ness etc. similar strategy to step3. */

   private final void step4() { switch (b[k])
   {
       case 'e': if (ends("icate")) { r("ic"); break; }
                 if (ends("ative")) { r(""); break; }
                 if (ends("alize")) { r("al"); break; }
                 break;
       case 'i': if (ends("icity")) { r("ic"); break; }
                 break;
       case 'l': if (ends("ical")) { r("ic"); break; }
                 if (ends("ful")) { r(""); break; }
                 break;
       case 's': if (ends("ness")) { r(""); break; }
                 break;
   } }

   /* step5() takes off -ant, -ence etc., in context <c>vcvc<v>. */

   private final void step5()
   {   if (k == 0) return; /* for Bug 1 */ switch (b[k-1])
       {  //case 'a': if (ends("al")){r("e"); break;} break;
          case 'c': if (ends("ence")) break; return;
          //case 'e': if (ends("er")) break; return;
          case 'i': if (ends("scopic")) {r("scope");break;} return;
          case 'l': if (ends("able")) break;
                    if (ends("ally")){ r("al"); break;}
                    if (ends("tly")){ r("t"); break;}
                    if (ends("iable")){ r("y"); break;}    
                    if (ends("ible")) break; return;
          case 'n': if (ends("ant")) break;
                    //if (ends("ement")) break;
                    if (ends("ment")) break;
                    /* element etc. not stripped before the m */
                    if (ends("ent")) break; return;
          case 'o': if (ends("ion") && j >= 0 && (b[j] == 's' || b[j] == 't')) break;
                                    /* j >= 0 fixes Bug 2 */
                    if (ends("ou")) break; return;
                    /* takes care of -ous */
          case 's': if (ends("ism")) break; return;
          case 't': if (ends("iti")) break; return;
          case 'u': if (ends("ous")) break; return;
          case 'y': if (ends("fye")){r("fy"); break;} return;
          case 'v': if (ends("ive")) break; return;
          case 'z': if (ends("ize")) break; return;
          default: return;
       }
       if (m() > 1) k = j;
   
   }
   /* step6() removes a final -e if m() > 1. */

   private final void step6(){}
   /*{  j = k;
      if (b[k] == 'e')
      {  int a = m();
         if (a > 1 || a == 1 && !cvc(k-1)) k--;
      }
      if (b[k] == 'l' && doublec(k) && m() > 1) k--;
   }
*/
   /** Stem the word placed into the Stemmer buffer through calls to add().
    * Returns true if the stemming process resulted in a word different
    * from the input.  You can retrieve the result with
    * getResultLength()/getResultBuffer() or toString().
    */

public void stem()
   {  k = i - 1;
      if (k > 1) { step1(); step2(); step3(); step4(); step5(); step6(); }
      i_end = k+1; i = 0;
   }

   /** Test program for demonstrating the Stemmer.  It reads text from a
    * a list of files, stems each word, and writes the result to standard
    * output. Note that the word stemmed is expected to be in lower case:
    * forcing lower case must be done outside the Stemmer class.
    * Usage: Stemmer file-name file-name ...
    */
public void intermediate()  throws SQLException, ClassNotFoundException, InterruptedException
{
    String insertIntoProduct=null;  
 insertIntoProduct="INSERT INTO SAMPLE1 SELECT WORD , COUNT (WORD) AS FREQUENCY , NOOFFILES FROM SAMPLE GROUP BY WORD , NOOFFILES ORDER BY FREQUENCY DESC";
                        ResultSet rs1 = db.runSql(insertIntoProduct);
                        rs1.close();
                        insertIntoProduct="INSERT  INTO DEMO SELECT sample1.word, POLOTICAL.frequency as frequency1 , sample1.frequency as frequency2,POLOTICAL.nooffiles as nooffiles1, sample1.nooffiles as nooffiles2,POLOTICAL.tf ,POLOTICAL.idf,POLOTICAL.tfidf,POLOTICAL.flag  FROM POLOTICAL INNER JOIN SAMPLE1 ON POLOTICAL.word=SAMPLE1.word";
                        ResultSet rs12 = db.runSql(insertIntoProduct);
                        rs12.close();
                        insertIntoProduct="INSERT INTO DEMO (WORD,FREQUENCY2,NOOFFILES2) SELECT * FROM SAMPLE1 WHERE WORD NOT IN (SELECT WORD FROM DEMO)";
                        ResultSet rs5 = db.runSql(insertIntoProduct);
                        rs5.close();
                        insertIntoProduct="UPDATE DEMO SET FLAG=1"; 
                        ResultSet rs14 = db.runSql(insertIntoProduct);
                        rs14.close();
                        insertIntoProduct="INSERT INTO DEMO (WORD,FREQUENCY1,NOOFFILES1,TF,IDF,TFIDF,FLAG) SELECT * FROM POLOTICAL WHERE WORD NOT IN (SELECT WORD FROM DEMO)";
                        ResultSet rs6 = db.runSql(insertIntoProduct);                                            
                        rs6.close();
                        insertIntoProduct="INSERT INTO DEMO1 select  word,(frequency1 + frequency2) as frequency ,(nooffiles1 + nooffiles2) as nooffiles ,TF,IDF,TFIDF,FLAG from demo ";
                        ResultSet rs7 = db.runSql(insertIntoProduct);
                        rs7.close();
                        insertIntoProduct="TRUNCATE TABLE POLOTICAL";
                        ResultSet rs8 = db.runSql(insertIntoProduct);
                        rs8.close();
                        insertIntoProduct="INSERT INTO POLOTICAL SELECT * FROM DEMO1";
                        ResultSet rs9 = db.runSql(insertIntoProduct);
                        rs9.close();

}

public void algorithm(int TOTALNOOFFILES )  throws SQLException, ClassNotFoundException, InterruptedException
{
     String insertIntoProduct=null;  
 insertIntoProduct="select * from POLOTICAL";
                        ResultSet rs16 = db.runSql(insertIntoProduct);
                        while (rs16.next())
                        {
                            String WORD =rs16.getString("WORD");
                            double FREQUENCY = rs16.getInt("FREQUENCY");
                            double NOOFFILES = rs16.getInt("NOOFFILES");
                            double TF = rs16.getInt("TF");
                            double IDF = rs16.getInt("IDF");
                            double TFIDF = rs16.getInt("TFIDF");
                            int FLAG = rs16.getInt("FLAG");
                            double FREQUENCYOFNEW = 1 ;
                            System.out.println(WORD+"  "+FREQUENCY+"  "+NOOFFILES+"  "+TF+"  "+IDF+"  "+TFIDF+"  "+FLAG);
                            insertIntoProduct="select frequency from sample1 where word ='"+WORD+"'";
                            ResultSet rs17 = db.runSql(insertIntoProduct);
                             while (rs17.next())
                            {
                               FREQUENCYOFNEW = rs17.getInt("FREQUENCY");
                               System.out.println(FREQUENCYOFNEW); 
                            }
                            rs17.close(); 
                            TF =(FREQUENCYOFNEW/FREQUENCY);
                            System.out.println("TF ="+TF);
                            double IDF1=(double)TOTALNOOFFILES/NOOFFILES;
                            IDF = Math.log(IDF1);
                            System.out.println("IDF ="+IDF);
                            TFIDF = TF*IDF;
                            System.out.println("TFIDF ="+TFIDF);
                             //insertIntoProduct="update query";
                            //ResultSet rs18 = db.runSql(insertIntoProduct);
                            // while (rs18.next())
                             {}
                        } 
                        rs16.close();

}
public void synonyms()  throws SQLException, ClassNotFoundException, InterruptedException
{
  //remove synonames
     String insertIntoProduct=null;                         
     insertIntoProduct="SELECT * FROM ENTERTAINMENT";
                        ResultSet rs15 = db.runSql(insertIntoProduct);
                         while (rs15.next()) {
                             
                         String WORD =rs15.getString("WORD");
                         int FREQUENCY = rs15.getInt("FREQUENCY");
                         int NOOFFILES = rs15.getInt("NOOFFILES");
                         WordNetDatabase database = WordNetDatabase.getFileInstance();
			 Synset[] synsets = database.getSynsets(WORD);
                         if (synsets.length > 0)
                            {
                                for (int i = 0; i < synsets.length; i++)
				{
					//System.out.println("");
					String[] wordForms = synsets[i].getWordForms();
					for (int j = 0; j < wordForms.length; j++)
					{
                                             insertIntoProduct="SELECT word,frequency FROM ENTERTAINMENT where word <> '"+WORD+"'";
                                             ResultSet rs16 = db.runSql(insertIntoProduct);
                                               while (rs16.next()) {
                                               String WORD1 =rs16.getString("WORD");
                                               if( wordForms[j].compareTo(WORD1)==0){
                                               System.out.println(WORD+" 9900 "+WORD1);
                                               }}
                                        }
                                }
                            }
                         System.out.print(WORD+"  ");
                         System.out.println(FREQUENCY);
                         }



}
public void initialise() throws SQLException
{
String insertIntoProduct=null;    
insertIntoProduct="TRUNCATE TABLE SAMPLE";
       ResultSet rs2 = db.runSql(insertIntoProduct);
       rs2.close();
       insertIntoProduct="TRUNCATE TABLE SAMPLE1";
       ResultSet rs3 = db.runSql(insertIntoProduct);
       rs3.close();
       insertIntoProduct="TRUNCATE TABLE DEMO";
       ResultSet rs10 = db.runSql(insertIntoProduct);
       rs10.close();
       insertIntoProduct="TRUNCATE TABLE DEMO1";
       ResultSet rs11 = db.runSql(insertIntoProduct);
       rs11.close();
}
   public void main1() throws SQLException, ClassNotFoundException, InterruptedException
   {
       
       int TOTALNOOFFILES=0;
       String a = ""; 
       String insertIntoProduct= null;
       initialise();
       System.setProperty("wordnet.database.dir", "C:\\Program Files (x86)\\WordNet\\2.1\\dict");
      char[] w = new char[501];int q = 0;
      Stemmer s = new Stemmer();
      //for (int i = 0; i < args.length; i++)
      try
      {
          String file="C:\\Users\\AvinashKumarPrajapati\\Desktop\\polotical1.txt";
          //FileInputStream FIN = new FileInputStream("C:\\Users\\AvinashKumarPrajapati\\Desktop\\123.txt");
          
          FileInputStream in = new FileInputStream("C:\\Users\\AvinashKumarPrajapati\\Desktop\\bc.txt");
          FileOutputStream fout=new  FileOutputStream("C:\\Users\\AvinashKumarPrajapati\\Desktop\\abc.txt");   
         
         try
         { 
         BufferedReader br = new BufferedReader(new FileReader(file));
        String line;
        while ((line = br.readLine()) != null) {
       // process the line.
            System.out.println(line);
          TOTALNOOFFILES=Integer.parseInt(line);
        }
        br.close();
        
         while(true)
              
           {  int ch = in.read();
              if (Character.isLetter((char) ch))
              {
                 for(int c=0;c<500;c++)w[c]='\0';
                 int j = 0;
                       
                 while(true)
                 {  ch = Character.toLowerCase((char) ch);
                    w[j] = (char) ch;
                    if ((j < 500)&&(Character.isLetter(w[j]))) j++;
                    ch = in.read();
                    if (!Character.isLetter((char) ch))
                    {
                       /* to test add(char ch) */
                        String x = ""; 
                       for (int c = 0; c < j; c++){x=x+w[c];s.add(w[c]);}
                        //System.out.println(x);
                       /* or, to test add(char[] w, int j) */
                       /* s.add(w, j); */
                        String wordForm = x;
			
                        //  Get the synsets containing the wrod form
			WordNetDatabase database = WordNetDatabase.getFileInstance();
			Synset[] synsets = database.getSynsets(wordForm);
                        s.stem();
                     //System.out.println(s.toString());
                       
                       {  
                           String u= null;
                           
                          /* and now, to test toString() : */
                          u = s.toString();
                            
                          /* to test getResultBuffer(), getResultLength() : */
                          /* u = new String(s.getResultBuffer(), 0, s.getResultLength()); */
                          if(synsets.length > 0)
                          {a =a+" "+u;
                         // insertIntoProduct="INSERT INTO ENTERTAINMENT VALUES ('','"+u+"',1,1,0,0)";
                           insertIntoProduct="INSERT INTO SAMPLE VALUES ('"+u+"',1,1)";
                          }  
                          else
                          {a =a+" "+x;
                          //insertIntoProduct="INSERT INTO ENTERTAINMENT VALUES ('','"+x+"',1,1,0,0)";
                          insertIntoProduct="INSERT INTO SAMPLE VALUES ('"+x+"',1,1)";
                          }   
                         q++;
                          ResultSet rs = db.runSql(insertIntoProduct);
                            rs.close();
                            //stmt.executeUpdate(insertIntoProduct);     
                       }
                       
                       break;
                       //Files.write(Paths.get("C:\\Users\\AvinashKumarPrajapati\\Desktop\\abc.txt"), a.getBytes());
                    }
                                               
                 }
                 
              }
               
              if (ch < 0) break;
              //System.out.print((char)ch);// ch contain character              
           }
                        System.out.println(TOTALNOOFFILES);
                         //db 
                        intermediate();
                        /*
                        //count increase;
                        PrintWriter writer = new PrintWriter(file);
                        writer.print("");
                        writer.close();
                        FileOutputStream FOUT=new  FileOutputStream(file);  
                       // TOTALNOOFFILES++;
                        System.out.println(TOTALNOOFFILES);    
                        String s2=Integer.toString(TOTALNOOFFILES);
                        byte[] contentInBytes = s2.getBytes();
                        FOUT.write(contentInBytes);
                        FOUT.close();
                        
                        
                        int F1=0;
                        insertIntoProduct="select count(word) as frequency from sample1";
                        ResultSet rs18 = db.runSql(insertIntoProduct);
                         while (rs18.next()) {
                         F1 = rs18.getInt("frequency");
                         System.out.println();
                         System.out.println(F1);
                         }
                         rs18.close();
                         
                        int TOTALFREQUENCY=0;
                        insertIntoProduct="select count(word) as words,sum(frequency) as totalfrequency from POLOTICAL";
                        ResultSet rs13 = db.runSql(insertIntoProduct);
                         while (rs13.next()) {
                         TOTALFREQUENCY = rs13.getInt("TOTALFREQUENCY");
                         int words = rs13.getInt("words");
                         System.out.println();
                         System.out.println(TOTALFREQUENCY+" "+words);
                         }
                         rs13.close();
                        // synonyms();
                         algorithm(TOTALNOOFFILES);
                     */  
                      System.out.println(a);        
                        byte b[]=a.getBytes();//converting string into byte array  
                        fout.write(b);  
                        fout.close();
         }
         catch (IOException e)
         {  //System.out.println("error reading " + args[i]);
            //break;
         }
      }
      catch (FileNotFoundException e)
      {  //System.out.println("file " + args[i] + " not found");
         //break;
      }
   }
   
}

public static OracleJDBC db ;

public static void main(String[] args) throws IOException,
    ClassNotFoundException,
    Exception {

    //for(int a=770;a<=799;a++)
    {
//till 550,(800-1982) in 1 2 ------1887
//no file 1-3,20,264,1977,1973,1961,1957,1904,1872,1860,1854,1858,1844,1755,1766,1782,1725,1733,1738,1760,1578
//no file 1536,1542,1456,1466,1482,1494,1112,1177,1184,1299,1318,1323,1347,1358,1372,1383,1393,1433,1434,664        
//no file 735,745
//heap//350,1838,1702,1644,383,514,820,857,925,618,985,1051,769
    //Thread.sleep(2000);
         db = new OracleJDBC();
 //   System.out.println("                 "+a+"   ");
    FileOutputStream fout=new  FileOutputStream("C:\\Users\\AvinashKumarPrajapati\\Desktop\\bc.txt");
    File file = new File("C:\\Users\\AvinashKumarPrajapati\\Desktop\\pol.txt");
    FileInputStream fis = new FileInputStream(file);
    byte[] data = new byte[(int) file.length()];
    fis.read(data);
    fis.close();
    MaxentTagger tagger = new MaxentTagger("taggers/wsj-0-18-bidirectional-nodistsim.tagger");
    String s=new String(data, "UTF-8");
    String sample = s.replaceAll("\\W"," ");


    String tagged = tagger.tagTokenizedString(sample);

    String[] x = tagged.split(" ");
    ArrayList<String> list = new ArrayList<String>();  

    for(int i=0;i<x.length;i++)
    {
        if (x[i].substring(x[i].lastIndexOf("_")+1).startsWith("N"))
        {
            list.add(x[i].split("_")[0]);
        }
    }
    String bit = "";
    for(int i=0;i<list.size();i++)
    {    
         bit+=list.get(i)+"\r\n ";
        System.out.println(list.get(i));       
    }
        byte b[]=bit.getBytes();//converting string into byte array            
        fout.write(b);
        fout.close();
        stanford stan = new stanford();
        stanford.Stemmer stem = stan.new Stemmer();
        stem.main1();

    try {
        db.finalize();
    } catch (Throwable ex){
    }
}
}}
