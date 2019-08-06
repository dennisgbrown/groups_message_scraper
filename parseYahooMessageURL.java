import java.util.*;
import java.io.*;
import java.net.URL;

import HTTPClient.*;

class parseYahooMessageURL
{

  public static void main(String[] args)
  {
    String partialUrlString = null;
    String startMessageString = null;
    String endMessageString = null;

    int startMessage = 0;
    int endMessage = 0;

    String groupname = null;      
    String groupwebname = null;
    String directory = null;

    boolean saveEndMessage = false;

    try
    {
      if (args.length < 4)
      {
        System.out.println("Need 4 arguments:");
        System.out.println(" - starting message number");
        System.out.println(" - ending message number");
        System.out.println(" - Yahoo group name");
        System.out.println(" - Yahoo group web id (as used in URL)");
        System.out.println(" - directory to put messages in (optional; defaults to group web id)");
      }

      startMessageString = args[0];
      endMessageString = args[1];

      startMessage = (Integer.valueOf(startMessageString)).intValue();
      endMessage = (Integer.valueOf(endMessageString)).intValue();

      groupname = args[2];      
      groupwebname = args[3];
      if (args.length > 4)
        directory = args[4];
      else
        directory = groupwebname;

      partialUrlString = "http://groups.yahoo.com/group/" + groupwebname + "/message/";

      if (startMessage == -1)
      {
        FileReader inReader = new FileReader(groupwebname + ".lastmsgrec");
        BufferedReader bInReader = new BufferedReader(inReader);
        String currLine = bInReader.readLine();
        startMessage = (Integer.valueOf(currLine)).intValue() + 1;
        bInReader.close();
        inReader.close();
        System.out.println("Starting at message " + startMessage);
      }

      if (endMessage == -1)
      {
        endMessage = 1000000;
        saveEndMessage = true;
      }
    }
    catch (Exception e)
    {
      e.printStackTrace();
      System.exit(1);
    }

    int numProblemMessages = 0;
    int lastSuccessfulMessage = -1;

    for (int currMessage = startMessage; currMessage <= endMessage; currMessage++)
    {
      BufferedReader data = null;
      FileOutputStream outFile = null;
      PrintStream outPrinter = null;
      String outputFilename = null;

      if (numProblemMessages >= 5)
      {
        System.out.println("Too many problem messages in a row... end of board?");
        break;
      }

      try
      {
        String urlString = partialUrlString + String.valueOf(currMessage);
        URL theURL = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection)theURL.openConnection();

        /*
        System.out.println("RESPONSE: " + conn.getResponseCode());
        System.out.println("HEADERS:");
        boolean go = true;
        int currHeaderIdx = 1;
        while (go)
        {
          String headerKey = conn.getHeaderFieldKey(currHeaderIdx);
          if (headerKey != null)
          {
            System.out.println(currHeaderIdx + ": " + headerKey + " = " + conn.getHeaderField(currHeaderIdx));
            currHeaderIdx++;
          }
          else go = false;
        }
        */

        conn.connect();
        System.out.print(currMessage + " Connection opened...");

        String messageNumberString = (new Integer(currMessage)).toString();
        String subject = null;
        String author = null;
        String dateWrongFormat = null;
        String messageBody = "";
        boolean inMessageBody = false;
        boolean stupidAdvertisement = false;

        data = new BufferedReader(new InputStreamReader(new BufferedInputStream(conn.getInputStream())));
        System.out.print(" Reading data... ");
        String currLine = data.readLine();

        // Skip header crap
        while ((currLine != null) &&
               (currLine.lastIndexOf("<!-- start content include -->") == -1))
        {
          //System.out.println("CURRLINE: " + currLine);
          currLine = data.readLine();
        }

        // Figure out what kind of reply this is: real message, advertisement, or "oops" reply
        while (currLine != null)
        {
          //System.out.println("CURRLINE: " + currLine);

          // If this is a real message, break out of this loop
          if (currLine.lastIndexOf("<!-- start of guts") != -1) break;

          // If this is an advertising message, skip it...
          if (currLine.lastIndexOf("is an advertising supported service") != -1)
          {
            System.out.println(" Got an advertising message; trying again on message " + currMessage);
            stupidAdvertisement = true;
            break;
          }

          // If this is an "oops" message, throw an exception like this was a non-existant URL.
          if (currLine.lastIndexOf("Oops..") != -1)
          {
            throw new Exception(" Got an OOPS! message ");
          }

          currLine = data.readLine();
        }

        // If this is a stupid advertisement, decrement currMessage (will be
        // re-incremented by the for loop code) and try again.
        if (stupidAdvertisement)
        {
          currMessage--;
          continue;
        }

        // If we get here, assume this is a real message
        while (currLine != null)
        {
          //System.out.println("CURRLINE: " + currLine);

          StringTokenizer st = null;

          // If this is the "from" line
          if ((currLine.lastIndexOf("<b>From:</b>") != -1) && (author == null))
          {
            // Read the next line and try to figure out the author.
            currLine = data.readLine();
            if (currLine != null)
            {
              st = new StringTokenizer(currLine, ">");
              st.nextToken();
              String restOfLine = st.nextToken();
              st = new StringTokenizer(restOfLine, "<");
              String authorPlusAt = st.nextToken();
              st = new StringTokenizer(authorPlusAt, "@");
              author = st.nextToken();
              //System.out.println(" author = " + author + " ");
            }
          }

          // If this is the "date" line
          else if ((currLine.lastIndexOf("<b>Date:</b>") != -1) && (dateWrongFormat == null))
          {
            st = new StringTokenizer(currLine, ">");
            st.nextToken();
            st.nextToken();
            st.nextToken();
            String restOfLine = st.nextToken();
            st = new StringTokenizer(restOfLine, "<");
            dateWrongFormat = st.nextToken();
            dateWrongFormat = replaceSubstring(dateWrongFormat, "&nbsp;", " ");
            dateWrongFormat = replaceSubstring(dateWrongFormat, ",", "");
            //System.out.println(" dateWrongFormat = " + dateWrongFormat + " ");
          }

          // If this is the "subject" line
          else if ((currLine.lastIndexOf("<b>Subject:</b>") != -1) && (subject == null))
          {
            st = new StringTokenizer(currLine, ">");
            st.nextToken();
            st.nextToken();
            st.nextToken();
            String restOfLine = st.nextToken();
            st = new StringTokenizer(restOfLine, "<");
            subject = st.nextToken();
            subject = replaceSubstring(subject, "&nbsp; ", "");
            subject = sanitizeString(subject);
            //System.out.println(" subject = " + subject + " ");
          }

          // If we're in the message body, copy lines...
          else if (inMessageBody)
          {
            // If this is the end of the message body, note it and continue
            if (currLine.lastIndexOf("</tt>") != -1)
            {
              inMessageBody = false;
            }
            else
            {
              messageBody += sanitizeString(currLine);
            }
          }

          // If this is the start of the message body, note it and continue
          else if ((currLine.lastIndexOf("<tt>") != -1) && (!inMessageBody))
          {
            inMessageBody = true;
          }

          currLine = data.readLine();
        } 

        System.out.println("Got message " + messageNumberString + ": " + subject);
        //System.out.println("messageNumberString = " + messageNumberString + " subject = " + subject + " author = " + author + " date = " + dateWrongFormat);
        //System.out.println("message body = " + messageBody);

        String messageNumberStringFormatted = null;
        int messageNumber = -1;
        messageNumber = (Integer.valueOf(messageNumberString)).intValue();
        if ((messageNumber >= 0) && (messageNumber < 10))
          messageNumberStringFormatted = "000000" + messageNumberString;
        else if ((messageNumber >= 10) && (messageNumber < 100))
          messageNumberStringFormatted = "00000" + messageNumberString;
        else if ((messageNumber >= 100) && (messageNumber < 1000))
          messageNumberStringFormatted = "0000" + messageNumberString;
        else if ((messageNumber >= 1000) && (messageNumber < 10000))
          messageNumberStringFormatted = "000" + messageNumberString;
        else if ((messageNumber >= 10000) && (messageNumber < 100000))
          messageNumberStringFormatted = "00" + messageNumberString;
        else if ((messageNumber >= 100000) && (messageNumber < 1000000))
          messageNumberStringFormatted = "0" + messageNumberString;
        else messageNumberStringFormatted = messageNumberString;

        outputFilename = directory + "\\" + groupwebname + "-" + messageNumberStringFormatted + ".txt";
        outFile = new FileOutputStream(outputFilename);
        outPrinter = new PrintStream(outFile);

        // Print out message header and body.

        Random RandomThingy = new Random();
        outPrinter.println("From: " + author + "_REMOVE_" + (RandomThingy.nextInt(899999) + 100000) + "_THIS_@yahoo.com");
        outPrinter.println("Date: " + convertDate(dateWrongFormat));
        outPrinter.println("Subject: " + subject + " [Yahoo! Groups: " + groupname + "]");
        outPrinter.println();
        outPrinter.println(messageBody);
        outPrinter.println();
        outPrinter.println("[This is message #" + messageNumberString + " by user " + author + " on Yahoo! Group " + groupname + ": http://groups.yahoo.com/group/" + groupwebname + " ]");
        outPrinter.println();

        outPrinter.close();
        outFile.close();
        data.close();
        outputFilename = null;

        numProblemMessages = 0;
        lastSuccessfulMessage = messageNumber;

        try
        {
          String outputFilename2 = groupwebname + ".lastmsgrec";
          FileOutputStream outFile2 = new FileOutputStream(outputFilename2);
          PrintStream outPrinter2 = new PrintStream(outFile2);
          outPrinter2.println(lastSuccessfulMessage);
          outPrinter2.close();
          outFile2.close();
        }
        catch (Exception e1234)
        {
          e1234.printStackTrace();
        }
      }
      catch (Exception e2)
      {
        System.out.println("PROBLEM! " + e2.getMessage() + "... Skipping message " + currMessage);

        numProblemMessages++;

        try
        {
          if (outPrinter != null) outPrinter.close();
          if (outFile != null) outFile.close();
          if (data != null) data.close();
          if (outputFilename != null)
          {          
            File killMe = new File(outputFilename);
            killMe.delete();
            outputFilename = null;
          }
        }
        catch (Exception e3)
        {
          e3.printStackTrace();
        }
      }
    }
  }

  static String replaceSubstring(String line, String oldSub, String newSub) throws Exception
  {    
    //System.out.println("replaceSubstring: " + line + "... " + oldSub + " " + newSub); 
    int oldSubLocation = line.lastIndexOf(oldSub);

    if (oldSubLocation == -1)
    {
      return line;
    }
    else
    {
      return replaceSubstring(line.substring(0, oldSubLocation) + newSub + line.substring(oldSubLocation + oldSub.length(), line.length()), oldSub, newSub);
    }
  }


  static String convertDate(String dateWrongFormat) throws Exception
  {
    StringTokenizer st = new StringTokenizer(dateWrongFormat, " ");

    String dayName = st.nextToken();    
    String monthName = st.nextToken();
    String day = st.nextToken();    
    String yearFourDigit = st.nextToken();
    String time = st.nextToken();

    return day + " " + monthName + " " + yearFourDigit + " " + time;
  }


  static String sanitizeString(String fixme) throws Exception
  {
    fixme = replaceSubstring(fixme, "&nbsp;", "");
    fixme = replaceSubstring(fixme, "<BR>", "\n");              
    fixme = replaceSubstring(fixme, "&gt;", ">");
    fixme = replaceSubstring(fixme, "&lt;", "<");
    fixme = replaceSubstring(fixme, "&amp;", "&");
    fixme = replaceSubstring(fixme, "&quot;", "\"");

    return fixme;
  }
}


