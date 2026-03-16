import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

//Btw double gamit naten usually for numbers kasi puro decimal yung mga numbers sa google sheet and the calculation

// these lines is for the access and the ID for the google sheet
public class GoogleSheetsReader {
    private static final String SPREADSHEET_ID = "1Ru0sGohLZC6X0HToRM4QXXXRDyzxo9TzH3lH8S0UvOA";
    private static final String CREDENTIALS_PATH = "motorph-file-reader-f68d698715d1.json";

    // gets the data from the google sheet
    public static void main(String[] args) {
        try (Scanner inputScanner = new Scanner(System.in)) {
            List<List<Object>> employeeData = getSheetData("'Employee Details'!A1:S35");
            List<List<Object>> attendanceData = getSheetData("'Attendance Record'!A1:f5169");

            System.out.println("--- MotorPH Employee Management System ---");

            // Main loop for user interaction
            while (true) {
                System.out.print("\nEnter Employee ID (or 'exit'): ");
                String searchId = inputScanner.nextLine().trim();
                if (searchId.equalsIgnoreCase("exit")) break;

                List<Object> foundEmployee = null;
                for (int i = 1; i < employeeData.size(); i++) {
                    List<Object> row = employeeData.get(i);
                    if (!row.isEmpty() && row.get(0).toString().equals(searchId)) {
                        foundEmployee = row;
                        break;
                    }
                }

                // If employee data is found didisplay yung info
                if (foundEmployee != null) {
                    displayEmployeeDetails(foundEmployee);
                    
                    //start ng pag print ng payslip pwede lahit month or number ex. 09, 9, sep, september ganorn
                    System.out.print("Type 'print [month]' (e.g., print september) or 'back': ");
                    String command = inputScanner.nextLine().trim().toLowerCase();

                    if (command.startsWith("print ")) {
                        String month = command.replace("print ", "").trim();
                        
                        // Calculate hours for 1st cutoff (1-15) and 2nd cutoff (16-31)
                        double hours1st = calculateHoursInRange(attendanceData, searchId, month, 1, 15);
                        double hours2nd = calculateHoursInRange(attendanceData, searchId, month, 16, 31);
                        
                        generatePayslip(foundEmployee, hours1st, hours2nd, month.toUpperCase());
                    }
                } else {
                    System.out.println("Employee ID not found."); //This line edi mali yung nilagay ni user duhh
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
        //para madali lahat na ng month para kapag kailangan na yung iba nanjan na kagad di na kailangan mag edit edit pa ulet
        private static String convertMonthToNum(String month) {
        switch (month.toLowerCase()) {
            case "january": case "jan": return "01";
            case "february": case "feb": return "02";
            case "march": case "mar": return "03";
            case "april": case "apr": return "04";
            case "may": return "05";
            case "june": case "jun": return "06";
            case "july": case "jul": return "07";
            case "august": case "aug": return "08";
            case "september": case "sep": return "09";
            case "october": case "oct": return "10";
            case "november": case "nov": return "11";
            case "december": case "dec": return "12";
            default: return month;
        }
    }
    //eto yung display for emoployee data (could add more if needed)
    private static void displayEmployeeDetails(List<Object> row) {
        System.out.println("\n--- Employee Information Found ---");
        System.out.println("ID Number:    " + row.get(0));
        System.out.println("Name:         " + row.get(2) + " " + row.get(1));
        System.out.println("Birthday:     " + row.get(3));
        System.out.println("Address:      " + row.get(4));
        System.out.println("Phone Number: " + row.get(5));
        System.out.println("Position:     " + row.get(11));
        System.out.println("Status:       " + row.get(10));
        System.out.println("----------------------------------");
    }

    private static double calculateHoursInRange(List<List<Object>> attendanceData, String targetId, String filterMonth, int startDay, int endDay) {
        double totalHours = 0;
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("H:mm");
        LocalTime shiftStart = LocalTime.of(8, 0);
        LocalTime shiftEnd = LocalTime.of(17, 0);
        LocalTime gracePeriod = LocalTime.of(8, 10);
        String monthNum = convertMonthToNum(filterMonth);

        for (int i = 1; i < attendanceData.size(); i++) {
            List<Object> row = attendanceData.get(i);
            if (row.size() > 5 && row.get(0).toString().equals(targetId)) {
                String dateStr = row.get(3).toString(); 
                boolean monthMatches = dateStr.startsWith(monthNum) || 
                                       dateStr.startsWith(monthNum.replaceFirst("^0", "")) || 
                                       dateStr.toLowerCase().contains(filterMonth);

                if (monthMatches) {
                    try {
                        // Extract day from date string (MM/DD/YYYY)
                        String[] dateParts = dateStr.split("/");
                        int day = Integer.parseInt(dateParts[1]);

                        if (day >= startDay && day <= endDay) {
                            LocalTime timeIn = LocalTime.parse(row.get(4).toString(), timeFormatter);
                            LocalTime timeOut = LocalTime.parse(row.get(5).toString(), timeFormatter);

                            if (timeIn.isAfter(shiftStart) && timeIn.isBefore(gracePeriod)) timeIn = shiftStart;
                            if (timeIn.isBefore(shiftStart)) timeIn = shiftStart;
                            if (timeOut.isAfter(shiftEnd)) timeOut = shiftEnd;

                            if (timeOut.isAfter(timeIn)) {
                                Duration duration = Duration.between(timeIn, timeOut);
                                double workSession = duration.toMinutes() / 60.0;
                                if (workSession > 5) workSession -= 1.0; 
                                totalHours += Math.max(0, workSession);
                            }
                        }
                    } catch (Exception e) {}
                }
            }
        }
        return totalHours;
    }

    private static void generatePayslip(List<Object> row, double hours1st, double hours2nd, String monthLabel) {
        try {
            double hourlyRate = Double.parseDouble(row.get(18).toString().replace(",", ""));
            
            // Calculate Gross for each cutoff
            double gross1st = hourlyRate * hours1st;
            double gross2nd = hourlyRate * hours2nd;
            double totalMonthlyGross = gross1st + gross2nd;
            
            // Deductions calculated based on the combined 1st and 2nd cutoff gross
            double sss = calculateSSS(totalMonthlyGross); 
            double philHealth = calculatePhilHealth(totalMonthlyGross); 
            double pagIbig = 200.00; 
            
            double totalGovDeductions = sss + philHealth + pagIbig;
            double taxableIncome = totalMonthlyGross - totalGovDeductions;
            double withholdingTax = calculateTax(taxableIncome);
            double totalNetPay = totalMonthlyGross - (totalGovDeductions + withholdingTax);

            //format ng payslip (WIP)
            System.out.println("\n==================================");
            System.out.println("        MOTORPH PAYSLIP           ");
            System.out.println("        MONTH: " + monthLabel);
            System.out.println("==================================");
            System.out.println("Employee: " + row.get(2) + " " + row.get(1));
            System.out.printf("1st Cutoff Gross: %,.2f (%.2f hrs)\n", gross1st, hours1st);
            System.out.printf("2nd Cutoff Gross: %,.2f (%.2f hrs)\n", gross2nd, hours2nd);
            System.out.println("----------------------------------");
            System.out.printf("TOTAL GROSS INCOME: %,.2f\n", totalMonthlyGross);
            System.out.println("DEDUCTIONS:");
            System.out.printf(" - SSS:           %,.2f\n", sss);
            System.out.printf(" - PhilHealth:    %,.2f\n", philHealth);
            System.out.printf(" - Pag-IBIG:      200.00\n");
            System.out.printf(" - Withholding Tax: %,.2f\n", withholdingTax);
            System.out.println("----------------------------------");
            System.out.printf("TOTAL NET PAYABLE: %,.2f\n", totalNetPay);
            System.out.println("==================================\n");
        
        // edi errorrrr
        } catch (Exception e) {
            System.out.println("Error calculating payslip.");
        }
    }

    private static double calculatePhilHealth(double salary) {
        return salary * 0.025;
    }

    // taken from the SSS table sa google sheet
    private static double calculateSSS(double salary) {
        if (salary <= 3250) return 135.00;
        if (salary <= 24750) {
            int steps = (int)((salary - 3250 - 0.01) / 500) + 1;
            return 135.00 + (steps * 22.50);
        }
        return 1125.00; 
    }

    // withholding tax calculation
    private static double calculateTax(double taxableIncome) {
        if (taxableIncome <= 20833) return 0;
        else if (taxableIncome <= 33333) return (taxableIncome - 20833) * 0.15;
        else if (taxableIncome <= 66667) return 1875 + (taxableIncome - 33333) * 0.20;
        else if (taxableIncome <= 166667) return 8541.67 + (taxableIncome - 66667) * 0.25;
        else return 33541.67 + (taxableIncome - 166667) * 0.30;
    }

    // DO NO TOUCH THIS PLEASE I BEG CAUSE I DONT KNOW HOW HALF OF IT WORKS
    public static List<List<Object>> getSheetData(String range) throws IOException, GeneralSecurityException {
        GoogleCredentials credentials = GoogleCredentials.fromStream(new FileInputStream(CREDENTIALS_PATH))
                .createScoped(Collections.singleton(SheetsScopes.SPREADSHEETS_READONLY));
        Sheets service = new Sheets.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("MotorPH_Reader")
                .build();
        ValueRange response = service.spreadsheets().values().get(SPREADSHEET_ID, range).execute();
        return response.getValues();
    }
}