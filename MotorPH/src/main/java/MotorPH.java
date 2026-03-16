import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

//Btw double gamit naten usually for numbers kasi puro decimal yung mga numbers sa google sheet and the calculation
public class MotorPH {
    private static final String EXCEL_FILE_PATH = "MotorPH_EmployeeData.xlsx";
    private static final DataFormatter FORMATTER = new DataFormatter();

    // gets the data from the excel file
    public static void main(String[] args) {
        File excelFile = new File(EXCEL_FILE_PATH);

        if (!excelFile.exists()) {
            System.err.println("\n[ERROR] File Not Found!");
            System.err.println("Place 'MotorPH_EmployeeData.xlsx' here: " + excelFile.getAbsolutePath());
            return;
        }

        try (Scanner inputScanner = new Scanner(System.in);
             FileInputStream fis = new FileInputStream(excelFile);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet employeeSheet = workbook.getSheet("Employee Details");
            Sheet attendanceSheet = workbook.getSheet("Attendance Record");

            System.out.println("--- MotorPH Employee Management System ---");

            // Main loop for user interaction
            while (true) {
                System.out.print("\nEnter Employee ID (or 'exit'): ");
                String searchId = inputScanner.nextLine().trim();
                if (searchId.equalsIgnoreCase("exit")) break;

                Row foundEmployee = null;
                for (Row row : employeeSheet) {
                    if (row.getRowNum() == 0) continue; 
                    Cell idCell = row.getCell(0);
                    if (idCell != null && getCellValueAsString(idCell).equals(searchId)) {
                        foundEmployee = row;
                        break;
                    }
                }

                // If employee data is found didisplay yung info
                if (foundEmployee != null) {
                    displayEmployeeDetails(foundEmployee);
                    
                    //start ng pag print ng payslip pwede lahit month or number ex. 09, 9, sep, september ganorn
                    System.out.print("Type 'print [month]' (e.g., print 06 or print june): ");
                    String command = inputScanner.nextLine().trim().toLowerCase();

                    if (command.startsWith("print ")) {
                        String month = command.replace("print ", "").trim();
                        
                        // Rule: Add 1st and 2nd cutoff amounts first
                        double hours1st = calculateHoursInRange(attendanceSheet, searchId, month, 1, 15);
                        double hours2nd = calculateHoursInRange(attendanceSheet, searchId, month, 16, 31);
                        
                        generatePayslip(foundEmployee, hours1st, hours2nd, month.toUpperCase());
                    }
                } else {
                    System.out.println("Employee ID not found."); //This line edi mali yung nilagay ni user duhh
                }
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    //para madali lahat na ng month para kapag kailangan na yung iba nanjan na kagad di na kailangan mag edit edit pa ulet
    private static String convertMonthToNum(String month) {
        switch (month.toLowerCase()) {
            case "january": case "jan": case "01": case "1": return "01";
            case "february": case "feb": case "02": case "2": return "02";
            case "march": case "mar": case "03": case "3": return "03";
            case "april": case "apr": case "04": case "4": return "04";
            case "may": return "05";
            case "june": case "jun": case "06": case "6": return "06";
            case "july": case "jul": return "07";
            case "august": case "aug": case "08": case "8": return "08";
            case "september": case "sep": case "09": case "9": return "09";
            case "october": case "oct": return "10";
            case "november": case "nov": return "11";
            case "december": case "dec": return "12";
            default: return month;
        }
    }

    //eto yung display for emoployee data (could add more if needed)
    private static void displayEmployeeDetails(Row row) {
        System.out.println("\n--- Employee Information Found ---");
        System.out.println("ID Number:    " + getCellValueAsString(row.getCell(0)));
        System.out.println("Name:         " + getCellValueAsString(row.getCell(2)) + " " + getCellValueAsString(row.getCell(1)));
        System.out.println("Birthday:     " + getCellValueAsString(row.getCell(3)));
        System.out.println("Address:      " + getCellValueAsString(row.getCell(4)));
        System.out.println("Phone Number: " + getCellValueAsString(row.getCell(5)));
        System.out.println("SSS # :       " + getCellValueAsString(row.getCell(6)));
        System.out.println("PhilHealth #: " + getCellValueAsString(row.getCell(7)));
        System.out.println("TIN #:        " + getCellValueAsString(row.getCell(8)));
        System.out.println("Pag-IBIG #:   " + getCellValueAsString(row.getCell(9)));
        System.out.println("Position:     " + getCellValueAsString(row.getCell(11)));
        System.out.println("Status:       " + getCellValueAsString(row.getCell(10)));
        System.out.println("----------------------------------");
    }

    private static double calculateHoursInRange(Sheet sheet, String targetId, String filterMonth, int startDay, int endDay) {
        double totalHours = 0;
        LocalTime shiftStart = LocalTime.of(8, 0);
        LocalTime shiftEnd = LocalTime.of(17, 0);
        LocalTime gracePeriod = LocalTime.of(8, 10);
        String monthNum = convertMonthToNum(filterMonth);

        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue; 
            
            Cell idCell = row.getCell(0);
            if (idCell != null && getCellValueAsString(idCell).equals(targetId)) {
                String dateStr = getCellValueAsString(row.getCell(3)); 
                
                if (dateStr.startsWith(monthNum + "/") || 
                    dateStr.startsWith(monthNum.replaceFirst("^0", "") + "/") ||
                    dateStr.toLowerCase().contains(filterMonth)) {
                    
                    try {
                        String[] dateParts = dateStr.split("/");
                        int day = Integer.parseInt(dateParts[1].trim());

                        if (day >= startDay && day <= endDay) {
                            LocalTime timeIn = parseTimeSafely(getCellValueAsString(row.getCell(4)));
                            LocalTime timeOut = parseTimeSafely(getCellValueAsString(row.getCell(5)));

                            if (timeIn.isAfter(shiftStart) && timeIn.isBefore(gracePeriod)) timeIn = shiftStart;
                            if (timeIn.isBefore(shiftStart)) timeIn = shiftStart;
                            if (timeOut.isAfter(shiftEnd)) timeOut = shiftEnd;

                            if (timeOut.isAfter(timeIn)) {
                                Duration duration = Duration.between(timeIn, timeOut);
                                double workSession = duration.toMinutes() / 60.0;
                                if (workSession > 5) workSession -= 1.0; // Lunch break deduction
                                totalHours += Math.max(0, workSession);
                            }
                        }
                    } catch (Exception e) {}
                }
            }
        }
        return totalHours;
    }

    private static void generatePayslip(Row row, double h1, double h2, String monthLabel) {
        try {
            double hourlyRate = row.getCell(18).getNumericCellValue();
            double totalHours = h1 + h2;
            double totalMonthlyGross = hourlyRate * totalHours;
            
            double sss = calculateSSS(totalMonthlyGross); 
            double philHealth = totalMonthlyGross * 0.025; 
            double pagIbig = (totalMonthlyGross > 0) ? 200.00 : 0; 
            
            double totalGovDeductions = sss + philHealth + pagIbig;
            double taxableIncome = totalMonthlyGross - totalGovDeductions;
            double withholdingTax = calculateTax(taxableIncome);
            double totalNetPay = totalMonthlyGross - (totalGovDeductions + withholdingTax);

            //format ng payslip (WIP)
            System.out.println("\n==================================");
            System.out.println("        MOTORPH PAYSLIP           ");
            System.out.println("        MONTH: " + monthLabel);
            System.out.println("==================================");
            System.out.println("Employee: " + getCellValueAsString(row.getCell(2)) + " " + getCellValueAsString(row.getCell(1)));
            System.out.printf("1st Cutoff Gross: %,.2f (%.2f hrs)\n", (hourlyRate * h1), h1);
            System.out.printf("2nd Cutoff Gross: %,.2f (%.2f hrs)\n", (hourlyRate * h2), h2);
            System.out.println("----------------------------------");
            System.out.printf("TOTAL GROSS INCOME: %,.2f\n", totalMonthlyGross);
            System.out.println("DEDUCTIONS:");
            System.out.printf(" - SSS:           %,.2f\n", sss);
            System.out.printf(" - PhilHealth:    %,.2f\n", philHealth);
            System.out.printf(" - Pag-IBIG:      %,.2f\n", pagIbig);
            System.out.printf(" - Withholding Tax: %,.2f\n", withholdingTax);
            System.out.println("----------------------------------");
            System.out.printf("TOTAL NET PAYABLE: %,.2f\n", Math.max(0, totalNetPay));
            System.out.println("==================================\n");
            
        } catch (Exception e) {
            System.out.println("Error calculating payslip.");
        }
    }

    private static double calculateSSS(double salary) {
        if (salary <= 0) return 0;
        if (salary <= 3250) return 135.00;
        if (salary <= 24750) {
            int steps = (int)((salary - 3250 - 0.01) / 500) + 1;
            return 135.00 + (steps * 22.50);
        }
        return 1125.00; 
    }

    private static double calculateTax(double taxableIncome) {
        if (taxableIncome <= 20833) return 0;
        else if (taxableIncome <= 33333) return (taxableIncome - 20833) * 0.15;
        else if (taxableIncome <= 66667) return 1875 + (taxableIncome - 33333) * 0.20;
        else if (taxableIncome <= 166667) return 8541.67 + (taxableIncome - 66667) * 0.25;
        else return 33541.67 + (taxableIncome - 166667) * 0.30;
    }

    private static LocalTime parseTimeSafely(String timeStr) {
        timeStr = timeStr.trim().toUpperCase();
        if (timeStr.contains("AM") || timeStr.contains("PM")) {
            return LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("h:mm a"));
        } else {
            String[] parts = timeStr.split(":");
            return LocalTime.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        }
    }

    private static String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        return FORMATTER.formatCellValue(cell).trim();
    }

    // DO NO TOUCH THIS PLEASE I BEG CAUSE I DONT KNOW HOW HALF OF IT WORKS
}