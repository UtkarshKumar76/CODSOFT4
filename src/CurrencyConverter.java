import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CurrencyConverter {

    private static final String LIVE_API = "https://api.frankfurter.app/latest?from=";
    private static final String HIST_API = "https://api.frankfurter.app/";
    private static final String HISTORY_FILE = "conversion_history.txt";
    private static final DecimalFormat DEC = new DecimalFormat("#,##0.00");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final LinkedHashMap<String, String> CURRENCIES = new LinkedHashMap<>();

    static {
        CURRENCIES.put("USD", "United States Dollar");
        CURRENCIES.put("EUR", "Euro");
        CURRENCIES.put("INR", "Indian Rupee");
        CURRENCIES.put("GBP", "British Pound");
        CURRENCIES.put("JPY", "Japanese Yen");
        CURRENCIES.put("AUD", "Australian Dollar");
        CURRENCIES.put("CAD", "Canadian Dollar");
        CURRENCIES.put("CHF", "Swiss Franc");
        CURRENCIES.put("SGD", "Singapore Dollar");
        CURRENCIES.put("CNY", "Chinese Yuan");
        CURRENCIES.put("HKD", "Hong Kong Dollar");
        CURRENCIES.put("NZD", "New Zealand Dollar");
    }

    private Scanner sc = new Scanner(System.in);

    // -------------------- Utility --------------------

    private String httpGet(String link) throws IOException {
        URL url = new URL(link);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);

        BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }

    private double extractRate(String json, String currency) {
        Pattern p = Pattern.compile("\"" + currency + "\":([0-9.]+)");
        Matcher m = p.matcher(json);
        if (m.find()) return Double.parseDouble(m.group(1));
        return -1;
    }

    private double liveRate(String base, String target) {
        try {
            String json = httpGet(LIVE_API + base + "&to=" + target);
            return extractRate(json, target);
        } catch (Exception e) {
            return -1;
        }
    }

    private double histRate(String base, String target, String date) {
        try {
            String link = HIST_API + date + "?from=" + base + "&to=" + target;
            String json = httpGet(link);
            return extractRate(json, target);
        } catch (Exception e) {
            return -1;
        }
    }

    private void printMenu() {
        System.out.println("\nAvailable Currencies:");
        int i = 1;
        for (var e : CURRENCIES.entrySet()) {
            System.out.printf("%2d. %-5s %-25s%n", i++, e.getKey(), e.getValue());
        }
    }

    private String readCode(String msg) {
        while (true) {
            System.out.print(msg);
            String c = sc.nextLine().trim().toUpperCase();
            if (CURRENCIES.containsKey(c)) return c;
            System.out.println("Invalid code. Example: USD, INR, EUR");
        }
    }

    private double readAmount(String msg) {
        while (true) {
            System.out.print(msg);
            try {
                double a = Double.parseDouble(sc.nextLine());
                if (a > 0) return a;
            } catch (Exception ignored) {}
            System.out.println("Enter a valid number greater than 0.");
        }
    }

    private void saveHistory(String line) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(HISTORY_FILE, true))) {
            pw.println(line);
        } catch (Exception ignore) {}
    }

    // -------------------- Historical Menu --------------------

    private void historicalSingle(String base, String target) {
        while (true) {
            System.out.print("\nEnter date (YYYY-MM-DD) or 'b' to go back: ");
            String date = sc.nextLine().trim();
            if (date.equalsIgnoreCase("b")) return;

            try {
                LocalDate.parse(date, DATE_FMT);
            } catch (Exception e) {
                System.out.println("Invalid date.");
                continue;
            }

            double rate = histRate(base, target, date);
            if (rate <= 0) {
                System.out.println("No historical data for this date.");
                continue;
            }

            System.out.printf("On %s → 1 %s = %s %s\n",
                    date, base, DEC.format(rate), target);

            double amount = readAmount("Enter amount to convert: ");
            double convert = amount * rate;

            System.out.printf("%s %s = %s %s\n",
                    DEC.format(amount), base, DEC.format(convert), target);

            saveHistory("HIST " + date + " | " + amount + " " + base +
                    " -> " + DEC.format(convert) + " " + target);
        }
    }

    private void last7Days(String base, String target) {
        System.out.println("\nLast 7 days rates:");

        LocalDate today = LocalDate.now();

        for (int i = 0; i < 7; i++) {
            LocalDate d = today.minusDays(i);
            String date = d.toString();

            try {
                String link = HIST_API + date + "?from=" + base + "&to=" + target;
                String json = httpGet(link);

                double rate = extractRate(json, target);

                if (rate > 0)
                    System.out.printf("%s : 1 %s = %s %s\n",
                            date, base, DEC.format(rate), target);
                else
                    System.out.printf("%s : no data\n", date);

            } catch (Exception e) {
                System.out.printf("%s : no data\n", date);
            }
        }
    }

    // -------------------- MAIN LOGIC --------------------

    public void start() {
        System.out.println("===============================================");
        System.out.println("           CURRENCY CONVERTER (LIVE + HIST)    ");
        System.out.println("===============================================");

        while (true) {   // OUTER LOOP — choose currencies
            printMenu();

            String base = readCode("\nEnter BASE currency: ");
            String target = readCode("Enter TARGET currency: ");

            System.out.println("\nFetching live exchange rate...");
            double rate = liveRate(base, target);

            if (rate <= 0) {
                System.out.println("API Error: Try different currency.");
                continue;
            }

            System.out.printf("Live Rate: 1 %s = %s %s\n", base, DEC.format(rate), target);

            menuLoop:   // INNER MENU LOOP START (labeled)
            while (true) {

                System.out.println("\nMENU:");
                System.out.println("1. Convert amount");
                System.out.println("2. Historical rate for specific date");
                System.out.println("3. Show last 7 days");
                System.out.println("4. Change currencies");
                System.out.println("5. Exit");

                System.out.print("Choice: ");
                String ch = sc.nextLine();

                switch (ch) {

                    case "1":
                        double amount = readAmount("\nEnter amount to convert: ");
                        double converted = amount * rate;

                        System.out.printf("\nRESULT: %s %s = %s %s\n",
                                DEC.format(amount), base, DEC.format(converted), target);

                        saveHistory(LocalDate.now() + " | " + amount + " " + base +
                                " = " + DEC.format(converted) + " " + target);
                        break;

                    case "2":
                        historicalSingle(base, target);
                        break;

                    case "3":
                        last7Days(base, target);
                        break;

                    case "4":
                        System.out.println();
                        break menuLoop;   // <-- FIXES CHANGE CURRENCY

                    case "5":
                        System.out.println("Goodbye!");
                        return;

                    default:
                        System.out.println("Invalid choice.");
                }
            } // end menuLoop
        } // end outer loop
    }

    public static void main(String[] args) {
        new CurrencyConverter().start();
    }
}