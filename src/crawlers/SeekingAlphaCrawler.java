package crawlers;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementNotVisibleException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxBinary;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxProfile;

/*
 * This crawler crawls all history news from seekingalpha.com.
 * The method is:
 * 1. Open seekingalpha.com;
 * 2. Click drop down button "LATEST", click "Other Date" and the page will show a calendar menu;
 * 3. Choose one certain date in calendar menu;
 * 4. Record the news on that day.
 */
public class SeekingAlphaCrawler {
  // Crawl control
  public static String StartDate = "2012-9-1";
  public static String EndDate = "2016-10-31";
  public static String[] Months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
  public static String SeekingAlphaUrl = "http://seekingalpha.com/market-news/all";

  // Files that save the crawled data
  public static String File_CrawlHistory = "./datas/1-Crawl-History";
  public static String File_EmptyDates = "./datas/1-Empty-Dates";
  public static String File_Prefix_DailyNews = "./datas/1-ALL-News-Daily-";
  
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
      fin = new File(File_EmptyDates);
      if (fin.exists() == false) return;
      reader = new BufferedReader(new InputStreamReader(new FileInputStream(fin), "UTF-8"));
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
    File pathToBinary = new File(CrawlerConstants.Firefox_Exe_Location);
    FirefoxBinary firefoxBinary = new FirefoxBinary(pathToBinary);
    FirefoxProfile firefoxProfile = new FirefoxProfile();
    webDriver = new FirefoxDriver(firefoxBinary, firefoxProfile);
    webDriver.manage().timeouts().implicitlyWait(60, TimeUnit.SECONDS);
    //webDriver.manage().timeouts().pageLoadTimeout(60, TimeUnit.SECONDS);
    //webDriver.manage().timeouts().setScriptTimeout(60, TimeUnit.SECONDS);
    webDriver.get("http://www.ifeng.com/");
    String firstWindowHandler = webDriver.getWindowHandle();
    
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
      String date = currentDate.get(Calendar.YEAR) + "-" + (currentDate.get(Calendar.MONTH) + 1) +
          "-" + currentDate.get(Calendar.DAY_OF_MONTH);
      if (crawled_history.contains(date)) {
        currentDate.add(Calendar.DATE, -1);
        continue;
      }
      long startMili = System.currentTimeMillis();
      webDriver.get(SeekingAlphaUrl);
      crawlOneDate(currentDate);
      currentDate.add(Calendar.DATE, -1);
      for (String handle : webDriver.getWindowHandles())
        if ( handle.equals(firstWindowHandler) == false ) {
          webDriver.switchTo().window(handle);
          webDriver.close();
        }
      long finishMili = System.currentTimeMillis();
      System.out.println("Time consumed : " + ((finishMili - startMili) / 1000.0 / 60.0) + " minutes.");
    }
    webDriver.quit();
  }
  
  
  public void crawlOneDate(Calendar targetDate) {
    System.out.println("Begin crawl news of date: " + targetDate.getTime());
    try {
      //------------------click and show calendar box------------------------
      //WebElement mc_right_menu = webDriver.findElement(By.id("mc_right_menu"));
      WebElement mc_right_menu = webDriver.findElement(
          By.xpath("html/body/div[1]/div[2]/div/div[1]/div[1]/div[@id=\"mc_right_menu\"]"));
      mc_right_menu.click();
      
      //WebElement news_data_selector = webDriver.findElement(By.className("news_date_selector"));
      WebElement news_data_selector = mc_right_menu.findElement(By.className("news_date_selector"));
      news_data_selector.click();
      
      WebElement market_calendar = mc_right_menu.findElement(By.id("market_calendar_bottom"));
      //------------------click and show calendar box------------------------
      
      //------------------choose target date in calendar box-----------------
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
        if (day.getText().equals("" + targetDate.get(Calendar.DAY_OF_MONTH)))
          day_of_month = day;
      }
      assert(day_of_month != null);
      day_of_month.click();
      //------------------choose target date in calendar box-----------------
      
      // TODO refreshed page will not appear immediately after click, thread sleep is not a good idea
      //      because it may fail for some cases.
      try {
        Thread.sleep(10000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      
      try {
      	for (int i = 0; i < 5; ++i) {
          //Scroll down to see more news.
          JavascriptExecutor jse = (JavascriptExecutor) webDriver;
          jse.executeScript("window.scrollTo(0,document.body.scrollHeight);");
        	Thread.sleep(1500);
      	}
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      
      //WebElement market_currents_list = webDriver.findElement(By.className("market_currents_list"));
      WebElement market_currents_list = webDriver.findElement(By.xpath("html/body/div[1]/div[2]/div/div[1]/ul"));
      List<WebElement> empty_page_messages = market_currents_list.findElements(By.className("empty_page_messages"));
      if (empty_page_messages.size() > 0) {
        writeEmptyDate(targetDate);
        return;
      }
      WebElement h4 = market_currents_list.findElement(By.tagName("h4"));
      // check whether loaded
      System.out.println(h4.getText());
      if (h4.getText().indexOf(
          targetDate.get(Calendar.DAY_OF_MONTH) + ", " + targetDate.get(Calendar.YEAR)) < 0)
        return;
      List<WebElement> mc_list_lis = market_currents_list.findElements(By.className("mc_list_li"));
      
      writeCrawledDataToFile(mc_list_lis, targetDate);
      
    } catch (NoSuchElementException e) {
      e.printStackTrace();
    } catch (AssertionError e) {
      e.printStackTrace();
    } catch (ElementNotVisibleException e) {
      e.printStackTrace();
    } catch (StaleElementReferenceException e) {
      e.printStackTrace();
    }
  }
  
  // There are days with no posts. Record them in file 
  public void writeEmptyDate(Calendar targetDate) {
    String date = targetDate.get(Calendar.YEAR) + "-" + (targetDate.get(Calendar.MONTH) + 1) +
        "-" + targetDate.get(Calendar.DAY_OF_MONTH);
    try {
      File fout = new File(File_EmptyDates);
      BufferedWriter writer = new BufferedWriter(
               new OutputStreamWriter(new FileOutputStream(fout, true), "UTF-8"));
      writer.write(date + "\n");
      crawled_history.add(date);
      writer.flush();
      writer.close();
     } catch ( IOException e ) {
      e.printStackTrace();
     }
  }
  public void writeCrawledDataToFile(List<WebElement> mc_lists_lis, Calendar targetDate) {
  	System.out.println("News List : " + mc_lists_lis.size());
//  	int loop = 0;
//  	for (WebElement mc_list_li : mc_lists_lis) {
//  		System.out.println("Loop : " + loop);
//  		loop++;
//  		String line = "";
//  		try {
//  			WebElement mc_list_tickers = mc_list_li.findElement(By.className("mc_list_tickers"));
//  			line = mc_list_tickers.getText().trim();
//  			List<WebElement> titles = mc_list_li.findElements(By.className("mc_bullets_title"));
//  			if (titles.size() == 0) {
//  				line = line + "\t";
//  			} else {
//  				line = line + "\t" + titles.get(0).getText().trim();
//  			}
//  			List<WebElement> contents = mc_list_li.findElements(By.className("general_summary"));
//  			if (contents.size() == 0) continue;
//  				line = line + "\t" + contents.get(0).getText().trim();
//  		} catch (StaleElementReferenceException e) {
//  			continue;
//  		}
//  		line = line.replaceAll("\n", "\\n");
//  		output = output + line + "\n";
//  		System.out.println(line);
//  	}
  	
  	Document document = Jsoup.parse(webDriver.getPageSource());
  	List<String> news =  parseNewsList(document);
    // write news
    String date = targetDate.get(Calendar.YEAR) + "-" + (targetDate.get(Calendar.MONTH) + 1) +
        "-" + targetDate.get(Calendar.DAY_OF_MONTH);
    try {
      File fout = new File(File_Prefix_DailyNews + date);
      BufferedWriter writer = new BufferedWriter(
               new OutputStreamWriter(new FileOutputStream(fout, true), "UTF-8"));
      for (String line : news) {
      	writer.write(line);
      	writer.write("\n");
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
  
  public List<String> parseNewsList(Document document) {
  	List<String> ret = new ArrayList<String>();
  	Elements mc_list_lis = document.body().getElementsByClass("mc_list_li");
  	System.out.println("Size of news : " + mc_list_lis.size());
  	for (int i = 0; i < mc_list_lis.size(); ++i) {
  		Element mc_list_li = mc_list_lis.get(i);
  		Elements mc_list_trickers = mc_list_li.getElementsByClass("mc_list_tickers");
  		Elements titles = mc_list_li.getElementsByClass("mc_bullets_title");
  		Elements contents = mc_list_li.getElementsByClass("general_summary");
  		if (mc_list_trickers.size() <= 0 || contents.size() == 0) continue;
  		String stockSymble = mc_list_trickers.get(0).text();
  		stockSymble = stockSymble.replaceAll(" ", " ").trim();
  		if (stockSymble.equals("")) continue;
  		StringBuilder news = new StringBuilder();
  		news.append(stockSymble);
  		if (titles.size() == 0) {
  			news.append("\t");
  		} else {
  			news.append("\t");
  			news.append(titles.get(0).text().trim());
  		}
  		news.append("\t");
  		news.append(contents.get(0).text().trim());
  		String line = news.toString().replaceAll("\n", "\\n");
  		ret.add(line);
  	}
  	System.out.println("Size of news with Stock Symble : " + ret.size());
  	return ret;
  }

  public static void main(String[] args) {
    SeekingAlphaCrawler crawler = new SeekingAlphaCrawler();
    crawler.start();
  }

}
