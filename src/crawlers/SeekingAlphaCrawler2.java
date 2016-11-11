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
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxBinary;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxProfile;


/*
 * This crawler crawls history news of certain stocks from seekingalpha.com.
 * The method is:
 * 1. open seekingalpha.com/symbol/[Symbol]/news
 * 2. scroll down window until find all news in search date range
 * 3. record the news
 * 4. open http://seekingalpha.com/symbol/[Symbol]/key-data/historical-quotes;
 * 5. enter search date range in query box and click "Apply" button
 * 6. record the prices
 */
public class SeekingAlphaCrawler2 {
	public static String File_Stock_Symbles = "./datas/2-Stock-Symbles";
	public static String File_Prefix_Stocks = "./datas/2-News-Stream-of-";
	public static String File_Prefix_Stock_Price = "./datas/2-Price-Stream-of-";
	public static String File_Stock_Symbles_History = "./datas/2-Crawl-History";
  public static String StartDate = "2012-9-1";
  public static String EndDate = "2016-10-31";
  public static String[] Months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
	
	public WebDriver webDriver;
  public HashMap<String, Integer> monthToId;
  public HashSet<String> crawledHistory;
  
  public SeekingAlphaCrawler2() {
    monthToId = new HashMap<String, Integer>();
    for (int i = 0; i < Months.length; ++i)
      monthToId.put(Months[i], i);
    crawledHistory = new HashSet<String>();
    try {
      File fin = new File(File_Stock_Symbles_History);
      if (fin.exists() == false) return;
      BufferedReader reader = new BufferedReader(
          new InputStreamReader(new FileInputStream(fin), "UTF-8"));
      while (true) {
        String line = reader.readLine();
        if (line == null) break;
        crawledHistory.add(line);
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
    try {
      File fin = new File(File_Stock_Symbles);
      BufferedReader reader = new BufferedReader(
          new InputStreamReader(new FileInputStream(fin), "UTF-8"));
      while (true) {
        String line = reader.readLine();
        if (line == null) break;
        if (crawledHistory.contains(line)) continue;
        
        System.out.println(line);
        
        crawlNewsOfOneStock(line);
        
        crawlPriceOfOneStock(line);
        
        for (String handle : webDriver.getWindowHandles())
          if ( handle.equals(firstWindowHandler) == false ) {
            webDriver.switchTo().window(handle);
            webDriver.close();
          }
      }
      reader.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    
    webDriver.quit();
	}
	
	public void crawlNewsOfOneStock(String symble) {
		String url = "http://seekingalpha.com/symbol/" + symble + "/news";
		webDriver.get(url);
		
		String pre_time = "Now";
    Calendar endDate = Calendar.getInstance();
    Calendar startDate = Calendar.getInstance();
    String[] time_terms = EndDate.split("-");
    endDate.set(Integer.parseInt(time_terms[0]),
                    Integer.parseInt(time_terms[1]) - 1,
                    Integer.parseInt(time_terms[2]));
    endDate.set(Calendar.HOUR_OF_DAY, 23);
    endDate.set(Calendar.MINUTE, 59);
    time_terms = StartDate.split("-");
    startDate.set(Integer.parseInt(time_terms[0]),
                  Integer.parseInt(time_terms[1]) - 1,
                  Integer.parseInt(time_terms[2]));
    startDate.set(Calendar.HOUR_OF_DAY, 0);
    startDate.set(Calendar.MINUTE, 0);
    
    Random rnd = new Random(System.currentTimeMillis());
    while (true) {
    	List<WebElement> mc_list_lis = webDriver.findElements(By.className("mc_list_li"));
    	assert mc_list_lis.size() > 0;
    	WebElement last_mc_list_li = mc_list_lis.get(mc_list_lis.size() - 1);
    	WebElement div_date = last_mc_list_li.findElement(By.className("pad_on_summaries"));
    	String str_date = div_date.getText(); // "Wed, Nov. 2, 12:04 PM" or "Oct. 24, 2015, 3:16 PM"
    	if (pre_time.equals(str_date)) break;
    	pre_time = str_date;
    	
    	// parse str_date to Calendar
    	System.out.println(str_date);
    	Calendar currentDate = parseDateStringOfNewsToCalendar(str_date);
    	System.out.println("" + currentDate.get(Calendar.YEAR) + "-" + (currentDate.get(Calendar.MONTH) + 1)
    			+ "-" + currentDate.get(Calendar.DAY_OF_MONTH));
    	if (currentDate.compareTo(startDate) < 0) break;
    	
      //Scroll down to see more news.
    	for (int i = 0; i < 3; ++i) {
        JavascriptExecutor jse = (JavascriptExecutor) webDriver;
        jse.executeScript("window.scrollTo(0,document.body.scrollHeight);"); 
        try {
          Thread.sleep(1500 + rnd.nextInt(1000));
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
    	}
    }
    
    // write crawled news to file.
    List<WebElement> mc_list_lis = webDriver.findElements(By.className("mc_list_li"));
    System.out.println("Size of news list : " + mc_list_lis.size());
  	Document document = Jsoup.parse(webDriver.getPageSource());
  	List<String> news =  parseNewsList(document, symble, startDate, endDate);
    try {
      File fout = new File(File_Prefix_Stocks + symble);
      BufferedWriter writer = new BufferedWriter(
               new OutputStreamWriter(new FileOutputStream(fout, true), "UTF-8"));
      for (String line : news) {
      	writer.write(line);
      	writer.write("\n");
      }
      writer.flush();
      writer.close();
     } catch (IOException e) {
      e.printStackTrace();
     }
	}
	
	// parse the date of news shown in page into calendar,
	// date of news is in the form of "Wed, Nov. 2, 12:04 PM" or "Oct. 24, 2015, 3:16 PM".
	public Calendar parseDateStringOfNewsToCalendar(String date) {
		int this_year = Calendar.getInstance().get(Calendar.YEAR);
		Calendar calendar = Calendar.getInstance();
		if (date.indexOf("Today") != -1) return calendar;
		if (date.indexOf("Yesterday") != -1) {
			calendar.add(Calendar.DATE, -1);
			return calendar;
		}
		try {
		String[] terms = date.split(",");
		assert terms.length == 3;
		char ch = terms[1].trim().charAt(0);
		if (ch >= '0' && ch <= '9') { //"Oct. 24, 2015, 3:16 PM".
			calendar.set(Calendar.YEAR, Integer.parseInt(terms[1].trim()));
			String[] month_date = terms[0].trim().split(" ");
			assert month_date.length == 2;
			calendar.set(Calendar.MONTH, monthToId.get(month_date[0].replaceAll("\\.", "").trim()));
			calendar.set(Calendar.DAY_OF_MONTH, Integer.parseInt(month_date[1].trim()));
		} else { // "Wed, Nov. 2, 12:04 PM"
			calendar.set(Calendar.YEAR, this_year);
			String[] month_date = terms[1].trim().split(" ");
			assert month_date.length == 2;
			calendar.set(Calendar.MONTH, monthToId.get(month_date[0].replaceAll("\\.", "").trim()));
			calendar.set(Calendar.DAY_OF_MONTH, Integer.parseInt(month_date[1].trim()));
		}
		} catch (AssertionError e) {
			System.out.println(date);
			e.printStackTrace();
			return null;
		}
		return calendar;
	}
	
  public List<String> parseNewsList(Document document, String symble, Calendar startDate,
  		Calendar endDate) {
  	List<String> ret = new ArrayList<String>();
  	Elements mc_list_lis = document.body().getElementsByClass("mc_list_li");
  	for (int i = 0; i < mc_list_lis.size(); ++i) {
  		Element mc_list_li = mc_list_lis.get(i);
  		Elements titles = mc_list_li.getElementsByClass("mc_bullets_title");
  		Elements contents = mc_list_li.getElementsByClass("general_summary");
  		Elements times = mc_list_li.getElementsByClass("pad_on_summaries");
  		if (contents.size() == 0 || times.size() == 0) continue;
  		String stockSymble = symble;
  		StringBuilder news = new StringBuilder();
  		Calendar date = parseDateStringOfNewsToCalendar(times.get(0).text());
  	  if (date.compareTo(startDate) < 0 || date.compareTo(endDate) > 0)
  	  	continue;
    	news.append(date.get(Calendar.YEAR) + "-" + (date.get(Calendar.MONTH) + 1) + "-" 
    			+ date.get(Calendar.DAY_OF_MONTH));
    	news.append("\t");
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

  public void crawlPriceOfOneStock(String symbol) {
  	String url = "http://seekingalpha.com/symbol/" + symbol + "/key-data/historical-quotes";
  	webDriver.get(url);
  	
  	String[] time_terms = StartDate.split("-");
  	// enter the query date range in input box.
  	// in the format "Nov 10, 2015 - Nov 10, 2016"
  	String key_to_send = Months[Integer.parseInt(time_terms[1]) - 1] + " " + time_terms[2] + ", " + time_terms[0];
  	key_to_send += " - ";
  	time_terms = EndDate.split("-");
  	key_to_send += Months[Integer.parseInt(time_terms[1]) - 1] + " " + time_terms[2] + ", " + time_terms[0];
  	WebElement input_date_range = webDriver.findElement(By.id("date-range"));
  	input_date_range.sendKeys(Keys.chord(Keys.CONTROL, "a"), key_to_send);
  	
    try {
      Thread.sleep(5000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  	
  	WebElement button = webDriver.findElement(By.id("btnApply"));
  	button.click();
  	
    try {
      Thread.sleep(10000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    
    // record daily prices from quote table.
    Document document = Jsoup.parse(webDriver.getPageSource());
    Elements table = document.getElementsByClass("quotes-table");
    assert table.size() > 0;
    Elements tbody = table.get(0).getElementsByTag("tbody");
    assert tbody.size() > 0;
    Elements trows = tbody.get(0).getElementsByTag("tr");
    
    List<String> daily_prices = new ArrayList<String>();
    for (Element trow : trows) {
    	Elements tds = trow.getElementsByTag("td");
    	assert tds.size() == 7;
    	StringBuilder price = new StringBuilder();
    	// td[0] is date, in the format Nov 02, 2016"
    	String quote_date = tds.get(0).text().trim();
    	String[] date_terms = quote_date.split(",");
    	price.append(date_terms[1].trim());
    	String[] month_date = date_terms[0].trim().split(" ");
    	price.append("-");
    	price.append(monthToId.get(month_date[0]) + 1);
    	price.append("-");
    	price.append(Integer.parseInt(month_date[1]));	
    	// td[1..5] are open, high, low, close
    	for (int i = 1; i <= 4; ++i) {
    		price.append("\t");
    		price.append(tds.get(i).text().trim());
    	}
    	// td[5] is volume
    	price.append("\t");
    	price.append(tds.get(5).text().trim().replaceAll(",", ""));
    	// td[6] is Change %
    	price.append("\t");
    	price.append(tds.get(6).text().replaceAll("%", "").trim());
    	daily_prices.add(price.toString());
    }
    
    System.out.println("SeekingAlphaCrawler2::crawlPriceOfOneStock number of price days: " + daily_prices.size());
    
    // write prices into file.
    try {
      File fout = new File(File_Prefix_Stock_Price + symbol);
      BufferedWriter writer = new BufferedWriter(
               new OutputStreamWriter(new FileOutputStream(fout, true), "UTF-8"));
      for (String line : daily_prices) {
      	writer.write(line);
      	writer.write("\n");
      }
      writer.flush();
      writer.close();
     } catch (IOException e) {
      e.printStackTrace();
     }
    
    // write crawl history
    try {
      File fout = new File(File_Stock_Symbles_History);
      BufferedWriter writer = 
          new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fout, true), "UTF-8"));
      writer.write(symbol + "\n");
      crawledHistory.add(symbol);
      writer.flush();
      writer.close();
    } catch ( IOException e ) {
      e.printStackTrace();
    }
  }
  
	public static void main(String[] args) {
		SeekingAlphaCrawler2 crawler2 = new SeekingAlphaCrawler2();
		crawler2.start();
	}
}
