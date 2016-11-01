package crawlers;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.openqa.selenium.By;
import org.openqa.selenium.ElementNotVisibleException;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxBinary;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxProfile;

public class SeekingAlphaCrawler {
  // Crawl control
  public static String StartDate = "2016-9-1";
  public static String EndDate = "2016-10-31";
  public static String[] Months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
  public static String SeekingAlphaUrl = "http://seekingalpha.com/market-news/top-news";
  // location of firefox 46.0 release
  public static String Firefox_Exe_Location = "D:\\Program Files\\firefox-sdk\\bin\\firefox.exe";

  // Files that save the crawled data
  public static String File_CrawlHistory = "./datas/Crawl-History";
  public static String File_Prefix_DailyNews = "./datas/News-Daily-";
  
  public WebDriver webDriver;
  public HashMap<String, Integer> monthToId;
  public HashSet<String> crawled_history; // dates that already be crawled
  
  public SeekingAlphaCrawler() {
    monthToId = new HashMap<String, Integer>();
    for (int i = 0; i < Months.length; ++i)
      monthToId.put(Months[i], i);
    crawled_history = new HashSet<String>();
    try {
      File fin = new File(File_CrawlHistory);
      if (fin.exists() == false) return;
      BufferedReader reader = new BufferedReader(
          new InputStreamReader(new FileInputStream(fin), "UTF-8"));
      while (true) {
        String line = reader.readLine();
        if (line == null) break;
        crawled_history.add(line);
      }
      reader.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
  
  public void start() {
    File pathToBinary = new File(Firefox_Exe_Location);
    FirefoxBinary ffBinary = new FirefoxBinary(pathToBinary);
    FirefoxProfile firefoxProfile = new FirefoxProfile();       
    webDriver = new FirefoxDriver( ffBinary, firefoxProfile );
    webDriver.manage().timeouts().implicitlyWait(60, TimeUnit.SECONDS);
    //webDriver.manage().timeouts().pageLoadTimeout(60, TimeUnit.SECONDS);
    //webDriver.manage().timeouts().setScriptTimeout(60, TimeUnit.SECONDS);
    System.out.println(1);
    webDriver.get("http://www.ifeng.com/");
    String firstWindowHandler = webDriver.getWindowHandle();
    System.out.println(2);
    
    Calendar currentDate = Calendar.getInstance();
    Calendar startDate = Calendar.getInstance();
    String[] time_terms = EndDate.split("-");
    currentDate.set(Integer.parseInt(time_terms[0]),
                    Integer.parseInt(time_terms[1]) - 1,
                    Integer.parseInt(time_terms[2]));
    time_terms = StartDate.split("-");
    startDate.set(Integer.parseInt(time_terms[0]),
                  Integer.parseInt(time_terms[1]) - 1,
                  Integer.parseInt(time_terms[2]));

    while (currentDate.compareTo(startDate) > 0) {
      String date = currentDate.get(Calendar.YEAR) + "-" + currentDate.get(Calendar.MONTH) +
          "-" + currentDate.get(Calendar.DAY_OF_MONTH);
      if (crawled_history.contains(date)) {
        currentDate.add(Calendar.DATE, -1);
        continue;
      }
      webDriver.get(SeekingAlphaUrl);
      crawlOneDate(currentDate);
      currentDate.add(Calendar.DATE, -1);
      for (String handle : webDriver.getWindowHandles())
        if ( handle.equals(firstWindowHandler) == false ) {
          webDriver.switchTo().window(handle);
          webDriver.close();
        }
    }
    webDriver.quit();
  }
  
  
  public void crawlOneDate(Calendar targetDate) {
    System.out.println("crawling date : " + targetDate.getTime());
    try {
      WebElement mc_right_menu = webDriver.findElement(By.id("mc_right_menu"));
      mc_right_menu.click();
      
      WebElement news_data_selector = webDriver.findElement(By.className("news_date_selector"));
      news_data_selector.click();
      
      WebElement market_calendar = webDriver.findElement(By.id("market_calendar_bottom"));
      
      WebElement current_month = null;
      WebElement previous_month = null;
      Calendar current_date = Calendar.getInstance();
      while (true) {
        current_month = market_calendar.findElement(By.className("current_month"));
        String[] month_year = current_month.getText().split(" ");
        assert(month_year.length >= 2);
        current_date.set(Integer.parseInt(month_year[1]),
                         monthToId.get(month_year[0]),
                         1);
        if (current_date.get(Calendar.YEAR) <= targetDate.get(Calendar.YEAR) &&
            current_date.get(Calendar.MONTH) <= targetDate.get(Calendar.MONTH))
          break;
        previous_month = market_calendar.findElement(By.className("previous_month"));
        previous_month.click();
      }
      WebElement market_calendar_bottom = market_calendar.findElement(By.id("market_calendar_bottom"));
      List<WebElement> days_of_month = market_calendar_bottom.findElements(By.className("day"));
      WebElement day_of_month = null;
      for (WebElement day : days_of_month) {
        System.out.println(day.getText());
        if (day.getText().equals("" + targetDate.get(Calendar.DAY_OF_MONTH)))
          day_of_month = day;
      }
      assert(day_of_month != null);
      day_of_month.click();
      
      // TODO refreshed page will not appear immediately after click, thread sleep is not a good idea
      //      because it may fail for some cases.
      try {
        Thread.sleep(10000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      
      WebElement market_currents_list = webDriver.findElement(By.className("market_currents_list"));
      WebElement h4 = market_currents_list.findElement(By.tagName("h4"));
      // check whether loaded
      System.out.println(h4.getText());
      if (h4.getText().indexOf(
          targetDate.get(Calendar.DAY_OF_MONTH) + ", " + targetDate.get(Calendar.YEAR)) < 0)
        return;
      List<WebElement> mc_list_lis = webDriver.findElements(By.className("mc_list_li"));
      
      writeCrawledDataToFile(mc_list_lis, targetDate);
      
    } catch (NoSuchElementException e) {
      e.printStackTrace();
    } catch (AssertionError e) {
      e.printStackTrace();
    } catch (ElementNotVisibleException e) {
      e.printStackTrace();
    }
  }
  
  public void writeCrawledDataToFile(List<WebElement> mc_lists_lis, Calendar targetDate) {
    // write news
    String date = targetDate.get(Calendar.YEAR) + "-" + targetDate.get(Calendar.MONTH) +
        "-" + targetDate.get(Calendar.DAY_OF_MONTH);
    try {
      File fout = new File(File_Prefix_DailyNews + date);
      BufferedWriter writer = new BufferedWriter(
               new OutputStreamWriter(new FileOutputStream(fout, true), "UTF-8"));
      for (WebElement mc_list_li : mc_lists_lis) {
        WebElement mc_list_tickers = mc_list_li.findElement(By.className("mc_list_tickers"));
        //WebElement title = mc_list_li.findElement(By.className("mc_bullets_title mc_summaries_title_link"));
        //WebElement content = mc_list_li.findElement(By.className("general_summary light_text bullets"));
        WebElement title = mc_list_li.findElement(By.className("mc_bullets_title"));
        WebElement content = mc_list_li.findElement(By.className("general_summary"));
        if (mc_list_li == null || title == null || content == null) continue;
        String line = mc_list_tickers.getText().trim() + "\t" + title.getText().trim() + "\t" + 
            content.getText().trim();
        line = line.replaceAll("\n", "\\n");
        writer.write(line + "\n");
      }
      writer.flush();
      writer.close();
     } catch ( IOException e ) {
      e.printStackTrace();
     }
    
    // write crawl history
    try {
      File fout = new File(File_CrawlHistory);
      BufferedWriter writer = 
          new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fout, true), "UTF-8"));
      writer.write(date + "\n");
      crawled_history.add(date);
      writer.flush();
      writer.close();
    } catch ( IOException e ) {
      e.printStackTrace();
    }
  }

  public static void main(String[] args) {
    SeekingAlphaCrawler crawler = new SeekingAlphaCrawler();
    crawler.start();
  }

}
